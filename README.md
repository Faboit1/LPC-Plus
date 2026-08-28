# LPC

[![Build](https://github.com/Faboit1/LPC-Plus/actions/workflows/build.yml/badge.svg)](https://github.com/Faboit1/LPC-Plus/actions/workflows/build.yml)

A chat formatting plugin for LuckPerms.

## Chat features

Beyond the LuckPerms-driven chat format, LPC gives players control over their own chat
and can police what goes into it. Everything here is configured in `config.yml`.

### `/showchatfrom <everyone|friends|none>`

Picks whose public chat you see. The setting belongs to the viewer, and never hides your
own messages. `friends` needs [FriendSystem](https://github.com/Faboit1/Friendsystem).

### `/allowmentions <friends|all|nobody>`

Picks who may ping you by name. Typing a player's name (or `@Name`, and you can require
the `@` in the config) repaints it in the message and gives them a sound and an action
bar. A name is only highlighted when the ping was actually allowed, so the colour never
promises a ping that did not happen. `friends` needs FriendSystem.

### Emoji

`:heart:` and friends are replaced with the character configured for them. On Paper the
emoji also carries a hover showing the shortcode it came from, so players can see how a
symbol was typed; plain Spigot's chat event is a single string with nowhere to put a
hover, so there the character is substituted without one. Shortcodes are plain ASCII, so
they pass the character filter below even on an ASCII-only server.

### Character filter

Optionally refuses messages containing characters you do not want in chat — symbols the
vanilla font cannot draw, combining marks stacked into "zalgo" text, or right-to-left
overrides. Instead of listing what to ban, `allowed-characters` is a regular-expression
character class saying what *is* allowed; the default is printable ASCII. A refused
message is never sent, and the player is warned in chat and on the action bar. Off by
default; `lpc.bypass.characters` exempts staff.

## FriendSystem integration

The `friends` option of `/showchatfrom` and `/allowmentions` is answered by
[FriendSystem](https://github.com/Faboit1/Friendsystem)'s public API. It is an optional
soft dependency reached through reflection, so LPC builds and runs unchanged without it —
FriendSystem is not published to a Maven repository, and pinning a JitPack build would
make LPC's own build depend on it. Only the API's query methods are used, which
FriendSystem documents as safe to call from the async chat thread.

Without FriendSystem installed, LPC cannot tell who is friends with whom, so `friends`
deliberately behaves like the open option rather than silently cutting a player off from
chat. Players are told this when they pick it.

## Server compatibility

Runs on Spigot, Paper and Folia from one jar. Paper gets the richer chat path
(per-viewer filtering and emoji hovers); Spigot falls back to the legacy chat event.
On Folia, work that touches a player is dispatched to that player's own scheduler and
the settings file is written through the async scheduler — both reached reflectively,
so the same jar still loads on Spigot where those schedulers do not exist.

## Building

Every push to `main` and every pull request is compiled automatically by GitHub
Actions, and the resulting jar is uploaded as a build artifact.

To build locally you need **JDK 25** and Maven:

```sh
mvn clean package
```

The jar is written to `target/LPC-<version>.jar`.

`plugin.yml` and `config.yml` are resources, so compiling never parses them — a broken
descriptor builds green and only fails when a server tries to load the plugin. CI runs a
check for exactly that, which you can run yourself after a build:

```sh
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" .github/scripts/DescriptorCheck.java
```

It parses `plugin.yml` with Bukkit's own `PluginDescriptionFile`, checks the main class
is in the build and that Maven substituted every `${...}`, and checks `config.yml` parses
with no emoji shortcode lost to YAML's boolean coercion.

JDK 25 is required because `paper-api` 26.1.2 ships Java 25 class files. The
plugin itself is still compiled to Java 8 bytecode, so it keeps running on the
older servers declared in `plugin.yml` (`api-version: 1.13`).
