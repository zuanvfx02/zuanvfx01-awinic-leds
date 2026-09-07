#!/system/bin/sh
MODDIR=${0%/*}

. "$MODDIR/logging.sh"
enable_logging service

echo "Service started at $(date)."
echo "\$MODDIR - $MODDIR"

resetprop -w sys.boot_completed 0 >/dev/null
echo "Boot completed at $(date)."

PKG=zuanvfx01.aw22xxx_leds
echo "Package: $PKG"

APP_UID=$(awk '/^'$PKG'/ {print $2}' /data/system/packages.list)
echo "UID: $APP_UID"

own() {
  echo
  echo "--------- $1 ---------"
  # set sepolicy context
  chcon -v u:object_r:sysfs_led:s0 /sys/class/leds/aw22xxx_led/"$1"
  
  # set app uid as owner
  chown -v "$APP_UID".root /sys/class/leds/aw22xxx_led/"$1"
  
  # set required mode
  chmod -v "$2" /sys/class/leds/aw22xxx_led/"$1"
}

own hwen 644
own effect 644
own cfg 644
own frq 644
own rgb 644
own trigger 644

# prevent value change
own reg 444
own imax 444
own brightness 444
own max_brightness 444
own task0 444
own task1 444