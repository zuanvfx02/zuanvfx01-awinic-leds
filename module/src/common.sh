#!/system/bin/sh
# Shared helpers (sourced by customize.sh / service.sh / action.sh)

PKG=zuanvfx01.aw22xxx_leds
LED_ROOT=/sys/class/leds
LOG_DIR=/data/adb/aw22xxx_leds

# Prints the name of the AWINIC LED node, returns 1 if none.
find_led_node() {
  for n in aw22xxx_led aw210xx_led aw22xxx aw210xx; do
    [ -e "$LED_ROOT/$n" ] && { echo "$n"; return 0; }
  done
  for d in "$LED_ROOT"/*; do
    n=${d##*/}
    case "$n" in
      *[Aa][Ww]22[Xx][Xx][Xx]*|*[Aa][Ww]210[Xx][Xx]*|*[Aa][Ww][Ii][Nn][Ii][Cc]*)
        echo "$n"; return 0 ;;
    esac
  done
  return 1
}

# Same minimum the app itself requires to call a device "compatible".
node_missing_files() {
  _missing=""
  for f in brightness rgb cfg effect; do
    [ -e "$LED_ROOT/$1/$f" ] || _missing="$_missing $f"
  done
  echo "$_missing"
}
