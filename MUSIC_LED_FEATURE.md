# Music LED Feature

## Fitur Baru: Music LED

Fitur Music LED memungkinkan LED berkedip mengikuti irama dan beat musik yang sedang diputar dari aplikasi apapun di HP.

## Cara Kerja

### Deteksi Audio
- Menggunakan `AudioRecord` untuk menangkap audio stream
- Mencoba `REMOTE_SUBMIX` untuk audio internal, fallback ke `MIC` jika tidak tersedia
- Sample rate: 44.1 kHz (CD quality)
- Real-time audio analysis

### Beat Detection Algorithm
- Menggunakan RMS (Root Mean Square) untuk menghitung energy audio
- Tracking energy history dengan sliding window
- Beat detection berdasarkan threshold dinamis menggunakan standar deviasi
- Minimum 100ms antar beat (maksimum 10 beats per detik)
- Sensitivity adjustable: 1-10 (1 = sangat sensitif, 10 = hanya beat kuat)

### LED Control
- **Random Effects**: Memilih effect secara random dari daftar yang diaktifkan
- **Dynamic Colors**: Warna berubah otomatis setiap beat dengan algoritma HSV
- **Use Own Values**: Menggunakan warna yang sudah di-set user
- **Frequency Range**: Dapat mengatur min/max frequency LED (1-100 Hz)

## Komponen

### 1. MusicLedService.kt
Foreground service yang berjalan di background untuk:
- Menangkap dan menganalisis audio stream
- Mendeteksi beat secara real-time
- Mengontrol LED sesuai beat yang terdeteksi
- Wake lock untuk mencegah service terhenti

### 2. MusicLedScreen.kt
UI screen untuk konfigurasi:
- Toggle enable/disable service
- Pilih mode effect (random atau fixed)
- Selector untuk memilih effect mana yang akan di-random
- Toggle dynamic colors atau use own values
- Slider sensitivity untuk beat detection
- Slider min/max frequency range
- Permission handling untuk RECORD_AUDIO

### 3. Proto Configuration
```protobuf
message MusicLedConfig {
  bool enabled = 1;
  bool use_random_effects = 2;
  bool use_own_values = 3;
  bool use_dynamic_colors = 4;
  repeated int32 enabled_effects = 5;
  int32 sensitivity = 6;
  int32 min_frequency = 7;
  int32 max_frequency = 8;
}
```

## Permission
Memerlukan permission:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Runtime `RECORD_AUDIO` dan persetujuan `MediaProjection` akan diminta saat user mengaktifkan fitur pertama kali. Persetujuan playback capture tidak dapat dilewati secara diam-diam oleh aplikasi.

## Integrasi

### AndroidManifest.xml
```xml
<service
    android:name=".services.MusicLedService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

### Navigation
Ditambahkan route baru di `AppScreen.kt`:
```kotlin
composable("settings_music") { MusicLedScreen { tabsNav.popBackStack() } }
```

### Settings Menu
Ditambahkan menu item di `SettingsScreen.kt`:
```kotlin
item {
    SettingsRow(
        headline = "Music LED",
        supporting = "LED mengikuti irama musik",
        onClick = onNavigateMusic
    )
}
```

## Fitur Detail

### 1. Mode Efek
- **Random Effect**: Effect berubah secara random setiap beat
- **Effect Selector**: User bisa pilih effect mana saja yang boleh di-random
- Jika tidak ada effect yang dipilih, semua effect akan digunakan

### 2. Mode Warna
- **Dynamic Colors**: 
  - Warna berubah otomatis setiap beat
  - Menggunakan algoritma HSV dengan randomized hue
  - 12 LED dengan offset hue 30° antar LED (rainbow effect)
  - Cooldown 500ms untuk perubahan warna
  
- **Use Own Values**: 
  - Menggunakan warna yang sudah di-set di settings
  - Tidak berubah setiap beat
  - Bisa dikombinasi dengan random effect

### 3. Sensitivity Control
- Range: 1-10
- Formula: `threshold = avgEnergy + (stdDev * (11 - sensitivity) / 5.0)`
- Low (1-3): Sangat sensitif, menangkap beat halus
- Medium (4-7): Balanced, beat normal
- High (8-10): Hanya beat kuat/keras

### 4. Frequency Range
- Min: 1-50 Hz
- Max: 50-100 Hz
- LED frequency akan di-random dalam range ini setiap beat

## Cara Penggunaan

1. Buka aplikasi → Settings → Music LED
2. Grant permission RECORD_AUDIO
3. Toggle "Aktifkan Music LED"
4. (Opsional) Konfigurasi:
   - Aktifkan "Random Effect" dan pilih effect yang diinginkan
   - Aktifkan "Dynamic Colors" untuk warna rainbow otomatis
   - Atau aktifkan "Use Own Values" untuk warna fixed
   - Atur sensitivity sesuai jenis musik
   - Atur frequency range
5. Play musik dari aplikasi apapun (Spotify, YouTube, dll)
6. LED akan berkedip mengikuti beat musik

## Technical Notes

### Audio Source Priority
1. `AudioPlaybackCaptureConfiguration` untuk `USAGE_MEDIA`, `USAGE_GAME`, dan `USAGE_UNKNOWN`
2. Tidak ada fallback microphone; bila aplikasi sumber melarang playback capture, Android tidak menyediakan audio untuk Music LED

### Performance
- Buffer size: 2x minimum buffer (untuk mengurangi latency)
- Analysis interval: 10ms
- History window: 43 samples (untuk smooth detection)
- Minimum beat interval: 100ms (mencegah false positive)

### Service Lifecycle
- Foreground service dengan notification
- Partial wake lock untuk menjaga service tetap aktif
- Auto-stop jika config.enabled = false
- Proper cleanup saat onDestroy

## Testing

Untuk testing fitur ini:
1. Pastikan permission RECORD_AUDIO granted
2. Play musik dengan beat yang jelas (EDM, Hip-Hop, Rock)
3. Adjust sensitivity jika LED tidak merespon atau terlalu sensitif
4. Coba berbagai kombinasi mode (random effect + dynamic colors, dll)
5. Monitor notification untuk memastikan service running

## Future Improvements

Potential enhancements:
- FFT analysis untuk frequency-based color mapping
- Multiple sensitivity zones (bass, mid, treble)
- Visualizer preview dalam UI
- Pattern/mode presets
- Integration dengan audio equalizer settings
- ML-based beat detection untuk accuracy lebih tinggi
