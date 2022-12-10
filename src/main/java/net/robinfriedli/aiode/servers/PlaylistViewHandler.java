package net.robinfriedli.aiode.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.AudioPlayerSendHandler;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.argument.ArgumentController;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.concurrent.ThreadExecutionQueue;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.DiscordEntity.VoiceChannel;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.exceptions.InvalidRequestException;
import net.robinfriedli.aiode.exceptions.UserException;
import net.robinfriedli.aiode.util.SearchEngine;
import net.robinfriedli.aiode.util.Util;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import se.michaelthelin.spotify.SpotifyApi;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class PlaylistViewHandler implements HttpHandler {

    private final ShardManager shardManager;
    private final SessionFactory sessionFactory;
    private final CommandManager commandManager;
    private final GuildManager guildManager;
    private final SpotifyApi.Builder spotifyApiBuilder;
    private final CommandExecutionQueueManager executionQueueManager;

    public PlaylistViewHandler(ShardManager shardManager, SessionFactory sessionFactory,
            CommandExecutionQueueManager executionQueueManager,
            GuildManager guildManager,
            SpotifyApi.Builder spotifyApiBuilder,
            CommandManager commandManager) {
        this.executionQueueManager = executionQueueManager;
        this.shardManager = shardManager;
        this.sessionFactory = sessionFactory;
        this.guildManager = guildManager;
        this.commandManager = commandManager;
        this.spotifyApiBuilder = spotifyApiBuilder;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Session session = null;
        try {
            Map<String, String> parameterMap = ServerUtil.getParameters(exchange);
            String guildId = parameterMap.get("guildId");
            String name = parameterMap.get("name");
            boolean isPartitioned = Aiode.get().getGuildManager().getMode() == GuildManager.Mode.PARTITIONED;

            if (guildId != null || !isPartitioned) {

                session = sessionFactory.openSession();
                String runthis = parameterMap.get("runthis");
                if (runthis != null) {
                    runThis(runthis, guildId, session);
                }

                if (name != null) {
                    String html = Files.readString(Path.of("html/playlist_view.html"));
                    Playlist playlist = SearchEngine.searchLocalList(session, name, isPartitioned, guildId);
                    if (playlist != null) {
                        String createdUserId = playlist.getCreatedUserId();
                        String createdUser;
                        if (createdUserId.equals("system")) {
                            createdUser = playlist.getCreatedUser();
                        } else {
                            User userById;
                            try {
                                userById = shardManager.retrieveUserById(createdUserId).complete();
                            } catch (ErrorResponseException e) {
                                if (e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                                    userById = null;
                                } else {
                                    throw e;
                                }
                            }
                            createdUser = userById != null ? userById.getName() : playlist.getCreatedUser();
                        }
                        String htmlString = String.format(html,
                                playlist.getName(),
                                playlist.getName(),
                                Util.normalizeMillis(playlist.getDuration()),
                                createdUser,
                                playlist.getSize(),
                                getList(playlist));

                        byte[] bytes = htmlString.getBytes();
                        exchange.sendResponseHeaders(200, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                    } else {
                        throw new InvalidRequestException("No playlist found");
                    }
                } else {
                    String html = Files.readString(Path.of("html/playlist_list.html"));
                    session = sessionFactory.openSession();
                    List<Playlist> playlists = SearchEngine.getLocalLists(session, isPartitioned, guildId);
                    String htmlString = String.format(html, formatPlaylists(playlists, guildId));
                    byte[] bytes = htmlString.getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }
            } else {
                throw new InvalidRequestException("Insufficient request parameters");
            }
        } catch (InvalidRequestException e) {
            ServerUtil.handleError(exchange, e);
        } catch (Exception e) {
            ServerUtil.handleError(exchange, e);
            LoggerFactory.getLogger(getClass()).error("Error in HttpHandler", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private String getList(Playlist playlist) {
        StringBuilder listBuilder = new StringBuilder();
        List<PlaylistItem> playlistItems = playlist.getItemsSorted();
        for (int i = 0; i < playlistItems.size(); i++) {
            PlaylistItem item = playlistItems.get(i);
            listBuilder.append("<tr>").append(System.lineSeparator())
                    .append("<td>").append(i + 1).append("</td>").append(System.lineSeparator())
                    .append("<td>").append(item.display()).append("</td>").append(System.lineSeparator())
                    .append("<td>").append(Util.normalizeMillis(item.getDuration())).append("</td>")
                    .append(System.lineSeparator())
                    .append("</tr>").append(System.lineSeparator());
        }

        return listBuilder.toString();
    }

    private String formatPlaylists(List<Playlist> playlists, String guildId) {
        StringBuilder listBuilder = new StringBuilder();
        playlists.sort(Comparator.comparing((Playlist l) -> l.getName()));
        for (int i = 0; i < playlists.size(); i++) {
            Playlist item = playlists.get(i);
            listBuilder.append("<tr>").append(System.lineSeparator())
                    .append("<td>").append(i + 1).append("</td>").append(System.lineSeparator())
                    // TODO: ESCAPE THESE
                    .append("<td><a href=\"/list?guildId=").append(esc(guildId)).append("&runthis=")
                    .append(esc(item.getName())).append("\">").append(esc(item.getName())).append("</a></td>")
                    .append(System.lineSeparator())
                    .append("<td>").append(Util.normalizeMillis(item.getDuration())).append("</td>")
                    .append("<td>").append(item.getSize()).append("</td>")
                    .append("<td><a href=\"/list?guildId=").append(esc(guildId)).append("&name=")
                    .append(esc(item.getName())).append("\">view</a></td>")
                    .append(System.lineSeparator())
                    .append("</tr>").append(System.lineSeparator());
        }

        return listBuilder.toString();
    }

    private String esc(String in) {
        return StringUtils.replaceEach(in, new String[] { "&", "\"", "<", ">" },
                new String[] { "&amp;", "&quot;", "&lt;", "&gt;" });
    }

    private void runThis(String name, String guildId, Session session) {
        Guild guild = shardManager.getGuildById(guildId);

        Member selfMember = guild.getSelfMember();
        if (!selfMember.getVoiceState().inAudioChannel()) {
            Optional<net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel> c = guild.getChannels().stream()
                    .filter(c1 -> c1.getType() == ChannelType.VOICE && selfMember.hasAccess(c1))
                    .map(c2 -> (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel) c2)
                    .filter(c3 -> c3.canTalk(selfMember) && !c3.getMembers().isEmpty())
                    .findFirst();

            if (c.isPresent()) {
                AudioPlayback playback = guildManager.getContextForGuild(guild).getPlayback();
                playback.setVoiceChannel(c.get());
                guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(playback.getAudioPlayer()));
                guild.getAudioManager().openAudioConnection(c.get());
            }
        }
        CommandContext commandContext = new CommandContext(
                guild,
                guildManager.getContextForGuild(guild),
                guild.getJDA(), // JDA jda,
                selfMember, // Member member,
                "$script $invoke=playls " + name, // String message,
                sessionFactory, // SessionFactory sessionFactory,
                spotifyApiBuilder, // SpotifyApi.Builder spotifyApiBuilder,
                "$script $invoke=playls " + name, // String commandBody,
                (MessageChannelUnion) guildManager.getDefaultTextChannelForGuild(guild), // MessageChannelUnion
                // textChannel,
                false, // boolean isSlashCommand,
                null // @Nullable InteractionHook interactionHook
        );
        // CommandContext commandContext = new CommandContext(event, guildContext,
        // hibernateComponent.getSessionFactory(), spotifyApiBuilder, "clear");
        ExecutionContext.Current.set(commandContext.fork());
        ThreadExecutionQueue queue = executionQueueManager.getForGuild(guild);
        try {
            AbstractCommand commandInstance = commandManager
                    .instantiateCommandForIdentifier("script", commandContext, session)
                    .orElseThrow(() -> {
                        // logger.error("No command found for slash command identifier {} on guild {}",
                        // event.getName(), guild);
                        return new UserException("No such command: script playls");
                    });

            // ArgumentController argumentController =
            // commandInstance.getArgumentController();
            // for (OptionMapping option : event.getOptions()) {
            // if (option.getType() == OptionType.BOOLEAN && !option.getAsBoolean()) {
            // continue;
            // }
            // String optionName = option.getName();
            // String optionValue = option.getAsString();
            // argumentController.setArgument(optionName, optionValue);
            // if ("input".equals(optionName)) {
            // commandInstance.setCommandInput(optionValue);
            // }
            // }

            commandManager.runCommand(commandInstance, queue);
        } catch (UserException e) {
            LoggerFactory.getLogger(getClass()).error("Error in HttpHandler", e);
            // EmbedBuilder embedBuilder = e.buildEmbed();
            // messageService.sendTemporary(embedBuilder.build(),
            // commandContext.getChannel());
            // event.getInteraction().getHook().deleteOriginal().queue();
        }
    }
}
