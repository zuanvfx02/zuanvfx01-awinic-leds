# Awinic LED Manager (Root)

![License](https://img.shields.io/github/license/yourusername/awinic-leds)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Access](https://img.shields.io/badge/Access-Root-red)

A highly optimized fork of the original [n08i40k/awinic-leds](https://github.com/n08i40k/awinic-leds). This project is dedicated to providing low-level hardware control for Android devices equipped with Awinic LED controllers and rear interface lighting.

## 🚀 Overview

This fork focuses on **Execution Efficiency** and **Hardware Abstraction**. We have re-engineered the core logic to reduce latency in root-level commands, making it ideal for real-time applications such as music-to-light synchronization and low-latency notifications.

### Key Enhancements
* **Performance Optimization:** Refactored I/O operations for faster `/sys/class/leds/` node writing.
* **Enhanced Device Detection:** Improved scanning logic to accurately identify unique LED zones across different OEMs.
* **UI/UX Streamlining:** A more developer-centric interface for easier debugging and preset management.
* **Improved Polling:** Reduced CPU overhead during active LED state monitoring.

---

## 📱 Supported Hardware Ecosystem

This project aims to support devices featuring rear-mounted LED interfaces (Glyph, Mecha, and RGB strips). Below is the current compatibility list:

### **1. Nothing (Glyph Interface)**
* Nothing Phone (1)
* Nothing Phone (2)
* Nothing Phone (2a) / 2a Plus

### **2. Transsion Holdings (Infinix & Tecno)**
* **Infinix:** GT 20 Pro (Mecha Loop), GT 10 Pro
* **Tecno:** Pova 6 Pro 5G, Pova 5 Pro 5G

### **3. ZTE / Nubia (RedMagic & Neo)**
* **RedMagic:** 9 Pro / 9S Pro (Internal Fan & Strip), 8 Series, 7/6 Series
* **Nubia Neo:** Neo 2 5G (Hero Eye Zone)

### **4. ASUS (ROG Phone)**
* ROG Phone 8 Pro (AniMe Vision)
* ROG Phone 7 Ultimate (ROG Vision)
* ROG Phone 6 / 5 Series (RGB Logo)

### **5. Xiaomi (Black Shark)**
* Black Shark 5 Pro / 5RS
* Black Shark 4 Pro
* Poco F4 GT / Redmi K50 Gaming

---

## Developer Integration

To interact with the LED controller at the kernel level, this application targets the following system nodes:

```bash
# Common Awinic Controller Path
/sys/class/leds/aw210xx_led/

# Standard LED Parameters
/sys/class/leds/<led_name>/brightness
/sys/class/leds/<led_name>/blink
/sys/class/leds/<led_name>/lut_entry
