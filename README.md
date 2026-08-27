# LPC

[![Build](https://github.com/Faboit1/LPC-Plus/actions/workflows/build.yml/badge.svg)](https://github.com/Faboit1/LPC-Plus/actions/workflows/build.yml)

A chat formatting plugin for LuckPerms.

## Building

Every push to `main` and every pull request is compiled automatically by GitHub
Actions, and the resulting jar is uploaded as a build artifact.

To build locally you need **JDK 25** and Maven:

```sh
mvn clean package
```

The jar is written to `target/LPC-<version>.jar`.

JDK 25 is required because `paper-api` 26.1.2 ships Java 25 class files. The
plugin itself is still compiled to Java 8 bytecode, so it keeps running on the
older servers declared in `plugin.yml` (`api-version: 1.13`).
