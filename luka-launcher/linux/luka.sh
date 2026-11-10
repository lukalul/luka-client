#!/bin/sh
# Starts the luka launcher on Linux. Keep this folder writable: launcher updates land here as luka-launcher.jar.new
# and are moved into place by luka-boot.jar on the next start, as on Windows.
#   Game files, settings and logins: ${XDG_DATA_HOME:-~/.local/share}/luka (override with LUKA_HOME)
#   Java for the launcher: this folder's runtime/, then $DATA/launcher-runtime, then a system Java 21+. With none of
#   these, Java 25 from Eclipse Adoptium is downloaded once into $DATA/launcher-runtime (checked against its SHA-256).
#   The game's own Java ($DATA/runtime) is never used here: the launcher replaces it when the update list pins a new
#   build, and a launcher running from it could then no longer start processes.
set -eu

APP=$(dirname "$(readlink -f "$0")")
DATA=${LUKA_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/luka}
mkdir -p "$DATA"

major() { # the major version of a java executable, 0 when it does not run
  "$1" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\)\(\.[^"]*\)\{0,1\}".*/\1/p' | head -n 1 | grep . || echo 0
}

JAVA=""
for candidate in "$APP/runtime/bin/java" "$DATA/launcher-runtime/bin/java"; do
  if [ -x "$candidate" ] && [ "$(major "$candidate")" -ge 21 ]; then JAVA=$candidate; break; fi
done
if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1 && [ "$(major java)" -ge 21 ]; then JAVA=$(command -v java); fi

if [ -z "$JAVA" ]; then
  echo "luka: no Java 21 or newer found, downloading Java 25 from Eclipse Adoptium (first start only)"
  API='https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jre&os=linux&vendor=eclipse'
  fetch() { if command -v curl >/dev/null 2>&1; then curl -fsSL "$1"; else wget -qO- "$1"; fi; }
  INFO=$(fetch "$API")
  LINK=$(printf '%s' "$INFO" | grep -o '"link" *: *"https://[^"]*\.tar\.gz"' | head -n 1 | sed 's/.*"\(https[^"]*\)"/\1/')
  SUM=$(printf '%s' "$INFO" | grep -o '"checksum" *: *"[0-9a-f]\{64\}"' | head -n 1 | sed 's/.*"\([0-9a-f]\{64\}\)"/\1/')
  if [ -z "$LINK" ] || [ -z "$SUM" ]; then echo "luka: Adoptium gave no Java download; install Java 21 or newer and start again" >&2; exit 1; fi
  PART="$DATA/launcher-runtime.download.tar.gz"
  fetch "$LINK" > "$PART"
  if [ "$(sha256sum "$PART" | cut -d ' ' -f 1)" != "$SUM" ]; then rm -f "$PART"; echo "luka: the Java download failed its checksum" >&2; exit 1; fi
  rm -rf "$DATA/launcher-runtime.installing"
  mkdir -p "$DATA/launcher-runtime.installing"
  tar -xzf "$PART" -C "$DATA/launcher-runtime.installing" --strip-components=1
  rm -f "$PART"
  rm -rf "$DATA/launcher-runtime"
  mv "$DATA/launcher-runtime.installing" "$DATA/launcher-runtime"
  JAVA="$DATA/launcher-runtime/bin/java"
fi

# a menu entry that points at this folder (rewritten when the folder moves)
APPS=${XDG_DATA_HOME:-$HOME/.local/share}/applications
ENTRY="$APPS/luka.desktop"
if [ -f "$APP/luka.desktop" ] && ! grep -qF "Exec=\"$APP/luka.sh\"" "$ENTRY" 2>/dev/null; then
  mkdir -p "$APPS"
  sed -e "s|@APP@|$APP|g" "$APP/luka.desktop" > "$ENTRY" 2>/dev/null || true
fi

set -- -Xshare:auto -Dluka.home="$DATA" -Dluka.app="$APP" ${LUKA_MANIFEST:+-Dluka.manifest="$LUKA_MANIFEST"} \
  -cp "$APP/luka-boot.jar" dev.luka.launcher.Boot "$@"
cd "$APP"
exec "$JAVA" "$@"
