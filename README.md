Ancient Gates Reloaded
====

## Unofficial Paper 26.2 and 26.3 compatibility build

This repository updates [ChanceSD/AncientGates](https://github.com/ChanceSD/AncientGates)
for **Paper 26.2 / 26.3 and Java 25**. It is an interim community compatibility build,
version `2.7.2-paper26.2-26.3-SNAPSHOT`, based on upstream commit
`af059f850623858e7d82b6ac72272fb70d5fcebe`.

Download **`ancientgates-26.2-26.3-vibecoded-test.jar`** from the
[test release](https://github.com/mrfloris/AncientGates/releases/tag/26.2-26.3-vibecoded-test).
This is an unofficial test build, not an upstream release. Report problems with
this build in [this repository](https://github.com/mrfloris/AncientGates/issues).

The API dependency is pinned to **`26.2.build.129-stable`**, and the plugin declares
**`api-version: '26.2'`** so the same JAR can load on both versions. Paper 26.3
entered beta with **build 134** on 2026-09-28. The test matrix covers 26.2 build 129
and 26.3 beta build 134. GitHub Actions compiles against both exact API versions.
Versions before 26.2, Spigot, and Folia are outside this build's support target.

The earlier `26.3-vibecoded-test` release requires Paper 26.3 and is rejected by
26.2 with `Unsupported API version 26.3`. Replace that JAR with the release above
to run on 26.2.

### Build and install

Use JDK 25 or later and Maven 3.9 or later:

```sh
mvn -B -ntp clean verify
```

On macOS, select Java 25 for the build if another JDK is the default:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -B -ntp clean verify
```

Install **`target/AncientGates.jar`**. Stop the server, back up its worlds and
`plugins/AncientGates` directory, replace the previous plugin JAR, and restart
with Java 25. Existing `conf.json`, `gates.json`, command names, and permission
nodes are retained. Check for an older AncientGates JAR already queued in the
server's `plugins/update` directory before restarting.

The release download uses the filename `ancientgates-26.2-26.3-vibecoded-test.jar`;
install it the same way, keeping only one AncientGates plugin JAR in `plugins/`.

Upstream automatic downloads and update checks are disabled in this build,
including when an existing configuration still has them enabled. Install future
compatibility updates manually.

**Upgrade every participating server before using cross-server item transfers.**
New item payloads preserve components and plugin metadata and require this build
at the receiver. The receiver can still read the old item format; older plugin
versions cannot read the new format.
Participating servers must also use the same Minecraft version for item/entity
transfers. Loading this plugin on both 26.2 and 26.3 does not provide safe
downgrading of item data from a newer Minecraft version.

### Verification

The opt-in integration suite uses a real Paper server to test portal materials and
orientations, item/container metadata, vehicles and passengers, cancellation,
permissions and cooldowns, cross-world travel, and persisted gates after restart.

```sh
mvn -B -ntp -Ppaper-smoke clean verify
python3 scripts/paper-smoke.py --accept-eula --java /path/to/java25/bin/java --version 26.2 --build 129
python3 scripts/paper-smoke.py --accept-eula --java /path/to/java25/bin/java --version 26.3 --build 134
```

Pass `--accept-eula` only if you accept the [Minecraft EULA](https://aka.ms/MinecraftEULA).
The script verifies the official server JAR checksum, uses a dedicated directory
under `.scratch`, binds to `127.0.0.1:25583`, and starts and stops the server twice.
Logs remain in `.scratch/paper-smoke-<version>-<build>/`. Unsupported API declarations
fail immediately with the loader's error instead of waiting for the test timeout.

**Never install `AncientGates-paper-smoke.jar` on a real server.** It creates test
gates and entities and shuts the server down. It is separate from the production JAR.
The normal Maven build compiles the harness; the script executes its assertions.

See [compatibility notes and remaining staging checks](docs/paper-26.3.md).

## Upstream project

The upstream Git history, author credits, and [GPLv3 license](LICENSE) are retained.
The compiled plugin is distributed as a GitHub release asset; build outputs,
disposable servers, worlds, logs, and local credentials are excluded from Git.

- Bukkit Page: https://dev.bukkit.org/projects/ancient-gates/
- Spigot Page: https://www.spigotmc.org/resources/ancient-gates.6583/
- Dev Builds: https://ci.codemc.io/job/ChanceSD/job/AncientGates/

Developers
------
The coordinates below describe the upstream release, not this compatibility build.
 * Maven Repo:
```xml
<repository>
    <id>CodeMC</id>
    <url>https://repo.codemc.org/repository/maven-public/</url>
</repository>
```
 * Artifact Information:
```xml
<dependency>
    <groupId>org.mcteam.ancientgates</groupId>
    <artifactId>ancientgates</artifactId>
    <version>2.7.1</version>
    <scope>provided</scope>
</dependency>
 ```

**Bstats**

[![bStats](https://bstats.org/signatures/bukkit/AncientGates.svg "bStats")](https://bstats.org/plugin/bukkit/AncientGates/ "bStats")
