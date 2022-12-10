{
  # Pull in tools & environment variables that are only
  # required for interactive development (i.e. not necessary
  # on CI). Only when this is enabled, Rust nightly is used.
  isDevelopmentShell ? true
, pkgs ? import <nixpkgs> {}
}:

let
  # Keep project-specific shell commands local
  fish_history = "${toString ./.}/.fish_history";

  # Rust-specific

  # Enable printing backtraces for rust binaries
  RUST_BACKTRACE = 1;

  # Only in development shell

  buildInputs = [
    pkgs.openjdk17
    pkgs.llvmPackages.libclang.lib
    pkgs.clang
    pkgs.clangStdenv
    pkgs.pkg-config
    pkgs.openssl

#    pkgs.cargo
#    pkgs.rustPackages.clippy
#    pkgs.rustc
#    pkgs.rustfmt
#    pkgs.git
#    pkgs.direnv
#    pkgs.shellcheck
#    pkgs.carnix
#    pkgs.nix-prefetch-git
#    pkgs.nixpkgs-fmt
#
#    # To ensure we always have a compatible nix in our shells.
#    # CI doesn’t know `nix-env` otherwise.
#    pkgs.nix
#  ] ++ pkgs.stdenv.lib.optionals pkgs.stdenv.isDarwin [
#    pkgs.darwin.Security
#    pkgs.darwin.apple_sdk.frameworks.CoreServices
#    pkgs.darwin.apple_sdk.frameworks.CoreFoundation
  ];

in
pkgs.mkShell (
  {
    name = "aiode";
    buildInputs = buildInputs; #  ++ pkgs.stdenv.lib.optionals isDevelopmentShell [ pkgs.rustracer ];

    inherit RUST_BACKTRACE;
    LIBCLANG_PATH = "${pkgs.llvmPackages.libclang.lib}/lib";
  }
)

