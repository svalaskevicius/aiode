#!/usr/bin/env bash

set -e 

cd ../tabletop-rpg-music

cat packs/tabletop-rpg-music.db | jq -r '.name' | while read -r name ; do
  pname="$(echo "$name" | sed -e "s/'/''/g")"
  echo "INSERT INTO public.playlist (created_user,created_user_id,guild,guild_id,\"name\") VALUES ('rakatan','775829016330502176','Nordom''s plane','1049987698695352320','$pname');"

  idx=0
  cat packs/tabletop-rpg-music.db | jq -c "select(.name == \"$name\") | .sounds[]" | while read -r line ; do
    sname="$(echo "$line" | jq -r '.name' | sed -e "s/'/''/g")"
    # echo "$sname"
    spathr="$(echo "$line" | jq -r '.path')"
    spath="$(echo "${spathr//modules\/tabletop-rpg-music\//}")"
    lpath="$(echo "${spathr//modules\/tabletop-rpg-music\//}" | sed -e "s/%27/'/g")"
    # echo "$spath"
    sdur="$(ffmpeg as ffmpeg -i "./$lpath" 2>&1 | grep Duration | perl -p -e 's/.*Duration: ([0-9]+):([0-9]+):([0-9]+)\.([0-9]*),.*/((($1*60+$2)*60)+$3)*1000+$4*10/e')"
    # echo "$sdur"

    echo "INSERT INTO public.url_track (added_user,added_user_id,created_timestamp,duration,item_index,title,url,playlist_pk) VALUES
    ('rakatan','775829016330502176','2022-12-10 16:18:56.614',$sdur,$idx,'$sname','http://localhost:9899/$spath',(select pk from playlist where name = '$pname'));"
    idx=$((idx+1))
  done
#     sed -e 's#modules/tabletop-rpg-music#http://localhost:9899#g' | \
#     while read
#     xargs -IXXX echo "add 'XXX' \$to '$name'"
#
# ffmpeg as ffmpeg -i ../tabletop-rpg-music/music/AStrangeWorld.mp3 2>&1 | grep Duration | perl -p -e 's/.*Duration: ([0-9]+):([0-9]+):([0-9]+)\.([0-9]*),.*/((($1*60+$2)*60)+$3)*1000+$4*10/e'
done # | sed -e "s/'/\\\\\\\\'/g" -e 's/\$/\\$/g' -e "s/^/command.run('/g" -e "s/\$/')/g"
