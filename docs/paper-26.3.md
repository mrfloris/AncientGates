# Paper 26.3 compatibility notes

## Target and changes

- Java compilation uses `--release 25` (class-file major version 69).
- Paper API is pinned to `26.3.build.49-alpha`, with `api-version: '26.3'`.
- Maven Shade 3.6.2 handles Java 25 bytecode. The scheduler dependency is pinned
  to the exact previously published SDUtils snapshot instead of a changing snapshot.
- XSeries and old Minecraft-version parsing are removed from the plugin. Gate
  materials, air detection, and portal orientation use supported Bukkit/Paper APIs.
- Closing portals explicitly removes the portal blocks. This fixes water/lava
  remaining after close and avoids drops from plugin-created portal materials.
- Local vehicle and command-gate travel uses Paper's native teleportation instead
  of passing CraftBukkit implementation classes to `World.spawn`. Existing vehicle
  UUIDs, inventories, metadata, and passengers are preserved by the server.
- Inventory removal/drop for local vehicles happens only after a successful
  teleport. Every direct player passenger is checked for gate access and cooldown.
  Multiple players must dismount to use a paid gate; multiple passengers must
  dismount to use a cross-server gate because the legacy transport represents one rider.
- Item transport serializes Paper's versioned item bytes as Base64 with an `AG2:`
  prefix. It preserves components, PDC, nested containers, and empty slots. Legacy
  payloads remain readable, with their original slot indices respected. Oversized
  Bungee payloads are rejected before truncating the legacy signed-short length.
- Removed unsupported skeleton subtype mutation and obsolete chest flags on
  ordinary horses. Villager professions use the registry. Pet owner lookups use
  exact names and the server's local player cache.
- Upstream auto-updates are disabled so they cannot replace this compatibility
  build. Scheduler tasks are cancelled on disable. The Folia support declaration
  is removed because this branch targets Paper's main-thread execution model.

## Verification performed

On 2026-09-28, clean builds used Java 25.0.4.1. The separate smoke plugin ran on
real Paper 26.3 builds 49 and 133, including startup, shutdown, and restart.

| Paper build | First start | Restart | Shutdown |
| --- | --- | --- | --- |
| 49 alpha | 288 assertions, 0 failures | 291 assertions, 0 failures | Clean |
| 133 alpha | 288 assertions, 0 failures | 291 assertions, 0 failures | Clean |

The verified production JAR has SHA-256
`a8a8388655d502013cc6b9aafbd8402e4a6d98c241a95081c70cb0fee98ca87c`.
The packaged descriptor declares API 26.3, plugin classes use Java 25 bytecode,
and neither the smoke plugin nor the Paper API is bundled in the production JAR.

The suite covers all eight gate materials, both vertical axes, horizontal and
diagonal gates, cave-air interiors, gate indexing, block removal, and persistence.
It also covers dropped items, storage minecarts, two-passenger boats, cross-world
passenger UUIDs, cancelled vehicle teleports, exactly one cargo drop, item names,
lore, third-party PDC, nested shulker contents, sparse legacy inventory slots,
oversized Bungee payload rejection, and legacy skeleton/horse/villager data.
Rider permission and cooldown checks use controlled Player/Vehicle test doubles
on the real server. Console `/gate help`, create, and delete were also exercised.

No unsafe Paper exploit-protection settings were enabled. Runtime code uses
public APIs; inspecting server internals during diagnosis did not introduce an
NMS/reflection dependency.

## Staging still required

Real connected Java and Floodgate clients, player inventory modes, live Vault
economy providers, Bungee/proxy and socket transfers, and interactions with CMI,
AutoSell, and other production plugins need testing on a copy of the intended
server. Cross-server transport retains upstream's delivery/retry design; the
serialization tests do not establish end-to-end delivery or crash recovery.
The legacy entity wire format still transfers only the properties it explicitly
models, so arbitrary entity metadata is not preserved between servers.

Configuration and gate JSON persistence retain the upstream I/O design. This
upgrade does not add an asynchronous persistence layer or transaction journal.
Do not combine old and new item-transfer endpoints. Keep a backup of the previous
JAR and data directory for rollback, and validate a copy before changing a live server.

## Official API and documentation checks

Discovery started from the live [PaperMC documentation index](https://docs.papermc.io/llms.txt).
The complete documentation dump is not vendored in this repository.

- [Project setup](https://docs.papermc.io/paper/dev/project-setup/): Java 25 and
  the new `26.x.build.<id>-<channel>` Maven coordinates.
- [plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/): the minimum API
  version, legacy plugin loading, commands, and permissions.
- [Roadmap](https://docs.papermc.io/paper/dev/roadmap/) and the
  [26.3 API](https://jd.papermc.io/paper/26.3/): removal/deprecation review.
- [Teleportation](https://docs.papermc.io/paper/dev/entity-teleport/) and
  [Entity](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Entity.html): current
  passenger handling and teleport success/cancellation behavior.
- [Material](https://jd.papermc.io/paper/26.3/org/bukkit/Material.html),
  [ItemStack](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html),
  [Registry](https://jd.papermc.io/paper/26.3/org/bukkit/Registry.html), and
  [AbstractSkeleton](https://jd.papermc.io/paper/26.3/org/bukkit/entity/AbstractSkeleton.html):
  replacements were verified against the exact build 49 API source artifact as
  well as the versioned Javadocs.
- [Scheduling](https://docs.papermc.io/paper/dev/scheduler/),
  [events](https://docs.papermc.io/paper/dev/event-listeners/), and
  [world configuration](https://docs.papermc.io/paper/reference/world-configuration/):
  main-thread world access, event cancellation, and unchanged safety defaults.

For the next Paper build update, recheck these live pages and the exact API
artifact, then rerun the clean build, smoke suite, restart, and production staging
checks. Pin a new build explicitly instead of silently widening the dependency.
