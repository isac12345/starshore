# CHANGES — Starshore

## Build v2.1 (hijau, run 38049180058, ~3m23s)

APK: `Starshore-apk` → `app-release.apk` (778 KB, debug-signed).

Fix dari zip (tak disentuh): UI banner (fade + sudut) dan tab/mode
(MainActivity.kt, +89/−34).

### Yang diubah (tahan workflow + 1 fix)

1. `.github/workflows/build.yml` — zip v2.1 me-reset workflow lagi;
   dikembalikan ke versi ber-SDK.
2. `Compressor.kt` — `AudioEncoderSettings` muncul lagi di zip; dihapus lagi
   (hasil akhir identik dengan v2 — commit ini murni MainActivity).
   Catatan audio-64k placebo tetap berlaku.

APK: `Starshore-apk` → `app-release.apk` (777 KB, debug-signed).

Fitur baru dari zip (tak disentuh): Compare pembanding geser, KeepAlive
foreground service, target ukuran + retry koreksi, preview 5 detik, preset
"Rek. layar", analisis penyimpanan, HEVC otomatis fallback H.264.

### Yang diubah (tahan workflow + 1 fix, tanpa ubah fitur/tampilan/paket/versi)

1. `.github/workflows/build.yml` — zip v2 mengembalikan workflow tanpa SDK;
   dikembalikan ke versi ber-SDK (langkah apt/sdkmanager platform-34).
2. `Compressor.kt` — `AudioEncoderSettings` muncul lagi di zip v2 (aud "64k");
   dihapus lagi sebelum push karena terbukti unresolved di Media3 1.4.1
   (run 38030643187 kemarin). API baru lain (`videoEncoderName`,
   `ClippingConfiguration`, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, Compare)
   lolos compile tanpa perubahan.
   Catatan jujur tetap berlaku: pilihan audio 64k memakai bitrate default
   encoder; "Asli" dan "Tanpa audio" berfungsi normal.

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
