#!/system/bin/sh
# Simple, race-free logging: everything after enable_logging goes to the log file.
LOG_DIR=/data/adb/aw22xxx_leds

enable_logging() {
  mkdir -p "$LOG_DIR"
  : > "$LOG_DIR/$1.log"
  exec >> "$LOG_DIR/$1.log" 2>&1
}
