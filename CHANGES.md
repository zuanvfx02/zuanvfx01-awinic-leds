# Perubahan UI - Navigation Bar Capsule & Animasi Scroll

## 🎨 Perubahan yang Diterapkan

### 1. **Floating Capsule Navigation Bar** ✨
Navigation bar sekarang benar-benar **melayang di atas konten** dengan karakteristik:

- ✅ **Bentuk capsule penuh** dengan border radius 999dp
- ✅ **Floating/melayang** - menggunakan `Box` overlay di atas konten
- ✅ **Spacing yang cukup** - padding horizontal 32dp, bottom 20dp
- ✅ **Shadow elevation 12dp** untuk depth yang jelas
- ✅ **Height 68dp** untuk proporsi yang pas
- ✅ **Navigation bars padding** untuk handle system bars
- ✅ **Content padding 96dp** di bottom untuk menghindari overlap

**Arsitektur:**
```kotlin
Box(Modifier.fillMaxSize()) {
    // Konten utama di belakang
    Scaffold { 
        NavHost(modifier = Modifier.padding(bottom = 96.dp)) { ... }
    }
    
    // Navigation bar floating di depan
    Box(Modifier.align(Alignment.BottomCenter)) {
        Surface(/* capsule style */) { ... }
    }
}
```

### 2. **Animasi Scroll-In pada Menu** 🎬
Setiap item di HomeScreen muncul dengan animasi yang smooth:

- **Fade-in**: 400ms duration
- **Slide-in**: dari 1/3 tinggi item, 500ms duration  
- **Staggered delay**: 0ms → 100ms → 200ms → 300ms → 400ms
- **Cascade effect** yang natural dan eye-catching

**Implementasi:**
```kotlin
item { 
    AnimatedScrollItem(delayMillis = 0) {
        HeroCard(...) // Muncul pertama
    }
}

item {
    AnimatedScrollItem(delayMillis = 100) {
        SettingsGroup(...) // Muncul 100ms kemudian
    }
}
```

## 📁 File yang Dimodifikasi

1. **`AnimatedScrollItem.kt`** (NEW) - Composable helper untuk animasi scroll
2. **`AppScreen.kt`** - Diubah total untuk floating navigation bar menggunakan Box overlay
3. **`HomeScreen.kt`** - Ditambahkan AnimatedScrollItem wrapper pada setiap section

## 🎯 Perbedaan Visual

### Navigation Bar

**❌ Sebelumnya:**
- Standard Material 3 NavigationBar
- Menempel di bottom tanpa spacing
- Tidak ada shadow yang kentara
- Terlihat "flat" dan menyatu dengan background

**✅ Sekarang:**
- Floating capsule yang melayang di atas konten
- Spacing 32dp dari kiri-kanan, 20dp dari bottom
- Shadow elevation 12dp untuk kedalaman
- Terpisah jelas dari konten dengan visual depth
- Ukuran height 68dp untuk proporsi modern

### Animasi Home Screen

**❌ Sebelumnya:**
- Item langsung muncul semua sekaligus
- Tidak ada transisi smooth
- Terasa "kaku"

**✅ Sekarang:**
- Hero card muncul duluan (0ms)
- Section Controller muncul 100ms kemudian
- Section State muncul 200ms kemudian
- Section Colors muncul 300ms kemudian
- Section Registers muncul 400ms kemudian
- Fade-in + slide-up yang smooth
- Cascade effect yang elegant

## ✅ Status Kompilasi

- ✅ Kotlin compilation: **BUILD SUCCESSFUL**
- ✅ Tidak ada error atau warning baru
- ✅ Siap untuk di-build dan di-test di device

## 🔧 Detail Teknis

### Floating Navigation Bar
- Menggunakan `Box` dengan `Modifier.align(Alignment.BottomCenter)`
- Content memiliki bottom padding 96dp untuk menghindari overlap
- Navigation bar menggunakan `navigationBarsPadding()` untuk system bars
- Surface dengan `tonalElevation = 4.dp` dan `shadowElevation = 12.dp`

### Animasi
- `AnimatedVisibility` dengan `LaunchedEffect` untuk trigger
- Kombinasi `fadeIn()` + `slideInVertically()`
- Initial offset `it / 3` untuk slide distance yang tidak terlalu jauh
- Delay menggunakan `kotlinx.coroutines.delay()`

## 📱 Hasil Akhir

Navigation bar sekarang terlihat **modern, clean, dan floating** seperti aplikasi iOS atau aplikasi Android modern terbaru. Tidak lagi menempel di bottom seperti standard navigation bar, tapi benar-benar melayang dengan shadow yang jelas. 

Animasi scroll memberikan **feeling premium** saat membuka aplikasi, dengan setiap section muncul secara bertahap yang membuat UX lebih engaging! 🎉

## Music LED crash / swipe fix
- Fixed `Application` name collision in `MusicLedService` by aliasing the app `Application` class.
- Fixed coroutine activity check to use the current coroutine context.
- Fixed Music LED settings sliders: changing sensitivity/frequency no longer launches MediaProjection repeatedly.
- MediaProjection is now requested only when enabling Music LED or pressing the explicit capture-permission button.
- Added brightness fallback for AWINIC kernels that expose `brightness` but not `hwen`.
- Music LED LED enable path now uses `hwen` when available and `brightness` otherwise.
