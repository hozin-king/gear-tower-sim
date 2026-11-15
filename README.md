# Gear Tower Sim

Simulasi menara gear 1000 (libGDX, native Android) — terinspirasi video viral
menara gear bertumpuk: motor di bawah memutar gear pertama, tiap stage 4x
lebih lambat dari stage di bawahnya, arah berselang-seling, gear paling atas
nyaris diam.

## Cara main

- **Drag horizontal** = engkol menara (kanan = maju, kiri = mundur).
  Ada inersia: lepas jari, menara terus berputar lalu meluruh (atau motor
  mengambil alih bila motor ON).
- **Drag vertikal** = orbit kamera naik/turun sedikit.
- **Pinch** = zoom.
- Tombol: **MOTOR ON/OFF**, **ARAH MAJU/MUNDUR**, **RESET** putaran.
- Slider: kecepatan motor 0.25x–4x.
- HUD: RPM gear bawah, total putaran gear bawah, total putaran gear paling
  atas (9 desimal — saking lambatnya), jumlah stage, arah.

## Build

APK hanya di-build GitHub Actions (push ke `main` → build debug → rilis
otomatis `v<versionName>`). Keystore debug persisten via secret repo
`DEBUG_KEYSTORE_BASE64` supaya rilis berikut bisa timpa-install.

- Package: `com.hozinking.geartower`
- libGDX 1.12.1, Java 17, AGP 8.5.2, Gradle 8.7
- Tanpa WebView, tanpa internet, tanpa iklan, portrait.
