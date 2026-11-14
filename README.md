<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/kaiserproger/krasnokamen/main/docs/brand/krasnokamen-logo-on-dark.svg">
    <img src="https://raw.githubusercontent.com/kaiserproger/krasnokamen/main/docs/brand/krasnokamen-logo-on-light.svg" alt="Krasnokamen" width="420">
  </picture>
</p>

<h1 align="center">Krasnokamen Loader</h1>

<p align="center">
  <b>An engine for large Minecraft-compatible simulations.</b><br>
  Optional client mod for Fabric, NeoForge and Forge that shows Krasnokamen plugin content
</p>

<p align="center">
  <sub><b>Krasnokamen 1.0.0-rc.5 «Pauline»</b> · formerly Solaris (<a href="https://github.com/kaiserproger/krasnokamen/blob/main/docs/brand/README.md#formerly-solaris">why the name changed</a>)</sub>
</p>

Optional **client-side mod** for [Krasnokamen](https://github.com/kaiserproger/krasnokamen)
servers whose plugins — WebAssembly components of the `krasnokamen:plugin` `0.8.0`
contract — provide custom views, HUDs, assets, sounds,
items and blocks. If the server uses only server-side plugins, an ordinary
vanilla client is enough.

This is not a server plugin, modpack, launcher, or replacement for Fabric,
NeoForge, or Forge. Players do not need Gradle, Rust, MCP, or an API token.

## Download

Open [Releases](https://github.com/kaiserproger/krasnokamen-loader/releases) and
choose the Loader release required by your server. Download **one** platform
JAR from its Assets, not GitHub's source-code archive:

| Minecraft | Platform baseline | Download |
| --- | --- | --- |
| 26.3 | Fabric Loader 0.19.5 + Fabric API 0.163.0+26.3 | `loader-fabric-1.0.0-rc.2.jar` |
| 26.3 | NeoForge 26.3.0.83 | `loader-neoforge-1.0.0-rc.2.jar` |
| 26.3 | Forge 26.3-66.1.0 | `loader-forge-1.0.0-rc.2.jar` |

Use **Java 25**. The same platform JAR works across operating systems; the
launcher, Java runtime, Minecraft version, and mod loader must match. Do not
mix adapters, use a nearby Minecraft version, or install an agent/development
JAR. Fabric requires the separate Fabric API mod. A `SHA256SUMS` release asset
allows verification with `sha256sum -c SHA256SUMS --ignore-missing` on Linux;
on Windows, compare `Get-FileHash FILE.jar -Algorithm SHA256` with that file.

Current Loader protocol is **3** with client artifact index **schema 2**. The
server plugin frontend is the WebAssembly component API **0.8.0** only; the
Luau `0.6.0` frontend is retired, and the plugin API version does not change
the client wire protocol or artifact schema. These are alpha contracts:
match the server's declared compatibility, not just similar version numbers.
Schema-1 bundles, the removed UI/interaction content kinds and wire-2 messages
fail closed instead of being bridged. Releases remain previews, not broad
survival-readiness claims.

## Install in your launcher

1. Close the Minecraft instance.
2. Select Minecraft **26.3**, Java **25**, and one platform from the table.
3. Open the **game directory of that instance** and its `mods/` folder.
4. Copy the matching Krasnokamen Loader JAR there. For Fabric, also install the
   matching Fabric API JAR. Remove any older Krasnokamen Loader or Solaris Loader
   JAR from that same instance so exactly one remains.
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

Do **not** unzip the JAR or place it in the Krasnokamen server's `plugins/` folder.
For a custom game directory, its `mods/` folder is authoritative; operating
system default Minecraft folders are not reliable for separate instances.

## First connection

Loader asks before activating a server's requested permissions. **Allow**
downloads missing declared bundles, verifies their sizes and SHA-256 hashes,
then activates content. **Deny** disconnects without requesting those bundles.
A different server address or permission set prompts again.

Decisions and verified content are cached under `~/.krasnokamen/loader-cache/`.
Active content, the open modal, every HUD instance with its held bindings, and
Loader sounds are cleared on disconnect. An interrupted download's temporary
file is removed when its connection closes, including during Configuration.
Only approve servers you trust; a matching content hash establishes byte
identity, not that a server is trustworthy.

## Operator computer (Fabric, opt-in)

The in-game computer is a plain-text terminal/chat bridge, separate from Loader
wire 3. Enable the server's `[computer]` section and add
`-Dkrasnokamen.computer.enabled=true` to the **client JVM arguments**. Place an ordinary
iron block, connect as an unlocked level-4 operator, and right-click it. Type a
line and press **Send**; replies stream into bounded plain-text scrollback.
**Older/Newer** navigate history; **Close/Escape** terminates the direct child
process. Sneak-use bypasses the terminal. Breaking the carrier, leaving its
six-block radius, dimension travel, logout, or permission loss ends the session.
There is no special item to register and no new save format.

Server defaults are disabled, loopback-only, and built-in echo (`command = []`).
For an AI agent, the operator supplies an **absolute executable and literal argv**
plus an absolute working directory for a persistent UTF-8 stdin/stdout adapter.
The adapter receives one line per submission; output can arrive asynchronously.
Run it inside an operator-managed sandbox/dedicated account. This is not a PTY:
ANSI/curses interfaces and raw keystrokes are intentionally unsupported. Input
is never turned into shell argv, stderr is hidden, and no transcript is saved.
Never expose a privileged host shell. Remote opt-in still requires server-authenticated
identity (online mode or authenticated Velocity) and level 4; an offline username
is not authentication. See the server's ADR 0018 for the wire/security contract.

Fabric implements the terminal; NeoForge/Forge integration and graphical
real-client acceptance are not claimed. Java-only terminal code is intended to
work on Windows, Linux and macOS but only the recorded build/test platform is
validated. This feature neither captures nor controls the player's desktop.

### Local desktop-capture spike

The selected design is a small **local native helper → bounded RGBA8 frames →
LWJGL texture**, kept entirely on the player's machine. The spike
`X11ComputerCapture` explicitly invokes FFmpeg `x11grab` for one selected local
X11 display/region; `ComputerCaptureTexture` owns/reuses/uploads the OpenGL
texture on its creating render thread. Neither is wired to joining a server,
block use, nor downloaded content. No automatic capture, frame upload to a
server, or OS input forwarding exists.

| Platform | Backend choice | Current implementation |
| --- | --- | --- |
| Linux X11 | Native helper; XShm/XComposite for production | FFmpeg x11grab single-frame spike and isolated capture→texture smoke |
| Linux native Wayland | xdg-desktop-portal ScreenCast + PipeWire | Design only; no XWayland substitution or portal-consent bypass |
| Windows | Windows Graphics Capture; DXGI where necessary | Design only; not compiled or tested |
| macOS | ScreenCaptureKit, OS Screen Recording consent | Design only; not compiled or tested |

Capture and input forwarding require independent local permission prompts in a
future desktop UI; a server cannot grant them. This spike proves the frame/texture
boundary, **not** an in-world screen renderer or desktop-control implementation.
The gated `ComputerCaptureTest.linuxX11CaptureUploadsActualPixelsToTexture` paints
synthetic pixels in its own GLFW window, captures them, uploads/reuses a texture,
and checks OpenGL readback. This passed on Linux/Xvfb with `LIBGL_ALWAYS_SOFTWARE=1` on
2026-10-23 (2 tests, zero failures/skips); it is not hardware-driver or Minecraft
rendering evidence. Run only on an isolated display:

```sh
env -u WAYLAND_DISPLAY KRASNOKAMEN_CAPTURE_SMOKE_X11=1 LIBGL_ALWAYS_SOFTWARE=1 \
  xvfb-run -a -s '-screen 0 128x128x24' \
  ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2 \
  :loader-fabric:test --tests '*ComputerCaptureTest'
```

The ordinary test run skips that explicit display-dependent method; it is not a
capture pass. `:loader-core:test --tests '*ComputerProtocolTest'` tests codec
vectors, limits, stale-session rejection and bounded transcript behavior.

## Monitors and shared screens (Fabric)

With the server's `computer:monitor` bundle admitted for Fabric,
the Fabric mod shows the monitor model (the carrier's `facing` states are overridden
by the runtime resource pack) and speaks the `krasnokamen:screen` relay (server ADR 0018
"Screen sharing"). Right-click a monitor face (sneak to place blocks against it instead):

- **Someone else's active screen** (generic "on" panel): sends `REQUEST`; the owner
  must allow. Frames (H.264, at most 640 px) then replace the panel, stretched
  (letterboxed) over the whole W×H monitor rectangle, only while you look at it.
  `F8` leaves; `STOP` resets the panel. The mod never sends input to the owner.
- **An inactive monitor**: opens a "Share your screen?" dialog. Nothing is captured
  before you press *Share*, and frames upload only while the server's `DEMAND` is on
  (at most 5 fps, 128 KiB per picture). When a viewer asks, a centered *Allow / Deny*
  dialog answers it. Linux needs an X11 `$DISPLAY`, `ffmpeg` and `xdpyinfo`
  (view-only; Wayland is not supported); Windows captures the primary screen with
  `java.awt.Robot` (compiled, **not run on Windows**).

Pictures are H.264 (Constrained Baseline, Annex B, one picture per FRAME, `key` flag,
`KEY_REQUEST`; no JPEG) encoded and decoded in pure Java by JCodec 0.2.5 (FreeBSD licence,
2 MB, bundled jar-in-jar, no native library). Pure codec/state/geometry live in `loader-core`
(`ScreenProtocol`, `ScreenRelay`, `ScreenGeometry`, `ScreenVideo`); `loader-fabric` has the renderer, dialogs and
capture. The server must admit Fabric Loader sessions for the screen channel.

Screen audio is **receive-only** in Fabric: permitted owners' Opus packets (48 kHz
mono, 20 ms, at most 1000 bytes) decode in pure Java with bundled Concentus 1.0.2
(BSD licence). Playback uses Minecraft's sound engine at the monitor face centre,
with the Blocks/Master volume controls and linear attenuation out to 32 blocks.
The stream is supplied through Fabric's `FabricSoundInstance` extension, leaving
Fabric API's sound-engine mixin as the sole owner of stream dispatch.
The PCM queue is bounded to 100 ms; look-away STOP, revocation, F8, monitor closure
and disconnect discard it. Fabric never captures or transmits audio: whole-desktop
audio would include Minecraft/voice playback and feed it back into the stream.
Voice is a separate Simple Voice Chat mod connected through the server's SVC
bridge, not a voice client implemented by this Loader.

Native/Fabric H.264 live interop was exercised in both directions on isolated Xvfb
clients: changing native test-pattern pictures rendered in Fabric; a moving X11
source captured by Fabric rendered in native with increasing decoded-frame counts.
Separately, a combined native H.264/audio client streamed a 440 Hz source through
the real server/Opus path to Fabric while changing pictures remained visible.
Fabric's private OpenAL Wave File Writer output contained the tone; no-stream and
look-away controls contained only one-LSB output dither. No host audio device was
used. This gate required the Fabric-capable screen relay: the `4bcd34fc` server
still admits only native screen clients and was not credited with a pass.
Neither screen gate establishes Distant Horizons coverage.

## Troubleshooting

- **“Loader required”**: verify the launched instance, `mods/` location, adapter,
  and Minecraft version. Look for `krasnokamen_loader` in `logs/latest.log`.
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
[full installation/permission guide](https://github.com/kaiserproger/krasnokamen/blob/main/docs/KRASNOKAMEN_LOADER.md)
and [plugin API](https://github.com/kaiserproger/krasnokamen/blob/main/docs/PLUGINS.md).

## Build from source

Java 25 is required. This Gradle workspace builds independently of core;
integration tests use the sibling `../solaris` fixtures.

```sh
git clone https://github.com/kaiserproger/krasnokamen-loader.git
cd krasnokamen-loader
./gradlew --no-configuration-cache :loader-fabric:jar :loader-neoforge:jar :loader-forge:jar
```

The build uses ModDevGradle 2.0.148 for Minecraft 26.3 source transformation;
older ModDevGradle releases can fail to recompile `HolderSet`.

On Windows use `gradlew.bat`. JARs appear in each adapter's `build/libs/`.
Only `loader-fabric`, `loader-neoforge`, and `loader-forge` are player adapters;
`fabric-agent`, `java-agent`, and `bridge-core` are development tooling.

<details>
<summary>Developer reference: Loader protocol and content activation</summary>

### Shared Loader implementation

`loader-core` contains the wire-3 manifest/ack and view codecs and validates
platform, permissions, and exact cache identities. `loader-fabric`, `loader-neoforge`,
and `loader-forge` register the same Configuration-state manifest and
acknowledgement payloads through their native 26.3 networking APIs.

Bundle `loaders` metadata recognizes the shared supported-platform values
`native`, `fabric`, `neoforge`, and `forge`. The `native` value is preserved for
the Krasnokamen native client; it is not a Java adapter identity. Each Java adapter
is admitted only when its real platform is listed, and acknowledges as
`fabric`, `neoforge`, or `forge`, never `native`. Unknown, empty, or duplicate
supported-platform declarations still fail validation. This does not change
Loader protocol **3** or client artifact index **schema 2**.

The first manifest for an exact server address and permission set opens a
Minecraft confirmation screen. Allow and deny decisions are stored separately
per normalized server address in `permissions.properties` under the Loader
cache. A different server or changed permission set prompts again. The cache
directory defaults to `~/.krasnokamen/loader-cache` and may be overridden for tests
or isolated profiles:

```text
-Dkrasnokamen.loader.cacheDir=/path/to/loader-cache
```

Denial disconnects before an artifact request or staging file can be created.
Krasnokamen keeps the Loader Configuration handshake open for up to two minutes so
the first confirmation is not constrained by the ordinary ten-second pre-Play
packet timeout.
For each missing exact cache identity the client requests only that bundle,
streams bounded Configuration payloads into a temporary file, verifies the
declared size and SHA-256, and atomically publishes it before acknowledging the
manifest. An unknown, duplicate, or missing permission fails the handshake.
After all exact cache files pass verification, the shared core opens each ZIP
with `krasnokamen-client.json` as its first entry. That closed schema **2** accepts
`screens`, `world_previews`, `blocks`, `items`, `assets` and `sounds`; unknown
fields, unknown widget types and out-of-bound geometry fail activation, and
schema-1 indexes or the removed UI/interaction shapes have no decoder. Each
screen declares an exact plugin-owned `id`: a lowercase namespaced resource
identifier of 1..128 ASCII bytes, with no built-in product kinds. Its bounded
widget list uses `paged_table`, `tabs`, `input_number`, `input_text`,
`select_enum`, `resource_panel`, `action_button` and `world_marker`; there is no
HTML/JS, arbitrary Java, filesystem access or client-side scripting. Unknown,
foreign-namespace or conflicting declarations fail activation.

A screen with `overlay: true` is non-modal (the optional flag defaults to
false): each owner's overlay is its own instance, several stay visible at
once, and an update or close names one exact instance and revision, so an
overlay never takes, replaces or closes the modal screen. Only an overlay may
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
`krasnokamen:loader/view` in Play. One shared renderer serves both view modes. A
modal view screen is the paginated presentation for the exact originating
connection - a paged table of rows, tabs, typed inputs, a resource panel, action
buttons honouring `enabled` and showing `deny_reason`, and the `reason` line,
with the declared item/block displays - and its caption is the activated screen
declaration's own bounded `title`, never the wire `view_id`. Overlay instances
render only their declared widgets, so a widgetless overlay draws nothing while
still hosting its declared bindings. Neither mode invents interactive widgets
for undeclared model actions, fields or tabs. Declared actions and inputs absent
from the live model are hidden, not enabled no-op buttons or placeholder fields;
live disabled actions remain disabled. Both modes open with the authoritative
model the `open_view` carried. Referenced items render
through a local vanilla stack with Minecraft 26.3's owner-namespaced
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
`view_request` use the same closed JSON contract. The protocol-3 request field
`request_kind` carries the exact declared screen `id`, not a built-in kind or
alias; the client refuses undeclared keys and inactive connections. Presentation
comes solely from the declaration, never from the key.

An overlay may also declare `view_request_bindings` (`key`, `view`): canonical
`key.keyboard.*` names, each naming a screen of the same bundle, sharing the
overlay's eight-binding bound and key namespace with `input_bindings` (a key
is either a view-action edge or a view request, never both) and requiring the
same `view_actions` content and `send_view_actions` permission. These keys are
armed when the content is activated, with no live view instance: a press with
gameplay focus (no GUI, overlay or inactive window; a key remapped to vanilla
attack/use is suppressed like any other binding; autorepeat is not an edge)
sends one `view_request` for the declared screen on the acknowledged Play
connection, and the server answers with an ordinary `open_view`. A key used by
two activated bundles, a name the client's own key table cannot resolve, or a
target the bundle does not declare refuses activation. The Loader registers
only the bindings an activated overlay declares - no rebinding, client-side
scripting, fallback key table, wire-2 fallback or client Lua runtime.

Activation and disconnect clear the open modal, every HUD instance with its held
bindings and action sink, Loader sound playback and the active registry, so
queued work or content from one server cannot reach a later
connection. Declared
`assets/<namespace>/<path>` bytes now form one
transient required Minecraft client resource pack. The acknowledgement waits
for an exact-byte resource reload, and the pack is removed and reloaded out
when its originating connection closes. Schema-2 block declarations require
`id`, `model`, `name`, `carrier`, and `drop_item`. The drop item is
owner-qualified; the server validates its installed custom item and carrier. The
reserved carriers are gray (monitor), pink (Ruby), and blue (Sapphire) glazed
terracotta; each can be used by only one activated block. Black and red glazed
terracotta are not admitted. The runtime pack maps each declared vanilla
carrier's four facing states to the owner's block model and generates a preview
item model from that same block model. The server determines the actual block states;
the client neither registers custom carrier blocks nor sends state IDs in its
acknowledgement. The runtime pack is removed on disconnect.

`krasnokamen:loader/sound` resolves only an activated sound on its originating
connection. The shared native presenter supports personal or fixed-position
one-shots, bounded volume/pitch, distance attenuation, and owner-id-local stop.
The [live fixture gate](../solaris/examples/loader-live-gate/README.md) captures actual
audio output for all three platforms; no acknowledgement or chat response is
treated as evidence that the sound played.

Run the scoped bridge, agent, and platform tests against the intended core
checkout (override `KRASNOKAMEN_REPO_ROOT` for a worktree):

```sh
KRASNOKAMEN_REPO_ROOT=../solaris ./gradlew --no-daemon \
  :bridge-core:test \
  :java-agent:test \
  :loader-core:test \
  :loader-fabric:test \
  :loader-neoforge:test \
  :loader-forge:test
```

`LoaderLiveGateFixtureTest` loads the core checkout's generated
`rich-content.zip` archives, not its JSON templates. After changing the fixture
screen schema, rebuild those archives and manifest hashes with the core's
`tools/build-loader-live-gate-fixture.sh` before running these tests; legacy
screen `kind` fields are rejected rather than adapted.

Build distributable platform jars with:

```sh
./gradlew :loader-fabric:jar :loader-neoforge:jar :loader-forge:jar
```

</details>

<details>
<summary>Developer reference: real-client MCP automation</summary>

### Krasnokamen Minecraft Client MCP

The Krasnokamen core harness locates this workspace through `KRASNOKAMEN_LOADER_ROOT`
(default `../solaris-loader`). The following automation commands require the
core checkout; normal player installations do not.

The development client agent embeds an authenticated Streamable HTTP MCP
server in the real Minecraft Java 26.3 client. MCP hosts can inspect the
client-visible world and drive ordinary client inputs without using screenshots
as state assertions or an external command-string launcher.

The plain-Java harness also retains 26.1.2. Its screen and overlay belong to
`Minecraft`, and its chat belongs directly to `Gui`; 26.3 moved those APIs to
`Gui` and `Hud`. Cached native bindings cover screen/overlay/chat access,
screenshots, keyboard types, HUD visibility, team colors, hand swings, predicted
drops and front-sign packets/text. They invoke the runtime's actual native APIs;
missing layouts fail explicitly rather than returning empty or simulated state.
The build still targets 26.3; a retained client must use its own unmodified Mojang
runtime JAR, never the compile-time client JAR.
Run `:java-agent:test` with `-Psolaris.testClientJar=/path/to/client.jar` once for
each retained runtime. This changes only the test runtime classpath, not the 26.3
compile target. `NativeMemberCompatibilityTest` checks every direct native member
reference in the compiled agent, including inherited JVM descriptors and changed
return types; binding tests exercise both native layouts and the actual runtime API.
Plain-Java premain installs the native lifecycle/packet event transformer before
Minecraft classes load. Screen/overlay changes, client ticks and applied packets
publish the same event-owner signals used by modded runtimes; readiness and
inventory waits do not depend on polling or a mod loader.
`minecraft_observe` exposes the exact applied SystemChat packet journal as
`received_system_chat`, with `dropped_system_chat` reporting loss beyond its 4096
receipt bound. It preserves duplicates and packet order independently of GUI
history; matrix input fences reject a journal with lost receipts.
`minecraft_list_entities` reports experience-orb `value` from the runtime's
synced `DATA_VALUE` accessor. It does not report `Count`: multiplicity is
server-only NBT, so matrix conservation queries `Value` and `Count` by each
received entity UUID and labels those console oracles separately from client data.

## Start

Use Java 25 and choose a fresh bearer token:

```sh
export KRASNOKAMEN_CLIENT_MCP_TOKEN="$(openssl rand -hex 32)"
export KRASNOKAMEN_CLIENT_MCP_PORT=39095
(cd ../solaris && python3 -m tools.harness client)
```

The launcher calls the fixed Gradle task
`:fabric-agent:runClientMcp`. Optional settings are:

- `KRASNOKAMEN_CLIENT_MCP_GAME_DIR`: isolated Minecraft game directory.
- `KRASNOKAMEN_CLIENT_MCP_USERNAME`: 1..16 ASCII letters, digits, or underscores.
- `KRASNOKAMEN_CLIENT_MCP_PORT`: free IPv4 loopback port, default `39095`.

Normal launch checks for an occupied MCP port before starting Gradle; the
in-client bind remains authoritative if another process races for the port.
Environment token and port values describe the current run and take precedence
over stale JVM properties. Treat HTTP 401 as a wrong endpoint/token pair, not
as a retryable MCP failure.

Validate Java, configuration, MCP transport tests, and the Gradle adapter
without launching Minecraft:

```sh
(cd ../solaris && KRASNOKAMEN_CLIENT_MCP_TOKEN=local-check-token \
  python3 -m tools.harness client --check)
```

The same run can be started directly:

```sh
KRASNOKAMEN_CLIENT_MCP_TOKEN="$KRASNOKAMEN_CLIENT_MCP_TOKEN" \
  ./gradlew --no-configuration-cache :fabric-agent:runClientMcp
```

## Distant Horizons Java validation

The `dh-protocol16` in-client scenario observes the installed, unmodified
Distant Horizons mod through its runtime API. It fails closed when DH is absent
or negotiation/registry resolution fails; it never injects LODs, cache rows or
generation requests. The `configure`, `control`, `landmark`, `edit`, `render`,
`render-update`, `state`, `debug` and `disconnect` phases are named `dh-protocol16-<phase>`.

`tools/dh_scenario.py` is the Java-client driver used by the core harness's
`dh-gate` profile. Set `KRASNOKAMEN_LOADER_ROOT` to this checkout and provide a
worktree-built `KRASNOKAMEN_SERVER_BINARY`, publisher-verified `KRASNOKAMEN_DH_JAR`,
Java 25 and Xvfb. Prepare `.analysis/prism-26.3/minecraft` here by copying
the owner's Prism 26.3 instance as a **read-only template**, excluding saves,
screenshots, logs, mods and `Distant_Horizons_server_data`. Install only the
pinned DH jar in the private template's `minecraft/mods`; never edit the
original instance. Each control/enabled/no-DH game directory is another
isolated copy under this checkout's `.analysis/dh-runs`.

The baseline pins are MC 26.3, DH 3.3.3/protocol 16, Fabric loader 0.19.5
with Fabric API 0.163.0+26.3, or NeoForge 26.3.0.83.
The Fabric mod metadata expands its minimum loader dependency from the selected
`fabricLoaderVersion`, matching the artifact it was compiled against.
The driver uses core's canonical server/Xvfb/MCP lifecycle and generates a fresh
local MCP bearer token for each client; no external credential is needed or written.
From the selected core checkout run `python3 -m tools.harness run dh-gate
--platform fabric`, then `--platform neoforge`.

The scenario compares independently clean server-DH-off/on caches at a fixed
camera, with vanilla radius 4 and DH radius 128. Its asymmetric edited landmark
is 1024 blocks away and is never approached by either test client. A separate
disposable **no-DH** preparation process builds it through real server commands,
waits on actual loaded-block/state packet events, then returns the saved player
to the origin and exits. It has a different game directory and connects directly;
the passive proxy covers only the independently clean control/enabled clients.
No LOD/cache data is copied from preparation. The gate retains actual
DH API column values, the unmodified server packet stream's vanilla chunk
coordinates and DH sections, screenshots, warm reconnect, live edit,
dimension/reset isolation and a separate no-DH vanilla join. Local receipts
are `dh-scenario.json` and `dh-packet-evidence.json` inside the core harness
artifact directory; a prepared copy or a unit-test pass is not graphical
compatibility evidence.
The clean-cache landmark wait is bounded to 30 minutes: the real client requests
nearer leaves first within the negotiated two-requests-per-second limit. It waits
on actual cache commits, never a blind delay or injected priority request.
Each API column observation supplies and closes a fresh DH-provided soft cache,
as required by the public terrain API, so committed live edits are not hidden by
an earlier observation's cached source.
The live edit extends the gold tower from Y=200 to Y=232 and replaces it with
emerald, with daylight frozen by `gamerule advance_time false`. Geometry changes
are intentional: upstream DH 3.3.3 can miss material-only swaps in parent LODs
when compacted raw datapoint ids stay identical. This is an accepted client
limitation, not evidence that unchanged-geometry material swaps render correctly.
Private fixture options mark vanilla's movement tutorial complete so its persistent
toast cannot obscure the distant landmark; DH warnings remain unchanged.
Control/render phases hide the ordinary HUD (F1-equivalent) so command chat
cannot cover the distant landmark; debug phases restore it for the F3 evidence.
Play alone is not visual readiness: connection setup also waits for the observed
resource-reload overlay to close. After decoded landmark data, the `render` phase
waits on Minecraft tick events for an unmodified DH visible VBO container covering
the landmark, with a nonzero number of uploaded/non-null vertex buffers and no vanilla landmark chunk.
It retains the section, VBO count and container identity before screenshots.
Before editing, the driver records the warm connection's uploaded geometry in
`previous-render.json`. The `render-update` phase additionally requires a
replacement uploaded container; the old warm-cache VBO is not evidence that
an emerald edit has reached the renderer.
Even a replacement can precede DH's asynchronous leaf-to-parent aggregation.
The driver therefore uses Pillow to inspect the actual upper-pillar pixels at
the pinned camera/FOV: sky-only control must contain no gold, the cold frame
must contain gold, and the edited frame must contain emerald. It waits on
canonical client-state producer events between actual captures, keeping only
the latest frame for each label. Daylight is set and observed for these color
predicates; nearby ground/greenery is outside their recorded crop.
These checks do not replace reviewing the actual captured landmark and lighting.
The configure phase also writes `client-runtime-manifest.json` with running JVM
classpath/module-path files and DH/Minecraft/agent code-source SHA-256 hashes.
Night/day captures wait on observed client clock changes, not command submission.
The DH-specific disconnect phase invokes the ordinary game disconnect on the
client thread and awaits its completion for up to two minutes while DH drains
its request/mesh queues, then returns to the title screen for reconnect. The
low-level facade's progress screen is not a completed return-to-menu flow;
generic interactive-operation deadlines are unchanged.
For this explicitly owner-authorized local fixture, the driver accepts Loader's
`present_views`/`send_view_actions` prompt through its guarded exact-title MCP
button API. It matches the current isolated loopback address and retains the
prompt screenshot and decision. It neither prepopulates a trust whitelist nor
accepts other confirmation screens, and never modifies the owner's instance.

## Codex

Register the endpoint once. Start Codex from an environment containing the
same bearer token used by the Minecraft process:

```sh
codex mcp add minecraft-client \
  --url http://127.0.0.1:39095/mcp \
  --bearer-token-env-var KRASNOKAMEN_CLIENT_MCP_TOKEN
```

No proxy process is required. The MCP endpoint is the mod inside the Minecraft
JVM at `http://127.0.0.1:39095/mcp`.

## Smoke

The protocol smoke waits for client startup, initializes MCP, checks the tool
catalog, and calls `minecraft_observe`:

```sh
(cd ../solaris && python3 -m tools.harness mcp)
```

The Java smoke sources compile against the vanilla 26.3 client JAR, not
NeoForge-only extensions. Filled-shulker custody checks inspect all nonempty
container templates and slot zero (`copyOne()`), retaining the exact three-diamond
assertion. Bucket diagnostics use the vanilla outline/no-fluid POV ray and
interaction range; actual bucket use still follows the native `Item.use` path.

To test a real connection and structured world reads against Krasnokamen:

```sh
(cd ../solaris && python3 -m tools.harness mcp \
  --server-address 127.0.0.1:25565 \
  --exercise-input \
  --disconnect)
```

## Tool Surface

Read-only tools:

- `minecraft_observe`: player, health/food, pose, inventory, active container,
  screen, target, clocks, and recent chat. The premain agent also exposes
  ordered applied system-chat packet receipts and a dropped-receipt count;
  bounded GUI chat history is not a substitute for this journal.
- `minecraft_read_block`: one loaded block with state, fluid, sky-light, and block-light values.
- `minecraft_wait_for_loaded_block`: wait for an applied packet event that makes
  one block's chunk client-loaded, then return that block.
- `minecraft_wait_for_block_state`: wait for an exact block ID and optional
  state properties, rechecking only after applied client state events.
- `minecraft_scan_blocks`: an inclusive loaded box with the same fields, capped at 4096 cells.
- `minecraft_list_entities`: visible entities within 128 blocks, capped at 512. Entity IDs are signed 32-bit values, including server-sent presentation entities with negative IDs.
- `minecraft_read_recipe_book`: bounded recipe display IDs accepted into the
  real client's recipe book.
- `minecraft_wait_for_visible_entity`: wait for an entity type within a bounded radius.
- `minecraft_wait_for_health_below`: wait for observed player damage.
- `minecraft_wait_for_inventory`: wait for an exact item count.
- `minecraft_wait_for_visible_item`: wait for an item entity near a position.
- `minecraft_wait_for_no_visible_item`: wait for that item entity to disappear.
- `minecraft_read_item_components`: on-demand native received-stack inspection,
  separate from the tick observer. Arguments are `source` (`selected`, `player`,
  or `container`) and `slot` (0..32767): `selected` forbids `slot` and resolves the
  actual selected player inventory slot; `player` and `container` require it.
  Player indices address the native player inventory; container indices address
  the actual visible native menu, never a player-inventory fallback. Out-of-range
  slots, no visible container, or an inactive client fail explicitly.
  Returns `source`, resolved `slot`, actual `item_id`, `count`, and `components`,
  keyed by present native namespaced component IDs. Container reads also return
  `container_id` and `container_state_id`. Standard values use Minecraft's
  registry-aware native JSON codecs, including integer `minecraft:max_damage`
  and `minecraft:damage`. `minecraft:custom_data`, when present, is
  `{"snbt":"<native Compound SNBT>"}` to preserve NBT numeric and array types;
  absence remains absence. This SNBT is not the core's canonical binary NBT
  carrier or a product-quality interpretation. Empty slots return
  `minecraft:air`, count 0 and an empty component object. Unregistered,
  non-serializable or codec-rejected components fail the whole read rather than
  silently omitting values or inventing equivalents.

Controls:

- Connection: `minecraft_connect`, `minecraft_wait_for_play`,
  `minecraft_disconnect`.
- Player: `minecraft_set_hotbar_slot`, `minecraft_select_hotbar_item`,
  `minecraft_drop_selected_item`, `minecraft_navigate_to_block`, `minecraft_approach_entity`,
  `minecraft_attack_entity_once`, `minecraft_attack_entity_until_drop_collected`, `minecraft_look`,
  `minecraft_look_at_block`, `minecraft_use_item_on`.
- Input: `minecraft_press_inputs`, `minecraft_wait_ticks`,
  `minecraft_open_inventory`, `minecraft_close_screen`,
  `minecraft_request_loader_view` (`view_key`: exact declared namespaced ID;
  requires the acknowledged live Loader connection),
  `minecraft_click_confirmation_button`, `minecraft_click_screen_button`,
  `minecraft_send_chat`.
- `minecraft_set_screen_input`: genuine on-client-thread native EditBox editing.
  Requires exact `expected_screen_class` and `expected_title` (1..256 characters),
  `input_label` (1..128), and string `value` (0..256, including empty). The label
  must name exactly one current native EditBox; it must be visible, enabled and
  editable. The value must fit that widget's actual native maximum length.
  The operation focuses the widget and invokes native `setValue`, including its
  real responder, then verifies the widget/screen remain current and the native
  value equals the requested value. Missing, ambiguous, disabled, stale or
  truncating/rejecting controls fail explicitly. No Loader model/packet field is
  written directly; a later ordinary action button submits the edited fields.
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

Earned zombie-combat scenarios keep the outside-reach natural-hostile scan
active for 40 seconds after the first nightfall time, covering the delay
before surface spawning instead of switching to a melee-range-only scan
before hostiles can appear. Killing the hostile and collecting its drop
remain required. Each attack waits for the client's observed vanilla attack
strength to reach 1.0, using the server-synchronized attack-speed attribute.
The first attack records that attribute and held item for live-run evidence.
An earned sword (iron, then stone, then wood) is preferred over an already-
earned pickaxe; the tool-only stage never grants a weapon.
Hostile scans choose only isolated targets: other loaded living hostiles must
remain beyond six blocks of both the target and player, and creepers beyond
seven. Live approach and attack recheck that isolation, verify eye-to-hitbox
melee reach and line of sight, and kite between fully charged attacks. Combat
movement checks the swept footprint against loaded, dry footing and refuses
falls exceeding three blocks. At ten health or below the driver retreats;
re-engagement requires fourteen health and, when hungry with owned edible food,
observed food increase plus inventory consumption. It never grants food or
health. Hostile-drop collection also refuses dangerous descents or nearby
hostiles instead of chasing loot into another fight.
Shield-block probes require a received `minecraft:item.shield.block`
sound-entity packet bound to the local player during the probe, plus survival
without health loss. Unchanged health alone is not proof, and shield wear is
recorded but not substituted for the event: vanilla blocks below three damage
do not consume durability.
Stone progression can excavate grass/dirt above a buried natural stone
target after a surface approach fails: it stands over the column, actually
breaks and collects each overburden block, then reselects the earned pickaxe
and still requires the stone's cobblestone drop and pickup. Reachable stone is
mined without a redundant descent toward each target; wall stone and shallow
cobblestone drops are preferred over deep natural shafts, whose uncollected
drops remain honest failed pickups while other stone is mined. After mining,
the exit cuts supported one-up/one-forward steps with three-block jump
clearance, also clearing the takeoff ceiling over every column touched by
the actual player hitbox, and choosing another direction at a cave gap. Where
no supported step exists it can jump-place dirt/cobblestone actually owned
beneath itself, requiring the real placed block, inventory consumption and
grounded rise. A fully observed clear shaft with enough owned blocks prefers
that direct pillar over cutting another staircase.
Pillar headroom cuts collect their observed ceiling drops before measuring the
placement inventory baseline, so a falling earned dirt drop cannot mask the
required one-block consumption when the player lands.
The exit reserves the caller's pending recipe cobblestone count: only surplus
cobblestone or earned dirt can become pillar blocks. Otherwise it carves
supported steps, retaining the materials needed for the actual next craft.
Each raised staircase arrival stops walking and jumping at the target, then
waits for observed grounding; airborne coordinates alone never complete a step
or the surface exit.
The exit budget scales with depth (forward/takeoff breaks plus arrival per
rise), rather than the short cliff limit. The earned pickaxe, real break/drop
proof and actual arrival at the original surface remain required; no blocks
or items are injected, and an unbreakable or unreachable exit remains blocked.
Iron search also examines actual loaded buried ore in a bounded 32-block
horizontal/64-block downward window when no exposed ore is found, covering
the main low-elevation iron band rather than assuming exposed surface ore. It only enters a
dry, completely observed solid column above that ore, breaks each overburden
layer with an owned pickaxe, and waits for one-block grounded descent after
each break. Shaft ingress and pillar placement first sneak-walk to a stopped,
grounded hitbox entirely inside the column, avoiding sprint coast onto a
neighbour's footing. The ore itself still requires its real raw-iron drop and inventory
increase. Returning to another station first reuses the earned excavation
exit to the recorded surface; hidden resources are never injected and no
minimum ore abundance is assumed.
Block and column approaches also cut a supported staircase toward a cliff when
an ordinary one-block jump cannot clear it, or a level passage when the ridge
keeps rising above the target. Each deterministic segment is limited to four
columns and four segments per approach, requires loaded dry existing
footings and three-block jump clearance, and only removes listed natural rock
or soil with a pickaxe actually present in inventory, checking each block's
actual drop type. It reuses the excavation exit's
real break/drop checks and observed walking arrival, restores the previously
held item, and never teleports, injects blocks or relaxes scenario acceptance.
Only an observed supported cliff plan activates a separate 120-second mining
budget measured from approach start; unobstructed approaches retain their
caller's ordinary deadline. Repeated segments cannot renew that budget.
Passive-food and generic block/item-drop collection steer toward the lower
drop column's center when the item lies below the vanilla pickup volume, rather than repeatedly
jumping onto adjacent raised footing. Once the player's inflated pickup box
intersects the item, movement stops. Item removal is not success: a separate
bounded two-second applied-inventory wait still requires the requested item
count to increase (and identity-bound collection still requires its take event).

</details>

## Java battle gate (26.1.2)

`tools/battle_scenario.py` is invoked by the core harness's `java-battle-functional`
profile, with `--platform fabric` or `--platform neoforge`. The core harness
owns receipts and the shared Xvfb/client lifecycle; this repository owns the
Java scenario. Each run uses clean game directories, the ordinary server-pushed
resource pack, a copied battle world and the live `PovRed`/`CamSpec` roles.
Received entity observations include hands, item models, blocking state and
vehicle identity so combat targets and horse riders are not guessed from proximity.
Functional assertions and retained screenshots are distinct: equipment, horse/rider
and raised-shield rendering still require looking at the frames in the report.

The shared Loader mixins preserve the humanoid shield-blocking pose for zombie
renderers (including battle husks). Vanilla's zombie-arm animation otherwise
overwrites both arms after the humanoid model computes its item poses. Nonblocking
zombies keep their vanilla arm animation; Java entities and horses keep vanilla models.

The automatic-pack cell uses vanilla's per-server **Server Resource Packs:
Enabled** preference, stored in the private `servers.dat`. A new/default
server entry remains **Prompt** and must not be reported as automatic.
MCP connects load matching vanilla server entries rather than replacing their
pack policy with an unsaved default `ServerData`.
The Loader MCP packet hook publishes applied health packets as well as tick/state
events. Respawn confirmation requires a fresh authoritative health event; an
ordinary tick alone cannot satisfy that fence.
The profile's PASS covers functional assertions only, including the absence of
battle-pack model-load failures in both Java logs. `java-battle.json` keeps
`visual_review.status=pending`: 3D weapons/shields, horses/riders and the raised
shield pose need separate screenshot review. A functional result is not visual
acceptance. The original invalid pack is a negative control for the model-load
check.
