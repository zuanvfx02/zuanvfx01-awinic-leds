#!/bin/bash

set -e

TOOL_DIR=$(dirname "$(realpath "$0")")
MODULE_DIR="${TOOL_DIR:?}/src"
BUILD_DIR="${TOOL_DIR:?}/build"

help() {
  SELF=$(basename "$0")
  cat << EOF
Usage: $SELF [OPTION]... -o FILE
Builds the module.

Required arguments:
  -o, --output=FILE       Path to the output file

Optional arguments:
  -a, --apk=FILE          Add apk to module
  -h, --help              Display this help and exit
  -i, --install[=MODE]    Install the module on the rooted device after successful build
    ksu                     Use KernelSU to install the module
    magisk                  Use Magisk to install the module
    reboot                  Reboot the device after installing the module

Examples:
  $SELF -o module.zip
  $SELF -a app-release.apk -o module.zip
  $SELF -i -o module.zip
  $SELF --install=reboot,ksu -o module.zip
  $SELF -a app/release/app-release.apk -o module.zip
EOF
}

self_check() {
  if [ ! -f "$MODULE_DIR/module.prop" ]; then
    echo "Could not find the source code of the module!" >&2
    exit 1
  fi
}

ARG_APK_PATH=

ARG_INSTALL=0
ARG_INSTALL_WITH=ksu
ARG_INSTALL_REBOOT=0

ARG_TARGET_PATH=

parse_args() {
  OPTIONS=$(
    getopt \
      -q \
      -l apk:,help,output:,install:: \
      -o a:,h,o:,i:: \
      -n "$(basename "$0")" -- "$@"
  ) || { help && exit 1; }

  eval set -- "$OPTIONS"

  while true; do
    case "$1" in
      -a|--apk)
        ARG_APK_PATH=$(realpath -q "$2") || {
          echo "Incorrect path to the APK file was provided!" >&2
          exit 1
        };
        shift 2 ;;
      -i|--install)
        ARG_INSTALL=1

        for mode in $(echo "$2" | tr "," "\n")
        do
          case $mode in
            ksu|magisk) ARG_INSTALL_WITH="$mode";;
            reboot)     ARG_INSTALL_REBOOT=1    ;;
            *)
              echo "Unknown installation mode '$mode'" >&2
              exit 1 ;;
          esac
        done

        shift 2;;
      -o|--output)
        ARG_TARGET_PATH=$(realpath -q "$2") || {
          echo "Не удалось найти папку в которую будет записан архив!" >&2
          exit 1
        };
        shift 2 ;;
      -h|--help)
        help
        exit ;;
      --)
        shift && break ;;
      *)
        echo "Passed unknown argument '$1'"
        help
        exit 1 ;;
    esac
  done
}

check_vars() {
  if [ -n "$ARG_APK_PATH" ] && [ ! -f "$ARG_APK_PATH" ]; then
    echo "Не удалось найти APK файл по пути $ARG_APK_PATH!" >&2
    exit 1
  fi

  if [[ "$ARG_INSTALL" -eq 1 ]] && ! adb devices | awk '{print $2}' | grep device >/dev/null
  then
    echo "Не удалось найти устройство для установки модуля!" >&2
    exit 1
  fi
}

pack() {
  # Удаление временных файлов
  rm -rf "${BUILD_DIR:?}/"*
  rm -f "$ARG_TARGET_PATH"

  # Копирование файлов модуля в папку сборки
  mkdir -p "$BUILD_DIR"
  cp -r "$MODULE_DIR/"* "$BUILD_DIR"

  # Копирование приложения в папку сборки, если указан путь
  if [ -n "$ARG_APK_PATH" ]; then
    mkdir -p "$BUILD_DIR/system/priv-app/AwinicLeds"
    cp "$ARG_APK_PATH" "$BUILD_DIR/system/priv-app/AwinicLeds/AwinicLeds.apk"
  fi

  # Упаковка всех файлов в директории сборки в архив
  (cd "$BUILD_DIR" && zip -r "$ARG_TARGET_PATH" ./*)

  echo "Модуль успешно упакован в $(realpath "$ARG_TARGET_PATH" --relative-to="$(realpath .)")!"
}

install() {
  adb shell "rm -f /data/local/tmp/module.zip"
  adb push "$ARG_TARGET_PATH" /data/local/tmp/module.zip

  case $ARG_INSTALL_WITH in
    ksu)
      adb shell "su -c /data/adb/ksud module install /data/local/tmp/module.zip"
      ;;
    magisk)
      adb shell "su -c /data/adb/magisk/magisk --install-module /data/local/tmp/module.zip"
      ;;
  esac

  if [ $ARG_INSTALL_REBOOT -eq 1 ]; then
    adb shell reboot
  fi

  echo "Установка модуля завершена успешно!"
}

self_check

parse_args "$@"
check_vars

pack

if [[ $ARG_INSTALL -eq 1 ]]; then
  install
fi