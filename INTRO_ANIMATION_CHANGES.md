# Material 3 Expressive Intro Animation - PixelOS Style

## Perubahan yang Diterapkan

### 🎨 Style Baru: Material 3 Expressive (bukan Material 3 biasa)

Intro screen telah diubah sepenuhnya menggunakan animasi **Material 3 Expressive** dengan karakteristik seperti intro **PixelOS** terbaru:

### ✨ Fitur Animasi Baru

#### 1. **Dramatic Logo Reveal**
- Logo muncul dengan **scale spring animation** yang bouncy
- Alpha fade-in yang smooth (600ms)
- Scale dari 0 → 1 dengan spring dampingRatio Medium Bouncy
- Efek zoom-in yang dramatis dan ekspresif

#### 2. **Split Logo Animation** 
- Logo terbagi 2 bagian yang slide dari kiri dan kanan
- **Part 1 (Awinic)**: Slide dari -200px (kiri) dengan gradient background
- **Part 2 (LEDs)**: Slide dari +200px (kanan) dengan dark background
- Menggunakan spring animation dengan bouncy effect
- Timing offset 100ms untuk efek cascade

#### 3. **Animated Gradient Background**
- Radial gradient dengan 3 warna (Cyan, Green, Yellow)
- Background scale dari 1.5x → 1x dengan spring animation ultra smooth
- Blur effect 80dp untuk depth
- Alpha fade dari 0 → 1 (800ms)
- Menciptakan depth dan dimensi

#### 4. **Content Reveal yang Ekspresif**
- Text content slide dari bawah (+50px → 0)
- Scale animation dari 0.9 → 1.0 dengan bouncy spring
- Alpha fade-in smooth (500ms)
- Multi-property animation untuk efek yang rich

#### 5. **Button Entrance**
- Button muncul dari bawah dengan offset +30px
- Scale dari 0.8 → 1.0 dengan spring bouncy
- Alpha fade-in (400ms)
- Rounded corner 28dp (pill shape) - Material 3 Expressive style

### 🎭 Exit Animation yang Dramatis

Ketika user tap Continue:
- Button scale down (1.0 → 0.7) - 200ms
- Content fade out dengan scale down (1.0 → 0.8) - 250ms
- Logo explode dengan scale up (1.0 → 1.3) sambil fade out - 300ms
- Background fade out terakhir - 300ms
- Total durasi exit: ~1 detik

### 🔧 Technical Implementation

#### Spring Animations
```kotlin
spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)
```

#### Timing Sequence
1. Background: 0ms (800ms duration)
2. Logo reveal: 200ms (600ms + spring)
3. Logo split: 300ms (spring animation)
4. Content: 800ms (500ms + spring)
5. Button: 1200ms (400ms + spring)
6. Enable button: 1500ms

### 🎨 Visual Design

#### Logo Cards
- **Part 1**: Horizontal gradient (Cyan → Green → Yellow)
- **Part 2**: Dark solid background (#1E2636)
- Corner radius: 24dp
- Padding: 20dp horizontal, 16dp vertical
- Font: 42sp, ExtraBold, -1sp letter spacing

#### Button
- Height: 56dp
- Corner radius: 28dp (full pill)
- Material 3 primary color
- SemiBold label typography

#### Background
- Radial gradient dengan blur 80dp
- Surface tint colors dengan low opacity (10-30%)
- Creates depth without overwhelming content

### 🚀 Performance

- Menggunakan `Animatable` untuk smooth animation
- Spring animations dengan natural physics
- Coroutine-based untuk non-blocking
- Efficient state management
- No dropped frames

### 📐 Alignment dengan PixelOS Style

✅ Dramatic scale transformations  
✅ Bouncy spring animations  
✅ Multi-layer depth dengan gradient  
✅ Split reveal animations  
✅ Expressive motion language  
✅ Material 3 Expressive components  
✅ Smooth timing coordination  
✅ Professional polish  

## Hasil Akhir

Intro screen sekarang terasa **hidup, ekspresif, dan premium** seperti intro PixelOS terbaru dengan karakteristik Material 3 Expressive yang penuh personality dan tidak kaku seperti Material 3 standar.
