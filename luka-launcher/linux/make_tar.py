"""Packs the Linux launcher: dist/luka-launcher-linux.tar.gz with a luka/ folder holding the launcher jar, the boot
jar, luka.sh (executable), the menu entry template and the icon. Runs on Windows too; file modes are set here,
not taken from the disk. Usage: python -I make_tar.py <dist folder> <icon png>"""
import io
import os
import sys
import tarfile
import time

dist, icon = sys.argv[1], sys.argv[2]
here = os.path.dirname(os.path.abspath(__file__))
files = [
    (os.path.join(dist, "luka-launcher.jar"), "luka/luka-launcher.jar", 0o644),
    (os.path.join(dist, "luka-boot.jar"), "luka/luka-boot.jar", 0o644),
    (os.path.join(here, "luka.sh"), "luka/luka.sh", 0o755),
    (os.path.join(here, "luka.desktop"), "luka/luka.desktop", 0o644),
    (icon, "luka/luka.png", 0o644),
]
out = os.path.join(dist, "luka-launcher-linux.tar.gz")
now = int(time.time())
with tarfile.open(out + ".part", "w:gz", format=tarfile.PAX_FORMAT) as tar:
    folder = tarfile.TarInfo("luka")
    folder.type, folder.mode, folder.mtime = tarfile.DIRTYPE, 0o755, now
    tar.addfile(folder)
    for source, name, mode in files:
        data = open(source, "rb").read()
        if name.endswith(".sh") or name.endswith(".desktop"):
            data = data.replace(b"\r\n", b"\n")  # a Windows checkout must not break the shell script
        info = tarfile.TarInfo(name)
        info.size, info.mode, info.mtime = len(data), mode, now
        info.uname = info.gname = "luka"
        tar.addfile(info, io.BytesIO(data))
os.replace(out + ".part", out)
print("linux package:", out)
