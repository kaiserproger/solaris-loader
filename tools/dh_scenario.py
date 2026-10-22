#!/usr/bin/env python3
"""Real MC 26.1.2 + DH protocol-16 scenario, using core's canonical lifecycle.

The copied Prism template is local-only. This driver never modifies the owner's
instance, sends a seed, injects DH cache data, or substitutes a protocol bot.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import secrets
import shutil
import struct
import sys
from pathlib import Path


def landmark_pixels(path: Path, material: str) -> dict:
    """Observe the upper pillar at the fixture's fixed camera/FOV, not terrain below it."""
    from PIL import Image
    if material not in ("gold", "emerald"):
        raise ValueError(f"unsupported fixture marker material: {material}")
    with Image.open(path) as source:
        image = source.convert("RGB")
        scale = image.height / 480
        center = image.width / 2
        region = [round(center - 2 * scale), round(220 * scale),
                  round(center + 5 * scale), round(241 * scale)]
        matching = 0
        for y in range(region[1], region[3]):
            for x in range(region[0], region[2]):
                red, green, blue = image.getpixel((x, y))
                if material == "gold":
                    matching += red >= green + 6 and green >= blue + 25
                else:
                    matching += green >= red + 12 and green >= blue + 8
        minimum = max(8, round(8 * scale * scale))
        return {"material": material, "image_size": list(image.size), "region": region,
                "matching_pixels": matching, "minimum_pixels": minimum, "passed": matching >= minimum}


class PacketEvidence:
    def __init__(self) -> None:
        self.stage = "startup"
        self.center = None
        self.channels = set()
        self.messages = collections.Counter()
        self.client_messages = collections.Counter()
        self.client_events = []
        self.events = []
        self.buffers = {}
        self.sections = []
        self.initializations = []
        self.null_sync_responses = 0
        self.max_vanilla_radius = 0
        self.landmark_vanilla_sent = False

    def packet(self, phase: str, packet: int, payload: bytes) -> None:
        from tools.harness.backends.light_wire import Reader
        if phase == "login" and packet == 2:
            self.center = None
        if phase == "play" and packet == 0x5E:
            reader = Reader(payload)
            self.center = [reader.varint(), reader.varint()]
        if phase == "play" and packet == 0x2D:
            x, z = struct.unpack_from(">ii", payload)
            if self.center is None:
                raise RuntimeError("vanilla chunk arrived before its stream-center packet")
            radius = max(abs(x - self.center[0]), abs(z - self.center[1]))
            self.max_vanilla_radius = max(radius, self.max_vanilla_radius)
            self.landmark_vanilla_sent |= x == 64 and z == 0
            self.events.append({"stage": self.stage, "chunk": [x, z], "center": self.center, "radius": radius})
        if (phase, packet) not in (("configuration", 1), ("play", 0x18)):
            return
        reader = Reader(payload)
        channel = reader.take(reader.varint()).decode("utf-8")
        self.channels.add(channel)
        if channel != "distant_horizons:msg":
            return
        body = payload[reader.offset:]
        protocol, kind = struct.unpack_from(">HH", body)
        if protocol != 16:
            raise RuntimeError(f"unexpected server DH protocol {protocol}")
        self.messages[kind] += 1
        body = body[4:]
        if kind == 2:
            strings = []
            offset = 0
            for _ in range(3):
                length = struct.unpack_from(">H", body, offset)[0]
                offset += 2
                strings.append(body[offset:offset + length].decode("utf-8"))
                offset += length
            self.initializations.append({"stage": self.stage, "dimension": strings[0],
                                         "server_key": strings[1], "level_key": strings[2]})
        elif kind == 10:
            buffer_id, length = struct.unpack_from(">ii", body)
            if len(body) != 9 + length:
                raise RuntimeError("invalid DH split frame length")
            if body[-1]:
                packed = struct.unpack_from(">Q", body, 8)[0]
                x, z = (packed >> 8) & 0xFFFFFFF, (packed >> 36) & 0xFFFFFFF
                x = x - (1 << 28) if x >= (1 << 27) else x
                z = z - (1 << 28) if z >= (1 << 27) else z
                self.buffers[buffer_id] = [packed & 255, x, z]
        elif kind == 8:
            if body[4]:
                buffer_id = struct.unpack_from(">i", body, 5)[0]
                self.sections.append({"stage": self.stage, "kind": "response",
                                      "section": self.buffers.pop(buffer_id)})
            else:
                self.null_sync_responses += 1
        elif kind == 9:
            length = struct.unpack_from(">H", body)[0]
            buffer_id = struct.unpack_from(">i", body, 2 + length)[0]
            self.sections.append({"stage": self.stage, "kind": "update",
                                  "section": self.buffers.pop(buffer_id)})

    def client_packet(self, phase: str, packet: int, payload: bytes) -> None:
        channel = b"distant_horizons:msg"
        prefix = bytes([len(channel)]) + channel
        if not payload.startswith(prefix):
            return
        body = payload[len(prefix):]
        protocol, kind = struct.unpack_from(">HH", body)
        self.client_messages[kind] += 1
        if len(self.client_events) < 128:
            self.client_events.append({"stage": self.stage, "phase": phase, "packet_id": packet,
                                       "protocol": protocol, "kind": kind, "body_hex": body[4:260].hex()})

    def report(self) -> dict:
        return {"origin": "unmodified_loopback_server_stream", "channels": sorted(self.channels),
                "client_message_counts": dict(self.client_messages), "client_messages": self.client_events,
                "message_counts": dict(self.messages), "chunk_packets": self.events,
                "max_vanilla_radius": self.max_vanilla_radius,
                "landmark_vanilla_sent": self.landmark_vanilla_sent,
                "initializations": self.initializations, "completed_sections": self.sections,
                "null_sync_responses": self.null_sync_responses}


def run(args: argparse.Namespace) -> int:
    loader = Path(__file__).resolve().parents[1]
    repo = args.repo_root.resolve()
    artifact = args.artifact_dir.resolve()
    artifact.mkdir(parents=True, exist_ok=True)
    os.environ["SOLARIS_LOADER_ROOT"] = str(loader)
    # System project properties override the normal Loader defaults for this
    # independently pinned DH cell; no owner Gradle file is changed.
    os.environ["GRADLE_OPTS"] = os.environ.get("GRADLE_OPTS", "") + (
        " -Dorg.gradle.project.fabricLoaderVersion=0.18.5"
        " -Dorg.gradle.project.fabricApiVersion=0.149.1+26.1.2"
        " -Dorg.gradle.jvmargs=-Xmx768m -Dorg.gradle.workers.max=2"
    )
    sys.path.insert(0, str(repo))
    from tools.harness import runtime
    from tools.harness.mcp import McpClient
    from tools.harness.profiles import ProfileContext
    from tools.harness.backends.gameplay import plugin_directory, prepare_operator_fixture
    from tools.harness.backends.light_wire import LightWireProxy
    import subprocess

    template = loader / ".analysis/prism-26.1.2/minecraft"
    games = loader / ".analysis/dh-runs" / artifact.name
    report = {"passed": False, "minecraft": "26.1.2", "dh": "3.3.3", "protocol": 16,
              "platform": args.platform, "camera": [0, 200, 0, -90, 4.5],
              "landmark": [1024, 80, 0, 1031, 200, 7], "dh_radius_chunks": 128,
              "edited_landmark": [1024, 80, 0, 1031, 232, 7],
              "vanilla_radius_chunks": 4, "cache_policy": "independently clean control/enabled; warm cache retained",
              "server_binary_sha256": hashlib.sha256(args.server_bin.read_bytes()).hexdigest(),
              "dh_jar_sha256": hashlib.sha256(args.dh_jar.read_bytes()).hexdigest(),
              "prism_template": str(template), "commands": [], "observations": {}, "screenshots": []}
    port = runtime.reserve_port()
    context = ProfileContext(artifact)
    plugin_directory(context)
    prepare_operator_fixture(context, ["DistantGate"])
    server = client = xvfb = mcp = proxy = None
    server_log = client_log = display_log = None
    capture = PacketEvidence()

    def check(condition, detail):
        if not condition:
            raise RuntimeError(detail)

    def start_server(enabled, world_name):
        nonlocal server, server_log
        source = (repo / "playable.toml").read_text()
        replacements = {
            "port = 25565": f"port = {port}",
            'world_dir = ".analysis/test-world-v12"': f'world_dir = "{artifact / world_name}"',
            'vanilla_data_dir = "data/vanilla"': f'vanilla_data_dir = "{os.environ.get("SOLARIS_CLIENT_DATA_DIR", repo / "data/vanilla")}"',
            'directory = "plugins"': f'directory = "{artifact / "plugins"}"',
            "seed = 0": "seed = 81",
        }
        for old, new in replacements.items():
            check(source.count(old) == 1, f"expected exactly one fixture token {old}")
            source = source.replace(old, new)
        source += f"\n[distant_horizons]\nenabled = {str(enabled).lower()}\nradius_chunks = 128\n"
        source += "\n[lod]\nenabled = false\nshare_terrain_keys = false\n"
        config = artifact / f"server-{world_name}-{enabled}.toml"
        config.write_text(source)
        log = artifact / f"server-{world_name}-{enabled}.log"
        server_log = log.open("ab")
        command = [str(args.server_bin), "--config", str(config), "--no-console"]
        report["commands"].append(command)
        server = subprocess.Popen(command, cwd=repo, stdout=server_log,
                                  stderr=subprocess.STDOUT, start_new_session=True)
        runtime.wait_port(port, 120, server, log_path=log)

    def stop_server():
        nonlocal server, server_log
        runtime.stop_process(server, interrupt=True)
        server = None
        if server_log:
            server_log.close()
            server_log = None

    def stop_client():
        nonlocal client, client_log, mcp
        closing, mcp = mcp, None
        try:
            if closing:
                closing.close()
        finally:
            runtime.stop_process(client, interrupt=True)
            client = None
        if client_log:
            client_log.close()
            client_log = None

    def launch(stage, with_dh=True, address=None):
        nonlocal client, client_log, mcp
        game = games / stage
        shutil.copytree(template, game, ignore=shutil.ignore_patterns("saves", "logs", "screenshots", "Distant_Horizons_server_data"))
        mods = game / "mods"
        mods.mkdir(exist_ok=True)
        if with_dh:
            shutil.copy2(args.dh_jar, mods / args.dh_jar.name)
        else:
            (mods / args.dh_jar.name).unlink(missing_ok=True)
        (game / "options.txt").write_text(
            "version:4790\nonboardAccessibility:false\nguiScale:2\nrenderDistance:4\n"
            "simulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\nfullscreen:false\ntutorialStep:none\n")
        token, mcp_port = secrets.token_urlsafe(32), runtime.reserve_port()
        log = artifact / f"client-{stage}.log"
        client, client_log = runtime.start_client(game_dir=game, log_path=log, token=token,
            mcp_port=mcp_port, username="DistantGate", display=display, platform=args.platform)
        report["commands"].append(client.args)
        # Leave the 30-minute in-client cold-cache wait time to report its state.
        mcp = McpClient(f"http://127.0.0.1:{mcp_port}/mcp", token, 1830)
        mcp.initialize()
        runtime.wait_client_ready_for_connect(mcp, 120)
        if with_dh:
            scenario("configure", stage + "-configure")
        connect(stage, address)

    def scenario(suffix, label=None):
        scenario_id = "dh-protocol16" + ("-" + suffix if suffix else "")
        destination = artifact / (label or suffix or "handshake")
        result = mcp.call_tool("minecraft_run_scenario", {"id": scenario_id, "artifacts_dir": str(destination)})
        report["observations"][label or suffix or "handshake"] = result
        check(result.get("result") == "passed", f"{scenario_id}: {result}")
        observation = json.loads(result["observations"][0])
        if suffix == "disconnect":
            check(observation.get("disconnected") and observation.get("title_screen"),
                  f"DH disconnect did not complete the return-to-menu flow: {observation}")
        return observation

    def connect(stage, address=None):
        capture.stage = stage
        target = address if address is not None else proxy.address
        mcp.call_tool("minecraft_connect", {"server_addr": target})
        state = runtime.current_client_state(mcp, 120)
        deadline = __import__("time").monotonic() + 120
        allowed_fixture_prompt = False
        while not state.get("in_play"):
            screen = state.get("screen", {})
            if (screen.get("class") == "net.minecraft.client.gui.screens.ConfirmScreen"
                    and screen.get("title") == f"Allow Solaris content from {target}?"
                    and not allowed_fixture_prompt):
                # Main explicitly authorized these two permissions for this
                # isolated loopback fixture/private game copy on 2026-10-22.
                screenshot(stage + "-loader-permission-prompt")
                mcp.call_tool("minecraft_click_confirmation_button", {
                    "expected_title": screen["title"], "button_label": "Allow"})
                report.setdefault("authorized_fixture_permissions", []).append({
                    "server": target, "permissions": ["present_views", "send_view_actions"],
                    "decision": "Allow", "scope": "isolated loopback fixture/private game copy"})
                allowed_fixture_prompt = True
                state = runtime.next_client_state(mcp, state, deadline)
                continue
            if screen.get("class") in (
                    "net.minecraft.client.gui.screens.ConfirmScreen",
                    "net.minecraft.client.gui.screens.DisconnectedScreen"):
                report["failed_connect_observation"] = state
                screenshot(stage + "-connection-refusal")
                raise RuntimeError(f"Java connect stopped at {screen}; no confirmation was accepted")
            state = runtime.next_client_state(mcp, state, deadline)
        # Play can precede resource-pack reload/its Mojang splash disappearing.
        # Wait on canonical client-state producer events, never a timed sleep.
        while state.get("overlay", {}).get("open"):
            state = runtime.next_client_state(mcp, state, deadline)
            check(state.get("in_play"), f"left Play while awaiting resource reload: {state}")
        report.setdefault("connected_world_observations", {})[stage] = state

    def command(text):
        report.setdefault("fixture_commands", []).append(text)
        mcp.call_tool("minecraft_send_chat", {"message": text, "command": True})

    def set_clock(value, minimum, label=None):
        # Freeze the clock so a material check cannot drift into night lighting.
        command("gamerule advance_time false")
        command("time set " + value)
        state = runtime.current_client_state(mcp, 120)
        deadline = __import__("time").monotonic() + 120
        while not minimum <= state["time"]["clock_time"] % 24000 < minimum + 2000:
            state = runtime.next_client_state(mcp, state, deadline)
        report.setdefault("clock_observations", {})[label or value] = state["time"]

    def camera():
        command("gamemode spectator")
        command("tp @s 0 200 0 -90 4.5")
        state = runtime.current_client_state(mcp, 120)
        deadline = __import__("time").monotonic() + 120
        while (any(abs(state["player"][axis] - target) > 0.5
                   for axis, target in (("x", 0), ("y", 200), ("z", 0), ("pitch", 4.5)))
               or abs((state["player"]["yaw"] + 270) % 360 - 180) > 0.5):
            state = runtime.next_client_state(mcp, state, deadline)
        report.setdefault("camera_observations", []).append(state)

    def screenshot(name):
        path = artifact / "screenshots" / (name + ".png")
        result = mcp.call_tool("minecraft_screenshot", {"path": str(path)})
        check(path.is_file() and path.stat().st_size > 1024, f"missing actual screenshot: {result}")
        entry = {"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        if report["screenshots"] and report["screenshots"][-1]["path"] == str(path):
            report["screenshots"][-1] = entry
        else:
            report["screenshots"].append(entry)
        return path

    def await_landmark_pixels(name, material):
        state = runtime.current_client_state(mcp, 120)
        deadline = __import__("time").monotonic() + 1200
        while True:
            observed = landmark_pixels(screenshot(name), material)
            report.setdefault("visual_predicates", {})[name] = observed
            if observed["passed"]:
                return
            # A received leaf or an unrelated replacement VBO can precede the
            # parent aggregation/render upload. Wait on real client events and
            # inspect the actual frame; never inject cache data or priority work.
            state = runtime.next_client_state(mcp, state, deadline)

    try:
        check(template.is_dir(), f"missing private copied Prism template: {template}")
        xvfb, display, display_log = runtime.start_xvfb(artifact)
        start_server(False, "world")
        # A separate disposable no-DH preparer may visit the edit. Neither the
        # clean control nor the clean DH test client ever does; the preparer
        # connects directly and is outside their passive proxy packet evidence.
        launch("fixture-preparation", with_dh=False, address=f"127.0.0.1:{port}")
        for text in ("gamemode spectator", "time set day", "weather clear",
                     "forceload add 1024 0 1055 31", "tp @s 1040 220 8"):
            command(text)
        for x in (1024, 1055):
            for z in (0, 31):
                mcp.call_tool("minecraft_wait_for_loaded_block",
                              {"x": x, "y": 80, "z": z, "timeout_seconds": 120})
        for text in ("fill 1024 80 0 1031 200 7 minecraft:gold_block",
                     "fill 1024 80 8 1055 87 15 minecraft:redstone_block",
                     "fill 1024 72 16 1031 76 23 minecraft:water"):
            command(text)
        prepared = []
        for x, y, z, block in ((1028, 100, 4, "minecraft:gold_block"),
                              (1040, 84, 12, "minecraft:redstone_block"),
                              (1028, 74, 20, "minecraft:water")):
            prepared.append(mcp.call_tool("minecraft_wait_for_block_state", {
                "x": x, "y": y, "z": z, "block_id": block, "timeout_seconds": 120}))
        report["fixture_preparation"] = {
            "client": "separate disposable no-DH game/process, direct server connection",
            "verified_native_edits": prepared}
        camera()
        mcp.call_tool("minecraft_wait_for_loaded_block",
                      {"x": 0, "y": 200, "z": 0, "timeout_seconds": 120})
        mcp.call_tool("minecraft_disconnect")
        stop_client()
        proxy = LightWireProxy(port, artifact, capture.packet, capture.client_packet)
        print(f"DH scenario: {args.platform} control", flush=True)
        launch("control")
        camera()
        scenario("control")
        set_clock("day", 1000, "control-day")
        control_image = screenshot("control-no-server-dh")
        control_pixels = landmark_pixels(control_image, "gold")
        report.setdefault("visual_predicates", {})["control-no-server-dh"] = control_pixels
        check(not control_pixels["passed"], "gold landmark is already visible in the clean DH-off control")
        scenario("disconnect", "control-disconnect")
        stop_client()
        stop_server()
        start_server(True, "world")
        print(f"DH scenario: {args.platform} enabled, independently clean cache", flush=True)
        launch("enabled")
        camera()
        initial = scenario("")
        scenario("debug", "enabled-before-arrival-debug")
        screenshot("enabled-before-arrival-f3")
        scenario("debug", "enabled-before-arrival-debug-off")
        scenario("landmark")
        scenario("render", "enabled-render")
        set_clock("day", 1000, "enabled-day")
        await_landmark_pixels("enabled-never-visited-landmark", "gold")
        scenario("debug")
        screenshot("enabled-f3")
        scenario("debug", "debug-off")
        scenario("disconnect", "enabled-disconnect")
        runtime.wait_client_ready_for_connect(mcp, 120)
        connect("warm-reconnect")
        camera()
        warm = scenario("", "warm-handshake")
        check(warm["levels"] == initial["levels"], "unchanged world changed its DH level identity")
        scenario("landmark", "warm-landmark")
        set_clock("day", 1000, "edit-day")
        warm_render = scenario("render", "warm-render")
        edit_artifacts = artifact / "edited-render"
        edit_artifacts.mkdir()
        (edit_artifacts / "previous-render.json").write_text(
            json.dumps(warm_render["visible_uploaded_landmark_buffers"]) + "\n")
        capture.stage = "live-edit"
        # Change geometry as well as material: upstream DH's parent merge can
        # miss material-only swaps when compacted datapoint ids stay identical.
        command("fill 1024 80 0 1031 232 7 minecraft:emerald_block")
        scenario("edit")
        scenario("render-update", "edited-render")
        await_landmark_pixels("edited-never-visited-landmark", "emerald")
        for dimension in ("minecraft:the_nether", "minecraft:overworld"):
            capture.stage = dimension
            command(f"execute in {dimension} run tp @s 0 200 0")
            state = runtime.current_client_state(mcp, 120)
            deadline = __import__("time").monotonic() + 120
            while state.get("dimension") != dimension:
                state = runtime.next_client_state(mcp, state, deadline)
            data = scenario("state", dimension.split(":")[1])
            check(data["dimension"] == dimension, "DH observation crossed dimensions")
            if dimension.endswith("the_nether"):
                check(not data["landmark_gold"] and not data["landmark_emerald"], "overworld landmark leaked into nether")
        camera()
        scenario("edit", "return-overworld-edit")
        scenario("render", "return-overworld-render")
        set_clock("midnight", 18000)
        screenshot("night-water-lighting")
        set_clock("day", 1000)
        await_landmark_pixels("day-water-lighting", "emerald")
        scenario("disconnect", "before-reset-disconnect")
        stop_server()
        start_server(True, "reset-world")
        runtime.wait_client_ready_for_connect(mcp, 120)
        connect("world-reset-warm-client-cache")
        camera()
        reset = scenario("", "reset-handshake")
        check(reset["levels"] != initial["levels"], "world reset reused the old DH identity")
        check(not reset["landmark_gold"] and not reset["landmark_emerald"], "old landmark leaked into reset world")
        screenshot("reset-world-no-old-landmark")
        scenario("disconnect", "reset-disconnect")
        stop_client()
        launch("no-dh", with_dh=False)
        camera()
        screenshot("vanilla-no-dh-join")
        mcp.call_tool("minecraft_disconnect")
        check(not capture.landmark_vanilla_sent, "far landmark was sent as a vanilla chunk")
        check(capture.max_vanilla_radius <= 4, f"vanilla stream exceeded configured radius: {capture.max_vanilla_radius}")
        check(any(section["section"] == [6, 16, 0] for section in capture.sections), "no wire DH DTO for landmark section")
        check(any(section["kind"] == "update" and section["section"] == [6, 16, 0]
                  and section["stage"] == "live-edit" for section in capture.sections),
              "no real-time DH update for the edited landmark on the wire")
        check(not any(any(word in channel for word in ("seed", "worldgen", "terrain_keys", "density"))
                      for channel in capture.channels), "seed/program payload channel emitted")
        check(not proxy.errors, f"wire decoding failed: {proxy.errors}")
        report["passed"] = True
        return 0
    except Exception as error:
        report["error"] = f"{type(error).__name__}: {error}"
        print(report["error"], flush=True)
        return 1
    finally:
        try:
            stop_client()
        except Exception as error:
            report["cleanup_error"] = f"{type(error).__name__}: {error}"
            report["passed"] = False
        stop_server()
        if proxy:
            proxy.close()
        runtime.stop_process(xvfb)
        if display_log:
            display_log.close()
        (artifact / "dh-packet-evidence.json").write_text(json.dumps(capture.report(), indent=2) + "\n")
        (artifact / "dh-scenario.json").write_text(json.dumps(report, indent=2) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=("fabric", "neoforge"), required=True)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--artifact-dir", type=Path, required=True)
    parser.add_argument("--server-bin", type=Path, required=True)
    parser.add_argument("--dh-jar", type=Path, required=True)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
