#!/usr/bin/env python3
"""Run the opt-in smoke plugin twice on an isolated, localhost-only Paper server."""

import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request


BUILDS = {
    49: "dd64988a011729e6812f2fff1be32ba5c572ecdc8890e6abc7a56aa91b53f77d",
    133: "c613f734e0d5713498b4368f720620256f53a2db623fd3bffb5a708a4db721dc",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-eula", action="store_true", help="Accept https://aka.ms/MinecraftEULA for this test server")
    parser.add_argument("--java", default="java", help="Java 25 executable")
    parser.add_argument("--build", type=int, choices=BUILDS, default=49)
    parser.add_argument("--server-jar", type=Path, help="Use a previously downloaded official JAR (checksum verified)")
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Pass --accept-eula only after accepting the Minecraft EULA.")

    root = Path(__file__).resolve().parents[1]
    jars = [root / "target" / name for name in ("AncientGates.jar", "AncientGates-paper-smoke.jar")]
    if not all(jar.is_file() for jar in jars):
        parser.error("Build both JARs first: mvn -Ppaper-smoke clean verify")
    server = root / ".scratch" / f"paper-smoke-{args.build}"
    marker = server / ".ancientgates-smoke-server"
    if server.exists() and not marker.is_file():
        parser.error(f"Refusing to use an existing unmarked directory: {server}")
    server.mkdir(parents=True, exist_ok=True)
    marker.touch()
    target = server / "paper.jar"
    expected = BUILDS[args.build]
    if args.server_jar:
        shutil.copyfile(args.server_jar, target)
    elif not target.exists():
        url = f"https://fill-data.papermc.io/v1/objects/{expected}/paper-26.3-{args.build}.jar"
        request = urllib.request.Request(url, headers={"User-Agent": "AncientGates-Smoke/1.0 (https://github.com/mrfloris/AncientGates)"})
        with urllib.request.urlopen(request, timeout=60) as response, target.open("wb") as output:
            shutil.copyfileobj(response, output)
    if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        parser.error("Paper server checksum mismatch; remove the cached test paper.jar and retry.")

    plugins = server / "plugins"
    (plugins / "bStats").mkdir(parents=True, exist_ok=True)
    (plugins / "AncientGates").mkdir(exist_ok=True)
    for jar in jars:
        shutil.copyfile(jar, plugins / jar.name)
    (plugins / "bStats" / "config.yml").write_text("enabled: false\n")
    # Exercise existing configurations with the upstream updater still enabled.
    (plugins / "AncientGates" / "conf.json").write_text('{"updateCheck":true,"autoUpdate":true}')
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25583\nonline-mode=true\n"
        "enable-rcon=false\nenable-query=false\nmax-players=1\n"
        "view-distance=2\nsimulation-distance=2\nspawn-protection=0\n"
        "level-type=minecraft:flat\ngenerate-structures=false\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"biome":"minecraft:plains"}\n'
    )
    for phase in ("first", "restart"):
        log = server / f"smoke-{phase}.log"
        command = [args.java, "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Xms512M", "-Xmx1536M"]
        if phase == "restart":
            command.append("-Dancientgates.smoke.restart=true")
        command += ["-jar", "paper.jar", "--nogui"]
        with log.open("w") as output:
            process = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)
            try:
                process.wait(timeout=180)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                try:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                    process.wait(timeout=30)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                raise SystemExit(f"Smoke test interrupted or timed out. See {log}")
            finally:
                process.stdin.close()
        content = log.read_text()
        result = re.search(r"ANCIENTGATES_SMOKE_RESULT assertions=(\d+) failures=(\d+)", content)
        if process.returncode != 0 or not result or result[2] != "0" or " ERROR]:" in content:
            raise SystemExit(f"Smoke test {phase} failed. See {log}")
        if "[AncientGates] Disabled" not in content:
            raise SystemExit(f"Clean plugin shutdown was not confirmed. See {log}")
        print(f"Paper 26.3 build {args.build}, {phase}: {result[1]} assertions passed; clean shutdown. {log}", flush=True)


if __name__ == "__main__":
    main()
