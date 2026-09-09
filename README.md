# Solaris Loader

Optional **client-side mod** for [Solaris](https://github.com/kaiserproger/solaris)
servers whose Luau plugins provide custom screens, HUD, assets, sounds, items,
blocks, or keyboard actions. If the server uses only server-side plugins, an
ordinary vanilla client is enough.

This is not a server plugin, modpack, launcher, or replacement for Fabric,
NeoForge, or Forge. Players do not need Gradle, Rust, MCP, or an API token.

## Download

Open [Releases](https://github.com/kaiserproger/solaris-loader/releases) and
choose the Loader release required by your server. Download **one** platform
JAR from its Assets, not GitHub's source-code archive:

| Minecraft | Platform baseline | Download |
| --- | --- | --- |
| 26.1.2 | Fabric Loader 0.19.3 + Fabric API 0.155.2+26.1.2 | `loader-fabric-0.1.0.jar` |
| 26.1.2 | NeoForge 26.1.2.76 | `loader-neoforge-0.1.0.jar` |
| 26.1.2 | Forge 26.1.2-64.1.0 | `loader-forge-0.1.0.jar` |

Use **Java 25**. The same platform JAR works across operating systems; the
launcher, Java runtime, Minecraft version, and mod loader must match. Do not
mix adapters, use a nearby Minecraft version, or install an agent/development
JAR. Fabric requires the separate Fabric API mod. A `SHA256SUMS` release asset
allows verification with `sha256sum -c SHA256SUMS --ignore-missing` on Linux;
on Windows, compare `Get-FileHash FILE.jar -Algorithm SHA256` with that file.

Current protocol is **2**, plugin API **0.6.0**. These are alpha contracts:
match the server's declared compatibility, not just similar version numbers.
The settlement overhaul and schema-2/protocol-3 view proposal are not current
features. Releases remain previews, not broad survival-readiness claims.

## Install in your launcher

1. Close the Minecraft instance.
2. Select Minecraft **26.1.2**, Java **25**, and one platform from the table.
3. Open the **game directory of that instance** and its `mods/` folder.
4. Copy the matching Solaris Loader JAR there. For Fabric, also install the
   matching Fabric API JAR. Remove an older Solaris Loader JAR from that same
   instance so exactly one remains.
5. Start that instance, connect to the server, and review its permission prompt.

| Launcher | Where to place the JAR |
| --- | --- |
| PrismLauncher | Edit the instance → **Mods** → add the downloaded file, or open its game folder and use `mods/`. Choose the mod loader under **Version**. |
| MultiMC | Edit the instance → **Loader mods** → add the file, or open the instance's Minecraft folder and use `mods/`. Use only a platform/version the launcher actually offers. |
| Official Minecraft Launcher | Install the chosen Fabric/NeoForge/Forge client profile with that platform's installer. Select its installation and open its configured **Game Directory**, then use `mods/`. The launcher alone does not install a mod loader. |
| Modrinth App | Open the matching instance's folder and copy the file into `mods/`; ensure the instance has the exact game version and platform. |
| CurseForge App | Open the matching custom profile's folder and copy the file into `mods/`. Managed packs may need content-management changes; do not replace their loader with a different platform. |
| Other launchers | Use the launched profile's game directory, not the launcher's application directory or another profile's global `.minecraft`. |

Launcher menus vary by version. If it cannot provide the exact Minecraft,
Java, and loader combination, use a compatible launcher rather than changing
the JAR's target version. This table describes manual installation, not a
claim that every launcher/platform combination was graphically tested.
[Prism's mod installation guide](https://prismlauncher.org/wiki/getting-started/download-mods/)
explains its instance/loader selection.

Do **not** unzip the JAR or place it in the Solaris server's `plugins/` folder.
For a custom game directory, its `mods/` folder is authoritative; operating
system default Minecraft folders are not reliable for separate instances.

## First connection

Loader asks before activating a server's requested permissions. **Allow**
downloads missing declared bundles, verifies their sizes and SHA-256 hashes,
then activates content. **Deny** disconnects without requesting those bundles.
A different server address or permission set prompts again.

Decisions and verified content are cached under `~/.solaris/loader-cache/`.
Active content, HUD, sounds, and held actions are cleared on disconnect.
Only approve servers you trust; a matching content hash establishes byte
identity, not that a server is trustworthy.

## Troubleshooting

- **“Loader required”**: verify the launched instance, `mods/` location, adapter,
  and Minecraft version. Look for `solaris_loader` in `logs/latest.log`.
- **Missing Fabric API / incompatible mod**: use the matching dependencies from
  the table, not Fabric API for another Minecraft release.
- **Wrong Java / unsupported class version**: configure Java 25 for this
  instance, not merely your shell.
- **Permission denied**: inspect the server address and your decision. Do not
  copy another server's permission file to bypass consent.
- **Hash, bundle, protocol, or activation failure**: retain the disconnect
  reason and `logs/latest.log`; ask the operator for a matching release and
  corrected bundle. Do not disable verification or mix adapters.

Operators and plugin authors: see the
[full installation/permission guide](https://github.com/kaiserproger/solaris/blob/main/docs/SOLARIS_LOADER.md)
and [plugin API](https://github.com/kaiserproger/solaris/blob/main/docs/PLUGINS.md).

## Build from source

Java 25 is required. This Gradle workspace builds independently of core;
integration tests use the sibling `../solaris` fixtures.

```sh
git clone https://github.com/kaiserproger/solaris-loader.git
cd solaris-loader
./gradlew --no-configuration-cache :loader-fabric:jar :loader-neoforge:jar :loader-forge:jar
```

On Windows use `gradlew.bat`. JARs appear in each adapter's `build/libs/`.
Only `loader-fabric`, `loader-neoforge`, and `loader-forge` are player adapters;
`fabric-agent`, `java-agent`, and `bridge-core` are development tooling.

<details>
<summary>Developer reference: Loader protocol and content activation</summary>

### Shared Loader implementation

`loader-core` contains the protocol-2 manifest/ack codec and validates platform,
permissions, and exact cache identities. `loader-fabric`, `loader-neoforge`,
and `loader-forge` register the same Configuration-state manifest and
acknowledgement payloads through their native 26.1.2 networking APIs.

The first manifest for an exact server address and permission set opens a
Minecraft confirmation screen. Allow and deny decisions are stored separately
per normalized server address in `permissions.properties` under the Loader
cache. A different server or changed permission set prompts again. The cache
directory defaults to `~/.solaris/loader-cache` and may be overridden for tests
or isolated profiles:

```text
-Dsolaris.loader.cacheDir=/path/to/loader-cache
```

Denial disconnects before an artifact request or staging file can be created.
Solaris keeps the Loader Configuration handshake open for up to two minutes so
the first confirmation is not constrained by the ordinary ten-second pre-Play
packet timeout.
For each missing exact cache identity the client requests only that bundle,
streams bounded Configuration payloads into a temporary file, verifies the
declared size and SHA-256, and atomically publishes it before acknowledging the
manifest. An unknown, duplicate, or missing permission fails the handshake.
After all exact cache files pass verification, the shared core opens each ZIP
with `solaris-client.json` as its first entry. That closed schema currently
accepts owned UI, block, item and sound definitions, declared asset bytes, and
owner-namespaced UI/keyboard interactions. Each block declaration (`id`, owner
`model`, and `name`) is backed by its exact verified model asset.
Each item names a known vanilla base
item and requires its exact verified
`assets/<namespace>/items/<path>.json` definition; UI may reference one
declared item. Each interaction carries a bounded label and at most 4 KiB UTF-8
payload, with optional same-bundle `ui_id` and canonical `key.keyboard.*` `key`.
At least one source is required; a screen may expose at most eight actions,
and at most 64 interactions may be activated. Key-only content uses
`send_interactions` without requesting `present_ui`.
Up to 64 `sounds` (`id`) require `play_sounds` and same-bundle verified mono
OGG Vorbis assets. Shared pack preparation probes their headers/initial audio
and generates owner `sounds.json` indexes without late registry mutation.
The three adapters publish the resulting immutable registry before sending the
acknowledgement and keep it available in Play. Unknown archive fields or
entries and mismatched asset bytes fail before acknowledgement. An acknowledged
Loader session can receive
`solaris:loader/ui` in Play. One shared presenter handles `screen`, `hud` and
`hidden` modes for an activated id from the exact originating connection.
Optional title/body overrides are bounded to 128 bytes/8 KiB; omitted values
come from the verified UI definition. Modal views retain item/block displays
and action buttons. HUD is non-interactive title/body text: at most eight rows,
256 GUI pixels wide, clipped by viewport capacity, and hidden by Minecraft's
hide-GUI option. Hiding one id cannot remove another owner's HUD or modal view.
Pressing a current action emits `solaris:loader/interaction` only while that
originating connection and definition are still active. Referenced items render
through a local vanilla stack with Minecraft 26.1.2's owner-namespaced
`ITEM_MODEL`; no late registry mutation is used. The server accepts the
bounded action only from the exact acknowledged Play session and delivers
`on_loader_interaction(event)` solely to the Lua plugin owning the interaction
namespace. The event carries `player_id`, `interaction_id`, `phase`
(`trigger` for buttons, `press`/`release` for keys), and untrusted `payload`.
The shared HEAD keyboard mixin covers vanilla early-return keys on every
adapter without cancellation. Menu/overlay/window-focus loss releases bounded
held state; autorepeat cannot reactivate it. Menu-closing Escape never becomes
a gameplay press. Fixed bindings preserve vanilla screenshot/fullscreen and
movement; no rebinding, chords, mouse/gamepad or client Lua runtime is added.
The protocol-2 action is `u16 protocol`, `u8 phase`, then `u16`-prefixed UTF-8
id/payload (big-endian, at most 4,231 bytes), with no old-wire fallback.
Activation/disconnect clears HUD, keyboard state and Loader sound playback; disconnect clears the
active registry, so queued work or content from one server cannot reach a later
connection. Declared
`assets/<namespace>/<path>` bytes now form one
transient required Minecraft client resource pack. The acknowledgement waits
for an exact-byte resource reload, and the pack is removed and reloaded out
when its originating connection closes. The block path pre-registers eight
bounded carriers before freeze on each platform. After verified pack reload it
sorts up to eight owner block ids, maps each blockstate/item definition to the
corresponding carrier, and sends the exact owner-id-to-runtime-state map as
`carrier_block_state_ids`. Solaris cross-checks that closed map against the
hash-verified artifacts and retains it only in the acknowledged Play session.
Server grants, placement, projection, break drops, persistence, and pickup
preserve the exact owner identity through that mapping; no client runtime id
enters canonical world or inventory state.

`solaris:loader/sound` resolves only an activated sound on its originating
connection. The shared native presenter supports personal or fixed-position
one-shots, bounded volume/pitch, distance attenuation, and owner-id-local stop.
The [live fixture gate](../solaris/examples/loader-live-gate/README.md) captures actual
audio output for all three platforms; no acknowledgement or chat response is
treated as evidence that the sound played.

```sh
./gradlew \
  :loader-core:test \
  :loader-fabric:test \
  :loader-neoforge:test \
  :loader-forge:test
```

Build distributable platform jars with:

```sh
./gradlew :loader-fabric:jar :loader-neoforge:jar :loader-forge:jar
```

</details>

<details>
<summary>Developer reference: real-client MCP automation</summary>

### Solaris Minecraft Client MCP

The Solaris core harness locates this workspace through `SOLARIS_LOADER_ROOT`
(default `../solaris-loader`). The following automation commands require the
core checkout; normal player installations do not.

The development client agent embeds an authenticated Streamable HTTP MCP
server in the real Minecraft Java 26.1.2 client. MCP hosts can inspect the
client-visible world and drive ordinary client inputs without using screenshots
as state assertions or an external command-string launcher.

## Start

Use Java 25 and choose a fresh bearer token:

```sh
export SOLARIS_CLIENT_MCP_TOKEN="$(openssl rand -hex 32)"
export SOLARIS_CLIENT_MCP_PORT=39095
(cd ../solaris && python3 -m tools.harness client)
```

The launcher calls the fixed Gradle task
`:fabric-agent:runClientMcp`. Optional settings are:

- `SOLARIS_CLIENT_MCP_GAME_DIR`: isolated Minecraft game directory.
- `SOLARIS_CLIENT_MCP_USERNAME`: 1..16 ASCII letters, digits, or underscores.
- `SOLARIS_CLIENT_MCP_PORT`: free IPv4 loopback port, default `39095`.

Normal launch checks for an occupied MCP port before starting Gradle; the
in-client bind remains authoritative if another process races for the port.
Environment token and port values describe the current run and take precedence
over stale JVM properties. Treat HTTP 401 as a wrong endpoint/token pair, not
as a retryable MCP failure.

Validate Java, configuration, MCP transport tests, and the Gradle adapter
without launching Minecraft:

```sh
(cd ../solaris && SOLARIS_CLIENT_MCP_TOKEN=local-check-token \
  python3 -m tools.harness client --check)
```

The same run can be started directly:

```sh
SOLARIS_CLIENT_MCP_TOKEN="$SOLARIS_CLIENT_MCP_TOKEN" \
  ./gradlew --no-configuration-cache :fabric-agent:runClientMcp
```

## Codex

Register the endpoint once. Start Codex from an environment containing the
same bearer token used by the Minecraft process:

```sh
codex mcp add minecraft-client \
  --url http://127.0.0.1:39095/mcp \
  --bearer-token-env-var SOLARIS_CLIENT_MCP_TOKEN
```

No proxy process is required. The MCP endpoint is the mod inside the Minecraft
JVM at `http://127.0.0.1:39095/mcp`.

## Smoke

The protocol smoke waits for client startup, initializes MCP, checks the tool
catalog, and calls `minecraft_observe`:

```sh
(cd ../solaris && python3 -m tools.harness mcp)
```

To test a real connection and structured world reads against Solaris:

```sh
(cd ../solaris && python3 -m tools.harness mcp \
  --server-address 127.0.0.1:25565 \
  --exercise-input \
  --disconnect)
```

## Tool Surface

Read-only tools:

- `minecraft_observe`: player, health/food, pose, inventory, active container,
  screen, target, clocks, and recent chat.
- `minecraft_read_block`: one loaded block with state, fluid, sky-light, and block-light values.
- `minecraft_wait_for_loaded_block`: wait for an applied packet event that makes
  one block's chunk client-loaded, then return that block.
- `minecraft_wait_for_block_state`: wait for an exact block ID and optional
  state properties, rechecking only after applied client state events.
- `minecraft_scan_blocks`: an inclusive loaded box with the same fields, capped at 4096 cells.
- `minecraft_list_entities`: visible entities within 128 blocks, capped at 512.
- `minecraft_read_recipe_book`: bounded recipe display IDs accepted into the
  real client's recipe book.
- `minecraft_wait_for_visible_entity`: wait for an entity type within a bounded radius.
- `minecraft_wait_for_health_below`: wait for observed player damage.
- `minecraft_wait_for_inventory`: wait for an exact item count.
- `minecraft_wait_for_visible_item`: wait for an item entity near a position.
- `minecraft_wait_for_no_visible_item`: wait for that item entity to disappear.

Controls:

- Connection: `minecraft_connect`, `minecraft_wait_for_play`,
  `minecraft_disconnect`.
- Player: `minecraft_set_hotbar_slot`, `minecraft_select_hotbar_item`,
  `minecraft_drop_selected_item`, `minecraft_navigate_to_block`, `minecraft_approach_entity`,
  `minecraft_attack_entity_once`, `minecraft_attack_entity_until_drop_collected`, `minecraft_look`,
  `minecraft_look_at_block`, `minecraft_use_item_on`.
- Input: `minecraft_press_inputs`, `minecraft_wait_ticks`,
  `minecraft_open_inventory`, `minecraft_close_screen`,
  `minecraft_click_confirmation_button`, `minecraft_click_screen_button`,
  `minecraft_send_chat`.
- Containers: `minecraft_quick_move_container_slot`,
  `minecraft_click_container_slot`, and `minecraft_click_container_button`;
  ordinary clicks accept primary or secondary input and also confirm a
  server-side close/reopen, while quick moves and buttons require the active
  container state ID to advance.
- Regression: `minecraft_run_scenario` runs an existing deterministic
  in-client scenario and returns its structured report through MCP.
- Optional visual context: `minecraft_screenshot`.

`minecraft_break_block` always waits for the actual block to become air.
`expected_drop_count` accepts 0..64: zero requires no pickup (for example,
clearing snow barehanded), while positive counts require the requested inventory
increase. With zero, `pickup_confirmed` is not evidence that an item dropped.
The core compatibility gate clears natural snow cover before harvesting its
one dirt currency; it does not grant items or relax purchase/pickup assertions.

Item observations include exact enchantment IDs and levels. All Minecraft reads execute on the client thread. Applied packets and client
login/logout lifecycle events wake state waits; client ticks use a separate
condition and wake only tick-driven input, movement, and duration waits.
Timeouts only fail stalled operations. Inventory and entity waits sample once,
then block until the producer publishes a state change. Held inputs advance on
client tick events and release every pressed key in `finally`. World tools expose only
loaded client-visible state and fail on unloaded positions; they do not reveal
hidden server state. The endpoint binds to `127.0.0.1`, requires bearer auth,
validates `Host`/`Origin`, and limits request bodies. HTTP tool requests may run
concurrently; actual Minecraft reads/mutations are dispatched to the client
thread. A tick-held input request can therefore coexist with observation.

`minecraft_press_inputs.keys` and optional `minecraft_respawn.keys` accept the
existing semantic inputs plus canonical `key.keyboard.*` names. The complete
batch is validated against Minecraft before any input changes; invalid mixed
batches fail without pressing earlier keys. Named inputs invoke the actual
native keyboard callback, not X11 hardware or OS modifier/IME emulation.
Execution releases inputs in `finally`, including a failed respawn.

`minecraft_navigate_to_block` accepts `x`, `y`, `z`, and optional
`timeout_seconds` (0.1 to 120, default 8). It only targets a client-loaded
block within 48 horizontal and 8 vertical blocks of the player. It uses
ordinary movement inputs and collision detours, waits on client tick events,
and succeeds only after the client observes the player grounded and
collision-free within 1.5 horizontal blocks and 1.25 vertical blocks of the
target. Invalid, unloaded, blocked, and timed-out targets return errors.

The existing `/rpc` client-agent bridge remains available for the historical
real-client regression runner and can be launched independently with
`:fabric-agent:runClientAgent`.

</details>
