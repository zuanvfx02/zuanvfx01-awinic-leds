#!/system/bin/sh
# Sourced by Magisk / KernelSU / APatch. Use abort (NOT exit) so a failed install
# is reported as failed AND the staged module is deleted, instead of being activated at reboot.

SKIPUNZIP=0

. "$MODPATH/common.sh"

mkdir -p "$LOG_DIR"
log() { ui_print "$1"; echo "$1" >> "$LOG_DIR/customize.log"; }
: > "$LOG_DIR/customize.log"
log "Installation started at $(date)"

# 1. Must be flashed from a booted system (recovery kernels do not expose the LED driver).
if [ "$BOOTMODE" != true ]; then
  abort "! Flash this module from the Magisk/KernelSU app (booted Android), not from recovery."
fi

# 2. The APK must really be inside the zip (a module built without -a would install nothing).
APK="$MODPATH/system/priv-app/AwinicLeds/AwinicLeds.apk"
rm -f "$MODPATH"/system/priv-app/AwinicLeds/*.txt
if [ ! -s "$APK" ]; then
  abort "! AwinicLeds.apk is missing from this zip. Rebuild with: module/build.sh -a <apk> -o module.zip"
fi
if command -v unzip >/dev/null 2>&1 && ! unzip -l "$APK" 2>/dev/null | grep -q AndroidManifest.xml; then
  abort "! AwinicLeds.apk is corrupted (no AndroidManifest.xml)."
fi

# 3. The device must expose a usable AWINIC LED node (same minimum as the app).
NODE=$(find_led_node)
if [ -z "$NODE" ]; then
  log "! LED nodes present: $(ls $LED_ROOT 2>/dev/null | tr '\n' ' ')"
  abort "! No AWINIC LED node in $LED_ROOT. This device/ROM/kernel is not supported."
fi
log "- LED node: $NODE"

MISSING=$(node_missing_files "$NODE")
if [ -n "$MISSING" ]; then
  abort "! $NODE is missing required files:$MISSING . Not supported."
fi
echo "$NODE" > "$MODPATH/node"

# 4. A normally-installed copy of the app overrides the priv-app copy and then runs as
#    untrusted_app, which can never see the LED node. Remove it.
INFO=$(dumpsys package "$PKG" 2>/dev/null)
if [ -n "$INFO" ]; then
  if echo "$INFO" | grep -q "codePath=/data/app" && ! echo "$INFO" | grep -Eq "flags=\[[^]]*SYSTEM"; then
    log "- Found a normally-installed copy of $PKG, removing it (needed for priv-app)"
    pm uninstall "$PKG" >/dev/null 2>&1 || cmd package uninstall "$PKG" >/dev/null 2>&1 \
      || log "! Could not remove it automatically. Uninstall the app manually, then reboot."
  fi
fi

# 5. Drop stale package caches so the priv-app is re-scanned on next boot.
find /data/system/package_cache -type f -name '*AwinicLeds*' -exec rm -f {} + 2>/dev/null

log "- OK. Reboot to activate (the app only becomes a priv-app after a reboot)."
