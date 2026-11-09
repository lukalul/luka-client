"""Builds what goes online for Luka.exe's Play button.

  release/online/client.luka.wtf/    upload to https://client.luka.wtf/ (the client mods, update.json, default settings, the launcher jar)
  release/online/github/      attach to the GitHub release (the two Prism Launcher imports)
  release/online/DOWNLOADS.md the full list of what a player downloads and from where

Usage: python release-work/build_manifest.py
       add --local http://127.0.0.1:8099 to point every file at a local test server instead.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "release", "online")
HCF = os.path.join(os.environ["APPDATA"], "PrismLauncher", "instances", "Ornithe-1.8.9-HCF", "minecraft")
CLOSED = ["cbui", "envoy", "bakeplus", "motionblur", "subframe", "fullbright", "fastpacks", "hcfopt"]
NORMAL = ("argentum-", "argentum-extras-", "cera-", "legacy-lwjgl3-")
VULKAN_ONLY = ("fabric-language-kotlin-",)
VERSION = "1.0.0"
SIGNING_KEY = os.environ.get("LUKA_SIGNING_KEY", os.path.join(os.path.expanduser("~"), ".luka-signing", "update-signing.key"))


def sha512(path):
    h = hashlib.sha512()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


PRISM_RAM_MB = 3072


def write_prism_instance(target, title, renderer, manifest_url):
    """A Prism Launcher instance. It holds no third-party mods: PrismSync fetches them before each launch, from the signed list."""
    launcher = os.path.join(ROOT, "luka-launcher")
    with open(os.path.join(launcher, "res", "jvmargs.txt"), encoding="utf-8") as f:
        jvm = " ".join(line.strip() for line in f if line.strip())
    jvm += " -Dfabric.addMods=../luka/renderers/" + renderer
    run = '\\"$INST_JAVA\\" -cp \\"$INST_DIR/luka/luka-launcher.jar\\" dev.luka.launcher.PrismSync \\"$INST_DIR\\"'
    cfg = "\n".join([
        "[General]", "ConfigVersion=1.3", "InstanceType=OneSix", "name=" + title, "iconKey=luka-client",
        "AutomaticJava=true", "OverrideMemory=true", "MinMemAlloc=%d" % PRISM_RAM_MB, "MaxMemAlloc=%d" % PRISM_RAM_MB,
        "OverrideJavaArgs=true", 'JvmArgs="%s"' % jvm, "OverrideCommands=true", 'PreLaunchCommand="%s"' % run,
        "OverrideModDownloadLoaders=true", 'ModDownloadLoaders="[\\"ornithe\\", \\"legacy-fabric\\", \\"babric\\"]"', ""])
    settings = "renderer=%s\n" % renderer
    if not manifest_url.startswith("https://client.luka.wtf/"):
        settings += "updateUrl=%s\n" % manifest_url
    note = "luka downloads its mods here each time you press Launch in Prism, so it stays up to date by itself.\n"
    template = os.path.join(ROOT, "release-work", "prism-template")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("instance.cfg", cfg)
        z.write(os.path.join(template, "mmc-pack.json"), "mmc-pack.json")
        for fn in os.listdir(os.path.join(template, "patches")):
            z.write(os.path.join(template, "patches", fn), "patches/" + fn)
        z.write(os.path.join(launcher, "res", "logo.png"), "luka-client.png")
        z.write(os.path.join(launcher, "dist", "luka-launcher.jar"), "luka/luka-launcher.jar")
        z.writestr("luka/launcher.properties", settings)
        z.writestr(".minecraft/luka-readme.txt", note)
        for fn in ("LICENSE", "NOTICE"):
            z.write(os.path.join(ROOT, "release-work", fn), "luka/" + fn)


def read_changelog():
    """release-work/CHANGELOG.md, newest first: '## 1.0.0' headings with '- ' notes."""
    releases = []
    for line in open(os.path.join(ROOT, "release-work", "CHANGELOG.md"), encoding="utf-8"):
        line = line.strip()
        if line.startswith("## "):
            releases.append({"version": line[3:].strip(), "notes": []})
        elif line.startswith("- ") and releases:
            releases[-1]["notes"].append(line[2:].strip())
    if not releases or not releases[0]["notes"]:
        raise SystemExit("CHANGELOG.md has no notes for the newest release")
    return releases[:5]


def write_signed(manifest, target):
    """The launcher refuses an update.json that this private key did not sign."""
    if not os.path.isfile(SIGNING_KEY):
        raise SystemExit("No update signing key at " + SIGNING_KEY)
    body = target + ".body"
    with open(body, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, indent=1)
    java = os.path.join(os.environ["JAVA_HOME"], "bin", "java.exe") if os.environ.get("JAVA_HOME") else "java"
    subprocess.run([java, os.path.join(ROOT, "release-work", "SignManifest.java"), "sign", SIGNING_KEY, body, target], check=True)
    os.remove(body)


def size_mb(path):
    return os.path.getsize(path) / 1048576


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--local", default="")
    args.add_argument("--unsigned", action="store_true", help="write update-body.json to sign later on the maintainer's PC")
    args.add_argument("--ms-client-id", default="", help="the Azure app ID for Microsoft sign-in; Mojang has to approve it first")
    a = args.parse_args()
    site = a.local + "/client.luka.wtf" if a.local else "https://client.luka.wtf"
    shutil.rmtree(OUT, ignore_errors=True)
    luka_dir = os.path.join(OUT, "client.luka.wtf")
    git_dir = os.path.join(OUT, "github")
    os.makedirs(os.path.join(luka_dir, "mods"))
    os.makedirs(git_dir)
    report = []

    mods = []
    for m in CLOSED:
        src = os.path.join(ROOT, m + "-ornithe", "build", "libs", m + "-ornithe-0.1.0.jar")
        name = os.path.basename(src)
        shutil.copy(src, os.path.join(luka_dir, "mods", name))
        mods.append({"file": name, "url": site + "/mods/" + name, "sha512": sha512(src)})
        report.append(("client.luka.wtf (open source, GPL-3.0)", name, size_mb(src), "both"))

    deps = json.load(open(os.path.join(ROOT, "release-work", "dependencies.json"), encoding="utf-8"))
    dependencies, normal, vulkan = [], [], []
    for d in deps:
        entry = {"file": d["file"], "url": d["url"], "sha512": d["sha512"]}
        local = os.path.join(HCF, "mods", d["file"])
        store = os.path.join(HCF, "mods-renderer", d["file"])
        path = local if os.path.isfile(local) else store
        mb = size_mb(path) if os.path.isfile(path) else 0
        if d["file"].startswith(NORMAL):
            normal.append(entry)
            report.append(("Modrinth", d["file"], mb, "normal"))
        elif d["file"].startswith(VULKAN_ONLY):
            vulkan.append(entry)
            report.append(("Modrinth", d["file"], mb, "vulkan"))
        else:
            dependencies.append(entry)
            report.append(("Modrinth", d["file"], mb, "both"))

    # SoundFix is LGPL and has no public 1.8.9 build to point at, so it is hosted with luka's own mods
    soundfix = os.path.join(ROOT, "release-work", "bundled", "soundfix-1.2.0+mc1.8.9.jar")
    if os.path.isfile(soundfix):
        name = os.path.basename(soundfix)
        asset = name.replace("+", ".")
        shutil.copy(soundfix, os.path.join(luka_dir, "mods", asset))
        dependencies.append({"file": name, "url": site + "/mods/" + asset, "sha512": sha512(soundfix)})
        report.append(("client.luka.wtf (open source)", name, size_mb(soundfix), "both"))

    for src in (os.path.join(ROOT, "luka-vulkan", "build", "libs", "astatine-0.1.0+mc1.8.9.jar"),
                os.path.join(ROOT, "customsky-ornithe", "build", "libs", "customsky-ornithe-0.1.0.jar"),
                os.path.join(ROOT, "customcolors-ornithe", "build", "libs", "customcolors-ornithe-0.1.0.jar")):
        name = os.path.basename(src)
        asset = name.replace("+", ".")
        shutil.copy(src, os.path.join(luka_dir, "mods", asset))
        vulkan.append({"file": name, "url": site + "/mods/" + asset, "sha512": sha512(src)})
        report.append(("client.luka.wtf (open source)", name, size_mb(src), "vulkan"))

    jar = os.path.join(ROOT, "luka-launcher", "dist", "luka-launcher.jar")
    shutil.copy(jar, luka_dir)
    defaults = os.path.join(luka_dir, "default-config.zip")
    source = os.path.join(ROOT, "release-work", "default-config")
    with zipfile.ZipFile(defaults, "w", zipfile.ZIP_DEFLATED) as z:
        for folder, _, files in os.walk(source):
            for fn in files:
                full = os.path.join(folder, fn)
                z.write(full, os.path.relpath(full, source).replace(os.sep, "/"))

    manifest = {
        **({"msClientId": a.ms_client_id} if a.ms_client_id else {}),
        "sequence": int(time.time()),
        "changelog": read_changelog(),
        "launcher": {"version": VERSION, "url": site + "/luka-launcher.jar", "sha512": sha512(jar)},
        "defaults": {"url": site + "/default-config.zip", "sha512": sha512(defaults)},
        "mods": mods,
        "dependencies": dependencies,
        "renderers": {"normal": normal, "vulkan": vulkan},
    }
    if a.unsigned:
        with open(os.path.join(OUT, "update-body.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(manifest, f, indent=1)
    else:
        write_signed(manifest, os.path.join(luka_dir, "update.json"))

    # the second way in: import one of these into Prism Launcher instead of installing the luka launcher
    for zip_name, title, renderer in (("luka-1.8.9-prism.zip", "luka", "normal"), ("luka-1.8.9-vulkan-prism.zip", "luka Vulkan", "vulkan")):
        target = os.path.join(git_dir, zip_name)
        write_prism_instance(target, title, renderer, site + "/update.json")
        report.append(("GitHub (Prism Launcher import, not downloaded by Play)", zip_name, size_mb(target), "-"))

    with open(os.path.join(OUT, "DOWNLOADS.md"), "w", encoding="utf-8") as f:
        f.write("# What Play downloads\n\n| From | File | MB | Used by |\n|---|---|---:|---|\n")
        for where, name, mb, used in sorted(report):
            f.write("| %s | %s | %.1f | %s |\n" % (where, name, mb, used))
        f.write("\nAlso, on first Play: Minecraft 1.8.9 client, libraries and assets from Mojang; Fabric/Ornithe libraries from their Maven "
                "repositories; Java 25 from Adoptium, or Amazon Corretto if Adoptium is unreachable.\n")
    print("wrote", OUT, "mods", len(mods), "deps", len(dependencies), "normal", len(normal), "vulkan", len(vulkan))


main()
