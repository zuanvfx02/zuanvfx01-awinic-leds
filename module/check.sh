#!/bin/bash
# Sanity-checks a built module zip so a broken one is never released.
# Usage: module/check.sh <module.zip> [--require-apk]
set -u

ZIP=${1:-}
REQUIRE_APK=0
[ "${2:-}" = "--require-apk" ] && REQUIRE_APK=1

[ -f "$ZIP" ] || { echo "FAIL: zip not found: $ZIP" >&2; exit 1; }

FAILS=0
fail() { echo "FAIL: $*" >&2; FAILS=$((FAILS + 1)); }
ok()   { echo "ok:   $*"; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

unzip -tq "$ZIP" >/dev/null 2>&1 && ok "zip integrity" || fail "zip is corrupt"
unzip -q "$ZIP" -d "$WORK" || { fail "cannot extract"; exit 1; }

# required module files
for f in module.prop customize.sh service.sh common.sh sepolicy.rule \
         META-INF/com/google/android/update-binary META-INF/com/google/android/updater-script; do
  [ -f "$WORK/$f" ] && ok "has $f" || fail "missing $f"
done

# module.prop must be valid
PROP="$WORK/module.prop"
if [ -f "$PROP" ]; then
  grep -q $'\r' "$PROP" && fail "module.prop has CRLF line endings (Magisk breaks)" || ok "module.prop line endings"
  ID=$(sed -n 's/^id=//p' "$PROP")
  VER=$(sed -n 's/^version=//p' "$PROP")
  CODE=$(sed -n 's/^versionCode=//p' "$PROP")
  [ "$ID" = "zuanvfx01_aw22xxx_leds" ] && ok "module id = $ID" || fail "unexpected module id '$ID'"
  [ -n "$VER" ] && ok "version = $VER" || fail "version missing"
  case "$CODE" in ''|*[!0-9]*) fail "versionCode not numeric ('$CODE')" ;; *) ok "versionCode = $CODE" ;; esac
fi

# shell scripts: LF only + valid syntax
for f in customize.sh service.sh common.sh action.sh logging.sh; do
  [ -f "$WORK/$f" ] || continue
  grep -q $'\r' "$WORK/$f" && fail "$f has CRLF line endings" || true
  sh -n "$WORK/$f" 2>/dev/null && ok "$f syntax" || fail "$f has a shell syntax error"
done

# the APK is the whole point of the module
APK="$WORK/system/priv-app/AwinicLeds/AwinicLeds.apk"
if [ -f "$APK" ]; then
  SIZE=$(wc -c < "$APK")
  [ "$SIZE" -gt 100000 ] && ok "APK present (${SIZE} bytes)" || fail "APK suspiciously small (${SIZE} bytes)"
  unzip -tq "$APK" >/dev/null 2>&1 && ok "APK is a valid zip" || fail "APK is corrupt"
  unzip -l "$APK" 2>/dev/null | grep -q "AndroidManifest.xml" && ok "APK has AndroidManifest.xml" || fail "APK has no AndroidManifest.xml"
elif [ "$REQUIRE_APK" -eq 1 ]; then
  fail "system/priv-app/AwinicLeds/AwinicLeds.apk is missing (zip without APK)"
else
  echo "note: no APK in zip (allowed without --require-apk)"
fi

if [ "$FAILS" -gt 0 ]; then
  echo "$FAILS check(s) failed" >&2
  exit 1
fi
echo "All checks passed."
