# Prompt opencode

Ada dua prompt: satu buat error build, satu buat bug waktu APK dipakai. Salin isi blok kode-nya.

## 1. Perbaiki build sampai jadi APK

```
Project ini aplikasi Android bernama Starshore (Kotlin, framework View biasa, tanpa Compose dan tanpa AppCompat, sengaja begitu supaya ringan). Build-nya lewat GitHub Actions di .github/workflows/build.yml dan menghasilkan APK release yang ditandatangani debug key.

Tugasmu: bikin build berhasil dan APK-nya bisa dipasang, tanpa mengubah fitur atau tampilan.

Langkah:
1. Kalau Android SDK dan gradle ada di mesin ini, jalankan `gradle assembleRelease --stacktrace`. Kalau tidak ada, commit dan push, lalu baca hasilnya dengan `gh run list` dan `gh run view <id> --log-failed`.
2. Baca error-nya sampai ketemu penyebab aslinya, perbaiki, lalu build ulang. Satu penyebab per putaran, jangan nebak banyak perubahan sekaligus.
3. Ulangi sampai workflow hijau dan artifact Starshore-apk ada. Jangan berhenti di tengah dan jangan minta konfirmasi di setiap langkah.

Aturan:
- Jangan hapus fitur, jangan ganti nama paket (app.starshore), jangan migrasi ke Compose atau AppCompat.
- Jangan tambah dependensi baru kecuali tidak ada jalan lain. APK harus tetap ringan.
- Versi AGP 8.5.2, Kotlin 1.9.24, Gradle 8.9, Media3 1.4.1, Shizuku 13.1.5 sengaja dipilih. Ubah versi hanya kalau memang tidak bisa di-resolve.
- Kalau ada API yang tidak cocok, pilih perbaikan paling kecil dan paling aman.

Tempat yang paling mungkin error:
- Compressor.kt: nama kelas dan signature Media3 (Transformer.Listener, Presentation.createForWidthAndHeight, FrameDropEffect.createDefaultFrameDropEffect, AudioEncoderSettings, Effects).
- Sh.kt: pemanggilan Shizuku.newProcess lewat refleksi, dan merge manifest untuk ShizukuProvider.
- R8 saat assembleRelease: kalau ada kelas hilang atau crash karena shrink, tambah aturan di app/proguard-rules.pro.

Kalau sudah hijau, tulis ringkasan singkat di CHANGES.md: apa yang diubah dan kenapa.
```

## 2. Perbaiki bug saat dipakai

Ganti bagian dalam kurung siku sebelum disalin.

```
Project ini aplikasi Android bernama Starshore (lihat README.md buat daftar fitur). APK hasil build-nya bermasalah waktu dipakai.

Masalah: [jelaskan, contoh: tombol Kompres langsung crash / pindai tidak nemu duplikat / TRIM tidak jalan]
HP dan Android: [contoh: Redmi Note 10, Android 13]
Mode akses: [Biasa / Shizuku / Root]
Log (kalau ada): [tempel hasil `adb logcat -d | grep -i -E "starshore|AndroidRuntime"`]

Cari penyebabnya di kode, perbaiki, lalu build ulang lewat GitHub Actions sampai hijau (cara kerjanya sama seperti prompt build). Jangan hapus fitur lain dan jangan tambah dependensi berat. Setelah itu catat perubahan di CHANGES.md.
```
