# Solaris Loader

Optional **client-side mod** for [Solaris](https://github.com/kaiserproger/solaris)
servers whose plugins — retained Luau `0.6.0` or WebAssembly components of the
`solaris:plugin` `0.7.0` contract — provide custom views, HUDs, assets, sounds,
items and blocks. If the server uses only server-side plugins, an ordinary
vanilla client is enough.

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

Current Loader protocol is **3** with client artifact index **schema 2**. The
plugin API the Loader carries is **0.6.0** for retained Luau packages and
**0.7.0** for WebAssembly components; the Loader itself handles the same typed
view, HUD, sound and item surface for both. These are alpha contracts:
match the server's declared compatibility, not just similar version numbers.
Schema-1 bundles, the removed UI/interaction content kinds and wire-2 messages
fail closed instead of being bridged. Releases remain previews, not broad
survival-readiness claims.

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
Active content, the open modal, every HUD instance with its held bindings, and
Loader sounds are cleared on disconnect.
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

`loader-core` contains the wire-3 manifest/ack and view codecs and validates
platform, permissions, and exact cache identities. `loader-fabric`, `loader-neoforge`,
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
with `solaris-client.json` as its first entry. That closed schema **2** accepts
`screens`, `world_previews`, `blocks`, `items`, `assets` and `sounds`; unknown
fields, unknown widget types and out-of-bound geometry fail activation, and
schema-1 indexes or the removed UI/interaction shapes have no decoder. Each
screen declares one of the six kinds `settlement`, `construction`, `economy`,
`garrison`, `army` or `hud` and a bounded widget list from `paged_table`,
`tabs`, `input_number`, `input_text`, `select_enum`, `resource_panel`,
`action_button` and `world_marker`; there is no HTML/JS, arbitrary Java,
filesystem access or client-side scripting.

A `hud` screen is non-modal: each owner's HUD is its own instance, several stay
visible at once, and an update or close names one exact instance and revision, so
a HUD never takes, replaces or closes the modal screen. A `hud` screen may also
declare up to eight `input_bindings` (`key`, `press_action`, `release_action`):
canonical `key.keyboard.*` names bound to owner-qualified view action ids, which
require `view_actions` content and `send_view_actions`. The client resolves every
declared key through Minecraft's own input table before installing the view; an
unknown, unbound or duplicated key refuses the whole view with no bindings rather
than guessing a key.

Each block declaration (`id`, owner
`model`, and `name`) is backed by its exact verified model asset. Each item
names a known vanilla base item and requires its exact verified
`assets/<namespace>/items/<path>.json` definition; a screen may reference one
declared item and one declared block. Up to 64 `world_previews` (`id`,
`blueprint_id`, 64-hex `content_hash`, one quarter-turn `rotation`, `size_x/y/z`
at most 64, at most 65,536 local `blocks`) require `present_world_previews`, and
`views` content requires `present_views`.
Up to 64 `sounds` (`id`) require `play_sounds` and same-bundle verified mono
OGG Vorbis assets. Shared pack preparation probes their headers/initial audio
and generates owner `sounds.json` indexes without late registry mutation.
The activated registry is bounded to 64 screens, 64 previews, one block per
bundle (eight total), 128 items, 128 assets, 64 sounds and 64 MiB of asset
bytes.
The three adapters publish the resulting immutable registry before sending the
acknowledgement and keep it available in Play. Unknown archive fields or
entries and mismatched asset bytes fail before acknowledgement. An acknowledged
Loader session can receive
`solaris:loader/view` in Play. One shared renderer serves both view modes. A
modal view screen is the paginated presentation for the exact originating
connection - a paged table of rows, tabs, typed inputs, a resource panel, action
buttons honouring `enabled` and showing `deny_reason`, and the `reason` line,
with the declared item/block displays - and its caption is the activated screen
declaration's own bounded `title`, never the wire `view_id`. A `hud` instance
renders only its declared widgets, so a widgetless HUD draws nothing while still
hosting its declared bindings. Both modes open with the authoritative model the
`open_view` carried. Referenced items render
through a local vanilla stack with Minecraft 26.1.2's owner-namespaced
`ITEM_MODEL`; no late registry mutation is used. `open_view` carries
`view_instance_id`, `revision`, `view_id`, `title` and the model;
`present_view` replaces the model and resets the action sequence; `close_view`
and disconnect clear the view. A disabled action sends nothing, and every
action carries the exact instance id, revision and action id with an increasing
`action_sequence` per connection and view instance. A declared key's press and
release edges travel as the same `view_action` message with the live instance,
revision and sequence. Presses that open or close a screen do not become
gameplay actions. Declared F2/F11 may report edges while preserving vanilla
screenshots/fullscreen; autorepeat is not an edge. Losing gameplay input focus
(a GUI, a hidden player, an inactive window) emits exactly one release per held
binding before the physical key-up. The server accepts the
bounded action only from the exact acknowledged Play session, re-reads the
instance, revision, owner and presented typed field schema, and delivers
`on_loader_view_action(event)` solely to the plugin owning the view
namespace; stale revisions, foreign owners, substituted field ids or types and
client-minted values are refused. Client-side `cancel_selection` and
`view_request` (`settlement` or `army`) use the same closed JSON
contract; the Loader registers only the bindings an activated `hud` screen
declares - no rebinding, client-side scripting, fallback key table, wire-2
fallback or client Lua runtime.

Activation and disconnect clear the open modal, every HUD instance with its held
bindings and action sink, Loader sound playback and the active registry, so
queued work or content from one server cannot reach a later
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
Installing or enabling a Loader adapter needs a client restart before those
native carriers exist; joining a new server with an already installed adapter
only activates verified per-connection content and reloads its resource pack,
not the frozen block registry.

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
