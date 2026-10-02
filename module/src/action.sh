#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"

echo "== module =="
grep -E '^(version|versionCode)=' "$MODDIR/module.prop"
echo

echo "== app registration =="
echo "uid: $(awk -v p="$PKG" '$1 == p {print $2}' /data/system/packages.list)"
dumpsys package "$PKG" 2>/dev/null | grep -E "codePath=|flags=|privateFlags=" | head -n 5
echo "(codePath must be /system/priv-app/... ; if it is /data/app the app runs as a normal app and will NOT work)"
echo

echo "== led node =="
NODE=$(find_led_node)
echo "node: ${NODE:-<none>}"
[ -n "$NODE" ] && ls -lZ "$LED_ROOT/$NODE/" | awk '{print $1,$3,$4,$5,$NF}'
echo

echo "== service.log =="
cat "$LOG_DIR/service.log" 2>/dev/null
echo

echo "== selinux denials (live) =="
dmesg 2>/dev/null | grep -E "avc: +denied" | grep -E "sysfs_led|sysfs_leds|aw22xxx|aw210xx|$PKG" | tail -n 30
echo
echo "== selinux denials (at boot) =="
cat "$LOG_DIR/avc.log" 2>/dev/null
