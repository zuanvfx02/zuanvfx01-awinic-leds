#!/system/bin/sh
. "$MODPATH/logging.sh"
enable_logging customize

echo "Installation started at $(date)."
echo "\$MODPATH - $MODPATH"

echo "Removing package cache..."
CACHE_FILES=$(find /data/system/package_cache/ | grep AwinicLeds | tr "\n" " ")

for path in $CACHE_FILES; do
  if [ -n "$path" ]; then
    rm -v "$path"
  fi
done

if [ ! -f "/sys/class/leds/aw22xxx_led/hwen" ]; then
  echo No led files found! Exitting...
  exit 1
fi