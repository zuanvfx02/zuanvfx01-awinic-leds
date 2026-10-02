# Awinic LED Manager

[![Platform](https://img.shields.io/badge/Platform-Android_12+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Requirement](https://img.shields.io/badge/Root-KernelSU%20%7C%20APatch%20%7C%20Magisk-critical?style=flat-square)](https://github.com)
[![UI](https://img.shields.io/badge/Design-Material_3_Expressive-6750A4?style=flat-square)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square)](LICENSE)

A low-level hardware controller for Android devices equipped with Awinic LED drivers and OEM rear lighting interfaces (Glyph, Mecha Loop, AniMe Vision, and RGB strips).

Forked and optimized from [n08i40k/awinic-leds](https://github.com/n08i40k/awinic-leds), featuring a modern user interface powered by Jetpack Compose, **Material 3 Expressive**, and translucent glass components.

---

## Highlights

- **Hardware-Level I/O:** Direct interaction with `/sys/class/leds/` nodes with reduced root shell overhead for low-latency triggers (music visualizer, notification sync).
- **Modern Adaptive UI:** Built with Material 3 Expressive design specifications and backdrop blur effects inspired by [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).
- **OEM Abstraction:** Unified controls across disparate vendor implementations (Nothing Glyph, Transsion Mecha, ROG Vision, etc.).
- **Battery-Conscious Polling:** Event-driven architecture minimizing background CPU cycles when lights are inactive.

---

## 📱 Hardware Support Matrix

Status indicator:
- `✓ Active` — Fully mapped and functional in the current build.
- `○ Planned` — Driver nodes identified; pending mapping, validation, or contribution.

| OEM / Brand | Model | Interface / Zone | Status |
| :--- | :--- | :--- | :---: |
| **Nothing** | Phone (1) | Glyph Interface | ✓ Active |
| | Phone (2) | Glyph Interface (Multi-zone) | ✓ Active |
| | Phone (2a) / 2a Plus | Glyph Interface | ✓ Active |
| **Transsion** | Infinix GT 20 Pro | Mecha Loop LED | ✓ Active |
| | Infinix GT 10 Pro | Mecha Mini-LED | ✓ Active |
| | Infinix Note 40 / 50 Series | Active Halo | ○ Planned |
| | Tecno Pova 6 Pro 5G | Dynamic-Eye / Rear Strip | ✓ Active |
| | Tecno Pova 5 Pro 5G | Rear Light Strip | ✓ Active |
| | Tecno Pova 6 Neo | Rear Accent | ○ Planned |
| **ZTE / Nubia** | RedMagic 9 Pro / 9S Pro | Internal Fan & Strip | ✓ Active |
| | RedMagic 8 Series | RGB Logo & Strip | ✓ Active |
| | RedMagic 6 / 7 Series | Dual Strip / Fan RGB | ✓ Active |
| | RedMagic 10 Pro / 10 Pro+ | RGB Fan / Rear Matrix | ○ Planned |
| | Nubia Neo 2 5G | Hero Eye Zone | ✓ Active |
| **ASUS** | ROG Phone 8 Pro | AniMe Vision (Matrix) | ✓ Active |
| | ROG Phone 7 Ultimate | ROG Vision (Rear Display/LED) | ✓ Active |
| | ROG Phone 5 / 6 Series | RGB Dotted Logo | ✓ Active |
| | ROG Phone 9 / 9 Pro | AniMe Vision | ○ Planned |
| **Xiaomi** | Black Shark 5 Pro / 5RS | Shark Eye Dual RGB | ✓ Active |
| | Black Shark 4 Pro | Dual RGB Strip | ✓ Active |
| | Poco F4 GT / Redmi K50G | Dual Shoulder & Camera RGB | ✓ Active |
| | Poco F3 GT / Redmi K40G | Camera Ring RGB | ○ Planned |
| **iQOO** | iQOO 9 / 10 / 11 Series | Monster Halo | ○ Planned |
| | iQOO 12 / 13 Series | Monster Halo Accent | ○ Planned |
| **Realme** | Realme GT Neo 5 (240W) | Pulse C-Shaped Halo | ○ Planned |
| | Realme GT 5 | Pulse Halo Strip | ○ Planned |
| **Lenovo** | Legion Phone Duel / Duel 2 | Dual RGB Engine | ○ Planned |
| | Legion Y70 / Y90 | Rear RGB Logo | ○ Planned |
| **Meizu** | Meizu 20 Infinity | Ring Flash LED | ○ Planned |
| | Meizu 21 | Aicy Smart RGB Ring | ○ Planned |
| **Sony** | Xperia 1 V / 1 VI | Legacy Notification LED | ○ Planned |

---

## 🛠 Technical Architecture

The core daemon targets standard kernel sysfs pathways. Devices typically expose Awinic ICs (e.g., AW21009, AW21018, AW2013) or proprietary OEM nodes:

```bash
# Common Awinic driver nodes
/sys/class/leds/aw210xx_led/
/sys/class/leds/<zone_name>/brightness
/sys/class/leds/<zone_name>/blink
/sys/class/leds/<zone_name>/lut_entry

# OEM-specific sysfs nodes
/sys/class/leds/aw_led/rgb_effect
/sys/devices/platform/soc/.../leds/
