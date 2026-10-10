# CHANGES — Starshore

## Build 1.0 (hijau, run 38030786089, ~3m48s)

APK: `Starshore-apk` → `app-release.apk` (748 KB, debug-signed, langsung bisa dipasang).

### Yang diubah (2 file, tanpa ubah fitur/tampilan/paket/versi)

1. `.github/workflows/build.yml` (+13 baris)
   - Kenapa: runner `ubuntu-latest` tidak punya Android SDK; build gagal sebelum
     compile (compileSdk 34 tidak ada).
   - Apa: pasang SDK platform-34 + build-tools 34.0.0 lewat apt/sdkmanager,
     set `ANDROID_HOME`/`ANDROID_SDK_ROOT`. Tanpa `android-actions/setup-android`.

2. `app/src/main/java/app/starshore/Compressor.kt` (-7/+1 baris + komentar)
   - Kenapa: `e: .../Compressor.kt:13:36 Unresolved reference: AudioEncoderSettings`
     — kelas itu tidak ada di `media3-transformer:1.4.1` (versi yang dipin).
   - Apa: hapus import + blok `setRequestedAudioEncoderSettings(...)`.
     Audio tetap di-encode dengan pengaturan default encoder; opsi bisu
     (`setRemoveAudio` saat audio = 0) tidak berubah.
   - Catatan jujur: kenop bitrate audio 128/64 kbps di UI sekarang tidak
     mengubah bitrate (pakai default). Kalau mau kenop itu berfungsi lagi,
     naikkan Media3 ke versi yang punya `AudioEncoderSettings` (bukan 1.4.1).
