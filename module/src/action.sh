#!/system/bin/sh

echo "cat /data/adb/aw22xxx_leds/service.log"
echo "-----------------------------"
cat /data/adb/aw22xxx_leds/service.log
echo
echo
echo
echo "ls -lZ /sys/class/leds/aw22xxx_led/ | awk '{print \$1,\$3,\$4,\$5,\$10}'"
echo "-----------------------------"
# shellcheck disable=SC2012
ls -lZ /sys/class/leds/aw22xxx_led/ | awk '{print $1,$3,$4,$5,$10}'
echo
echo
echo
echo "awk '/^zuanvfx01.aw22xxx_leds/ {print \$2}' /data/system/packages.list"
echo "-----------------------------"
awk '/^zuanvfx01.aw22xxx_leds/ {print $2}' /data/system/packages.list