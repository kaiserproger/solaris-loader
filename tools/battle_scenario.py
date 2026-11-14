#!/usr/bin/env python3
"""MC 26.1.2 Loader battle gate over the core harness lifecycle (private Xvfb)."""
from __future__ import annotations

import argparse
import functools
import hashlib
import http.server
import json
import math
from pathlib import Path
import secrets
import struct
import subprocess
import sys
import threading
import time
import zipfile


def battle_model_errors(paths):
    """Fail the functional gate on rejected battle models or pack metadata."""
    return {str(path): [line for line in path.read_text(errors="replace").splitlines()
                       if "Error reading pack metadata" in line or
                       ("solaris_battle:" in line and
                        ("Failed to load model" in line or "Missing block model:" in line))]
            for path in paths}


def save_enabled_server(game: Path, address: str) -> None:
    """Vanilla per-server Resource Packs=Enabled; never stages a resource-pack folder."""
    def utf(value):
        encoded = value.encode("utf-8")
        return struct.pack(">H", len(encoded)) + encoded
    entry = (b"\x08" + utf("name") + utf("Java battle gate")
             + b"\x08" + utf("ip") + utf(address)
             + b"\x01" + utf("acceptTextures") + b"\x01\x00")
    (game / "servers.dat").write_bytes(b"\x0a\x00\x00\x09" + utf("servers")
                                      + b"\x0a\x00\x00\x00\x01" + entry + b"\x00")


def run(args):
    repo, artifact = args.repo_root.resolve(), args.artifact_dir.resolve()
    artifact.mkdir(parents=True, exist_ok=True)
    sys.path.insert(0, str(repo))
    from tools.harness import runtime
    from tools.harness.mcp import McpClient
    from tools.harness.backends.light_wire import LightWireProxy, Reader

    report = {"functional_passed": False, "minecraft": "26.1.2", "platform": args.platform,
              "agent_run": True, "count_per_side": 1500, "checks": {}, "screenshots": [],
              "commands": [], "observations": {},
              "visual_review": {"status": "pending", "required": [
                  "3D weapons", "3D shields", "horse and rider", "raised shield pose"]}}
    clients, server, xvfb, httpd, proxy = [], None, None, None, None
    logs = []
    battle = artifact / "battle"

    def record(name, value):
        report["observations"][name] = value
        (artifact / "java-battle.json").write_text(json.dumps(report, indent=2) + "\n")
        return value

    def check(condition, message):
        if not condition:
            raise RuntimeError(message)

    def shot(client, name, wait_overlay=True):
        if wait_overlay:
            state_until(client, lambda s: not s.get("overlay", {}).get("open"), name + "-capture-ready")
        path = artifact / "screenshots" / (name + ".png")
        result = client.call_tool("minecraft_screenshot", {"path": str(path)})
        check(path.is_file() and path.stat().st_size > 1024, f"screenshot missing: {result}")
        report["screenshots"].append(str(path))
        return path

    def state_until(client, predicate, label, seconds=120):
        state = runtime.current_client_state(client, seconds)
        deadline = time.monotonic() + seconds
        while not predicate(state):
            state = runtime.next_client_state(client, state, deadline)
        return record(label, state)

    def server_line(phrase):
        path = battle / "server.log"
        def condition():
            if server.poll() is not None:
                raise RuntimeError(f"battle runner exited {server.returncode}; see battle-runner.log")
            text = path.read_text(errors="replace") if path.exists() else ""
            matches = [line for line in text.splitlines() if phrase in line]
            return bool(matches), matches[-1] if matches else None
        return runtime.wait_for_path_condition(path, 240, condition)

    def console(command):
        report.setdefault("console", []).append(command)
        with (battle / "console.fifo").open("w") as sink:
            sink.write(command + "\n")

    def kit(client):
        return [client.call_tool("minecraft_read_item_components", {"source": "player", "slot": slot})
                for slot in range(41)]

    def kit_identity(stacks):
        # Ordinary wear is not a kit change; all identities, models, dyes and custody still match.
        return [{**stack, "components": {key: value for key, value in stack.get("components", {}).items()
                                        if key != "minecraft:damage"}} for stack in stacks]

    def face_target(client, target, label, height):
        # Recenter after chunk/renderer settling: cavalry may have moved meanwhile.
        camera = runtime.current_client_state(client, 30)["player"]
        dx, dz = target["x"] - camera["x"], target["z"] - camera["z"]
        yaw = math.degrees(math.atan2(-dx, dz))
        pitch = math.degrees(math.atan2(camera["y"] + 1.62 - target["y"] - height, math.hypot(dx, dz)))
        console(f'tp CamSpec {camera["x"]} {camera["y"]} {camera["z"]} {yaw} {pitch}')
        state_until(client, lambda s: abs((s["player"]["yaw"] - yaw + 180) % 360 - 180) < 1
                    and abs(s["player"]["pitch"] - pitch) < 1, label)
        client.call_tool("minecraft_wait_ticks", {"ticks": 2})

    try:
        pack = artifact / "battle.zip"
        with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(args.pack_root.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(args.pack_root))
        sha1 = hashlib.sha1(pack.read_bytes()).hexdigest()
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(artifact))
        httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        threading.Thread(target=httpd.serve_forever, daemon=True).start()
        url = f"http://127.0.0.1:{httpd.server_port}/battle.zip"
        report["resource_pack"] = {"url": url, "sha1": sha1, "source": str(args.pack_root),
                                   "required": False, "client_preference": args.pack_preference,
                                   "policy": "vanilla server push, clean client, no local resourcepacks"}
        port, dashboard = runtime.reserve_port(), runtime.reserve_port()
        command = [sys.executable, str(repo / "tools/battle/run-battle.py"), "--take",
                   "--pin", str(args.pin), "--client", str(args.recorder), "--client-data", str(args.recorder_data),
                   "--cache", str(repo / "data/vanilla"), "--world", str(args.world), "--world-seed", "935",
                   "--seed", str(args.seed), "--scenario", "field-10k", "--count", "1500",
                   "--view-distance", "18", "--run-dir", str(battle), "--duration", str(args.duration),
                   "--port", str(port), "--dashboard-port", str(dashboard), "--console-fifo",
                   "--live-player", "PovRed:red", "--live-spectator", "CamSpec", "--live-timeout", "3600",
                   "--resource-pack-url", url, "--resource-pack-sha1", sha1]
        report["commands"].append(command)
        log = (artifact / "battle-runner.log").open("wb")
        logs.append(log)
        server = subprocess.Popen(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        runtime.wait_for_path_condition(battle, 240, lambda: (battle.is_dir(), None))
        server_line("Solaris is listening")
        xvfb, display, log = runtime.start_xvfb(artifact)
        logs.append(log)
        pack_statuses = []
        def client_packet(wire, phase, packet, payload):
            if phase == "configuration" and packet == 6:
                pack_statuses.append({"connection": id(wire), "pack_id": payload[:16].hex(),
                                      "action": Reader(payload, 16).varint()})
        proxy = LightWireProxy(port, artifact, client_packet_observer=client_packet, decode_light=False)
        for name in ("PovRed", "CamSpec"):
            game = artifact / name / "game"
            game.mkdir(parents=True)
            if args.pack_preference == "enabled":
                save_enabled_server(game, proxy.address)
            (game / "options.txt").write_text("version:4790\nonboardAccessibility:false\nguiScale:2\nrenderDistance:10\nsimulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\nfullscreen:false\ntutorialStep:none\n")
            token, mcp_port = secrets.token_urlsafe(32), runtime.reserve_port()
            process, log = runtime.start_client(game_dir=game, log_path=artifact / f"{name}.log", token=token,
                mcp_port=mcp_port, username=name, display=display, platform=args.platform, client_profile="loader")
            clients.append((process, None))
            logs.append(log)
            report["commands"].append(process.args)
            client = McpClient(f"http://127.0.0.1:{mcp_port}/mcp", token, 180)
            clients[-1] = (process, client)
            client.initialize()
            runtime.wait_client_ready_for_connect(client, 120)
            client.call_tool("minecraft_connect", {"server_addr": proxy.address})
            state = runtime.current_client_state(client, 120)
            deadline = time.monotonic() + 120
            saw_pack_prompt = False
            while not state.get("in_play"):
                screen = state.get("screen", {})
                if screen.get("title") in {
                        f"Allow Solaris content from {host}:{proxy.address.rsplit(':', 1)[1]}?"
                        for host in ("127.0.0.1", "localhost")}:
                    shot(client, name + "-loader-permission")
                    record(name + "-loader-permission", state)
                    client.call_tool("minecraft_click_confirmation_button",
                                     {"expected_title": screen["title"], "button_label": "Allow"})
                    state = runtime.next_client_state(client, state, deadline)
                    continue
                if screen.get("class", "").endswith("ConfirmScreen"):
                    record(name + "-first-connection-prompt", state)
                    shot(client, name + "-resource-pack-prompt", wait_overlay=False)
                    if args.pack_preference == "prompt" and screen.get("title") == (
                            "This server recommends the use of a custom resource pack."):
                        saw_pack_prompt = True
                        client.call_tool("minecraft_click_confirmation_button",
                                         {"expected_title": screen["title"], "button_label": "No"})
                        state = runtime.next_client_state(client, state, deadline)
                        continue
                    raise RuntimeError(f"{name}: automatic pack acceptance stopped at {screen}")
                if screen.get("class", "").endswith("DisconnectedScreen"):
                    shot(client, name + "-disconnected")
                    raise RuntimeError(f"{name}: disconnected: {screen}")
                state = runtime.next_client_state(client, state, deadline)
            state_until(client, lambda s: s.get("in_play") and not s.get("overlay", {}).get("open"), name + "-joined")
            shot(client, name + "-joined")
            if args.pack_preference == "prompt":
                check(saw_pack_prompt, "default server preference did not show its optional-pack prompt")
                record("pack-statuses", pack_statuses)
                declined = any(row["action"] == 1 for row in pack_statuses)
                check(declined, "No did not send a Declined resource-pack response")
                report["case"] = "default-pack-policy-only"
                report["checks"] = {"default_prompt_observed": True, "optional_decline_stays_connected": True}
                report["visual_review"]["status"] = "not-applicable"
                report["functional_passed"] = True
                return 0
        pov, cam = [client for _, client in clients]
        server_line("Battle started tick=")
        placed = server_line("Battle live placed: PovRed")
        record("placed", placed)
        before = record("kit-before", kit(pov))
        placement = dict(token.split("=", 1) for token in placed.split() if "=" in token)
        expected_slots = int(placement["kit_slots"])
        received_slots = sum(item.get("count", 0) > 0 for item in before)
        check(expected_slots > 0 and received_slots == expected_slots,
              f"PovRed kit has {received_slots} occupied slots; server declared {expected_slots}")
        shot(pov, "PovRed-equipped")
        shot(cam, "CamSpec-spectator")
        report["checks"]["join_and_kit"] = True
        record("pack-statuses", pack_statuses)
        report["checks"]["resource_pack"] = sum(row["action"] == 0 for row in pack_statuses) == 2
        check(report["checks"]["resource_pack"], "both Java clients must report SuccessfullyLoaded")
        console("kill PovRed")
        state_until(pov, lambda s: s.get("player", {}).get("health", 20) <= 0, "death")
        shot(pov, "PovRed-death")
        pov.call_tool("minecraft_respawn", {"timeout_seconds": 60})
        server_line("Battle live respawned: PovRed")
        state_until(pov, lambda s: s.get("in_play") and s.get("player", {}).get("health", 0) > 0, "respawn")
        after = record("kit-after", kit(pov))
        check(kit_identity(before) == kit_identity(after), "battle kit changed across respawn")
        shot(pov, "PovRed-respawn-kit")
        report["checks"]["respawn_same_kit"] = True
        entities = record("entities", pov.call_tool("minecraft_list_entities", {"radius": 128, "limit": 512}))["entities"]
        check(any(e.get("main_hand", {}).get("item_model", "").startswith("solaris_battle:") for e in entities),
              "received battle equipment observation missing (no visual claim from default stacks)")
        # Keep a received target when present. At a spear-rank spawn the nearest
        # 512 can instead hide the rear halberd rank, so move the observer there.
        weapon_entities = entities
        halberds = [e for e in weapon_entities if e.get("main_hand", {}).get("item_model") == "solaris_battle:iron_halberd"
                    and e.get("entity_type") != "minecraft:player"]
        if not halberds:
            player = runtime.current_client_state(pov, 30)["player"]
            rear_x, rear_z = player["x"] + 32, player["z"] + 24
            console(f'tp CamSpec {rear_x} {player["y"] + 10} {rear_z} 45 25')
            state = state_until(cam, lambda s: abs(s.get("player", {}).get("x", 0) - rear_x) < 1, "rear-rank-camera")
            deadline = time.monotonic() + 30
            while not halberds:
                weapon_entities = cam.call_tool("minecraft_list_entities", {"radius": 64, "limit": 512})["entities"]
                halberds = [e for e in weapon_entities if e.get("main_hand", {}).get("item_model") == "solaris_battle:iron_halberd"
                            and e.get("entity_type") != "minecraft:player"]
                if not halberds:
                    state = runtime.next_client_state(cam, state, deadline)
        record("halberd-camera-entities", weapon_entities)
        halberd = record("halberd-camera-target", min(halberds, key=lambda g: sum(
            (e["x"] - g["x"]) ** 2 + (e["z"] - g["z"]) ** 2 < 4 for e in weapon_entities)))
        console(f'tp CamSpec {halberd["x"] + 4} {halberd["y"] + 2} {halberd["z"] - 4} 45 25')
        state_until(cam, lambda s: abs(s.get("player", {}).get("x", 0) - (halberd["x"] + 4)) < 1
                    and s["player"].get("level_has_chunk"), "halberd-camera")
        cam.call_tool("minecraft_wait_ticks", {"ticks": 20})
        shot(cam, "models-halberd")
        horse = record("horse", pov.call_tool("minecraft_wait_for_visible_entity",
                       {"entity_type": "minecraft:horse", "radius": 128, "timeout_seconds": 30}))
        console(f'tp CamSpec {horse["x"] - 5} {horse["y"] + 5} {horse["z"] - 5} -45 35')
        state_until(cam, lambda s: abs(s.get("player", {}).get("x", 0) - (horse["x"] - 5)) < 1
                    and s["player"].get("level_has_chunk"), "horse-camera")
        cam.call_tool("minecraft_wait_ticks", {"ticks": 20})
        nearby = record("horse-camera-entities", cam.call_tool("minecraft_list_entities", {"radius": 24, "limit": 256}))["entities"]
        horses = {e["entity_id"]: e for e in nearby if e["entity_type"] == "minecraft:horse"}
        riders = [e for e in nearby if e.get("vehicle_id") in horses]
        check(riders, "no received horse/rider vehicle link near the horse camera")
        rider = riders[0]
        mount = horses[rider["vehicle_id"]]
        record("horse-rider-pair", {"horse": mount, "rider": rider})
        face_target(cam, mount, "horse-final-frame", 1.8)
        shot(cam, "models-horses-riders")
        errors = record("model-load-errors", battle_model_errors([artifact / f"{name}.log" for name in ("PovRed", "CamSpec")]))
        report["checks"]["battle_models_loaded"] = not any(errors.values())
        check(report["checks"]["battle_models_loaded"], "Java rejected battle models; see model-load-errors")
        deadline = time.monotonic() + 90
        attacks = []
        unreachable, approaches = set(), []
        while len(attacks) < 8:
            check(time.monotonic() < deadline, "no blue target reached within the live combat deadline")
            entities = pov.call_tool("minecraft_list_entities", {"radius": 16, "limit": 512})["entities"]
            blue = [e for e in entities if e.get("off_hand", {}).get("item_model") == "solaris_battle:blue_heater_shield"
                    and e.get("health", 0) > 0 and e.get("vehicle_id") is None
                    and e["entity_id"] not in unreachable]
            if not blue:
                state = runtime.current_client_state(pov, 90)
                runtime.next_client_state(pov, state, deadline)
                continue
            target = blue[0]
            if target["distance"] > 4:
                approached = pov.call_tool("minecraft_approach_entity", {"entity_id": target["entity_id"], "timeout_seconds": 15})
                approaches.append(approached)
                record("approaches", approaches)
                if not approached["in_reach"]:
                    # Match the existing survival scenario: cliffs are not an arrival.
                    unreachable.add(target["entity_id"])
                    continue
            attack = {key: target[key] for key in ("entity_id", "entity_uuid", "entity_type")}
            attack["timeout_seconds"] = 3
            try:
                attacks.append(pov.call_tool("minecraft_attack_entity_once", attack))
            except RuntimeError as error:
                # At most eight distinct attempts; moving/blocking enemies can deny a hit.
                # Retain that outcome, but no other MCP failure is acceptable.
                if "attack was not confirmed by entity damage or removal" not in str(error):
                    raise
                attacks.append({"confirmed": False, "target": target, "error": str(error)})
            record("attacks", attacks)
            pov.call_tool("minecraft_wait_ticks", {"ticks": 20})
            if attacks[-1].get("health_after", 20) < attacks[-1].get("health_before", 0):
                break
        record("attacks", attacks)
        shot(pov, "PovRed-hits")
        report["checks"]["target_health_falls"] = any(a.get("health_after", 20) < a.get("health_before", 0) for a in attacks)
        check(report["checks"]["target_health_falls"], "no received blue target health decrease")
        console("battle status")
        status = record("battle-status-after-hits", server_line("Battle running tick="))
        fields = dict(token.split("=", 1) for token in status.split() if "=" in token)
        report["checks"]["side_losses_grow"] = int(fields["side2"]) < 1500
        check(report["checks"]["side_losses_grow"], "blue side losses did not grow")
        entities = pov.call_tool("minecraft_list_entities", {"radius": 64, "limit": 512})["entities"]
        blocking = record("blocking-entities", [e for e in entities if e.get("blocking") and e.get("vehicle_id") is None])
        check(blocking, "no received raised shield state to capture")
        views = []
        for guard in blocking:
            angle = math.radians(guard["yaw"])
            x = guard["x"] - math.sin(angle) * 3 - math.cos(angle)
            z = guard["z"] + math.cos(angle) * 3 - math.sin(angle)
            clearance = min(((e["x"] - x) ** 2 + (e["z"] - z) ** 2 for e in entities
                             if e["entity_id"] != guard["entity_id"]), default=float("inf"))
            density = sum((e["x"] - guard["x"]) ** 2 + (e["z"] - guard["z"]) ** 2 < 16 for e in entities)
            views.append((density, -clearance, guard, x, z))
        _, _, guard, camera_x, camera_z = min(views, key=lambda view: view[:2])
        camera_yaw = math.degrees(math.atan2(camera_x - guard["x"], guard["z"] - camera_z))
        record("shield-camera-target", guard)
        console(f'tp CamSpec {camera_x} {guard["y"] + 3} {camera_z} {camera_yaw} 45')
        state_until(cam, lambda s: abs(s.get("player", {}).get("x", 0) - camera_x) < 1
                    and s["player"].get("level_has_chunk"), "shield-camera")
        cam.call_tool("minecraft_wait_ticks", {"ticks": 20})
        state = runtime.current_client_state(cam, 30)
        deadline = time.monotonic() + 15
        while True:
            nearby = cam.call_tool("minecraft_list_entities", {"radius": 16, "limit": 128})["entities"]
            observed_guard = next((e for e in nearby if e["entity_id"] == guard["entity_id"]), None)
            if observed_guard and observed_guard.get("blocking"):
                record("shield-capture-target", observed_guard)
                break
            state = runtime.next_client_state(cam, state, deadline)
        face_target(cam, observed_guard, "shield-final-frame", 1.1)
        shot(cam, "raised-shield-poses")
        rc = server.wait(timeout=args.duration + 240)
        check(rc == 0, f"battle runner failed: {rc}")
        counter = subprocess.run([sys.executable, str(args.hit_counter), str(battle)], capture_output=True, text=True, check=True)
        (artifact / "povred-hits.json").write_text(counter.stdout)
        hits = record("server-damage-events", json.loads(counter.stdout))
        report["checks"]["server_damage_events"] = hits.get("soldier_hits", 0) > 0
        report["functional_passed"] = all(report["checks"].values())
        return 0 if report["functional_passed"] else 1
    except Exception as error:
        report["error"] = f"{type(error).__name__}: {error}"
        for name, (_, client) in zip(("PovRed", "CamSpec"), clients):
            if client is not None:
                try:
                    record(name + "-failure-state", client.call_tool("minecraft_observe"))
                    path = artifact / "screenshots" / (name + "-failure.png")
                    client.call_tool("minecraft_screenshot", {"path": str(path)})
                    report["screenshots"].append(str(path))
                except Exception as capture_error:
                    report.setdefault("capture_errors", []).append(str(capture_error))
        return 1
    finally:
        for process, client in reversed(clients):
            if client:
                client.close()
            runtime.stop_process(process, interrupt=True)
        runtime.stop_process(server, interrupt=True)
        if proxy:
            proxy.close()
        runtime.stop_process(xvfb)
        if httpd:
            httpd.shutdown()
            httpd.server_close()
        for log in logs:
            log.close()
        (artifact / "java-battle.json").write_text(json.dumps(report, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--artifact-dir", type=Path, required=True)
    parser.add_argument("--platform", choices=("fabric", "neoforge"), required=True)
    parser.add_argument("--pin", type=Path, required=True)
    parser.add_argument("--recorder", type=Path, required=True)
    parser.add_argument("--recorder-data", type=Path, required=True)
    parser.add_argument("--world", type=Path, required=True)
    parser.add_argument("--pack-root", type=Path, required=True)
    parser.add_argument("--hit-counter", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--duration", type=int, default=150)
    parser.add_argument("--pack-preference", choices=("prompt", "enabled"), default="enabled")
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
