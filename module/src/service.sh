#!/system/bin/sh
MODDIR=${0%/*}

. "$MODDIR/common.sh"
. "$MODDIR/logging.sh"
enable_logging service

echo "Service started at $(date)"
echo "MODDIR - $MODDIR"

# wait for boot
resetprop -w sys.boot_completed 0 >/dev/null 2>&1
echo "Boot completed at $(date)"

# The driver can probe late: wait up to 60s for the node instead of giving up.
NODE=""
i=0
while [ $i -lt 60 ]; do
  NODE=$(find_led_node) && break
  NODE=""
  i=$((i + 1))
  sleep 1
done
[ -z "$NODE" ] && [ -f "$MODDIR/node" ] && NODE=$(cat "$MODDIR/node")
if [ -z "$NODE" ]; then
  echo "ERROR: no AWINIC LED node found after 60s"
  exit 0
fi
DIR="$LED_ROOT/$NODE"
echo "Node: $NODE"

# Wait (best effort) until the priv-app is registered, so we can chown to it.
APP_UID=""
i=0
while [ $i -lt 30 ]; do
  APP_UID=$(awk -v p="$PKG" '$1 == p {print $2}' /data/system/packages.list)
  [ -n "$APP_UID" ] && break
  i=$((i + 1))
  sleep 1
done
echo "Package: $PKG  UID: ${APP_UID:-<not registered>}"

CTX=u:object_r:sysfs_led:s0

# directory itself (needed so the app can stat/traverse the node)
chcon -v "$CTX" "$DIR"
chmod -v 755 "$DIR" 2>/dev/null

own() {
  [ -e "$DIR/$1" ] || { echo "skip $1 (missing)"; return; }
  chcon -v "$CTX" "$DIR/$1"
  # chown is best effort; mode 666 + SELinux (only priv/system apps allowed) keeps it working
  # even when the UID is not known yet.
  [ -n "$APP_UID" ] && chown -v "$APP_UID".root "$DIR/$1"
  chmod -v "$2" "$DIR/$1"
}

# writable controls
for f in hwen effect cfg frq rgb trigger; do own $f 666; done
# read-only (prevent accidental value changes)
for f in reg imax brightness max_brightness task0 task1; do own $f 444; done

# brightness is written by the Music LED feature
[ -e "$DIR/brightness" ] && chmod 666 "$DIR/brightness"

echo "Done at $(date)"
ls -lZ "$DIR"

# Record SELinux denials related to this feature to help debugging
( dmesg 2>/dev/null | grep -E "avc: +denied" | grep -E "sysfs_led|sysfs_leds|aw22xxx|aw210xx|$PKG" | tail -n 40 ) > "$LOG_DIR/avc.log" 2>&1
