# Starshore

Satu aplikasi buat dua hal: ngebersihin sampah di HP dan ngecilin ukuran video. Dibuat ringan, tanpa iklan, tanpa internet.

## Bersihkan

Ketuk Pindai, nanti muncul daftar hasil dan ukurannya. Kamu centang mana yang mau dihapus, baru ketuk Bersihkan. Ketuk nama kategori buat lihat daftar filenya.

- **File duplikat**: dicocokkan dari isi file, bukan dari nama. Satu file asli (yang paling lama) di tiap grup tetap ada, salinannya yang dihapus.
- **File sementara dan log**: .tmp, .log, .bak, thumbnail lama, dan isi folder cache di penyimpanan.
- **APK sisa**: file .apk yang nyangkut di penyimpanan.
- **Folder kosong**.
- **Cache aplikasi**: cache semua app, termasuk yang tidak bisa disentuh app biasa. Butuh Shizuku atau root.
- **Log sistem**: logcat, dan kalau root juga ANR dan tombstone. Butuh Shizuku atau root.
- **TRIM (fstrim)**: merapikan blok kosong di penyimpanan supaya baca tulis tetap lancar. Butuh Shizuku atau root.

## Kompres video

1. Ketuk Cari video, daftar video terbesar di HP muncul.
2. Centang satu atau banyak video. Gambar "Sebelum" muncul di bawah.
3. Atur kualitas: pilih preset Jernih, Seimbang, atau Kecil, lalu ubah sendiri kalau mau. Yang bisa diatur: codec (HEVC atau H.264), resolusi, FPS, bitrate, dan audio. Perkiraan ukuran hasil berubah waktu kamu geser.
4. Ketuk Kompres. Layar dijaga tetap nyala selama prosesnya.
5. Selesai, gambar "Sesudah" muncul. Ketuk gambar buat putar videonya.
6. Kamu ditanya mau hapus file asli atau tidak. Kalau dihapus, hasil kompres dikasih nama file aslinya. Kalau tidak, dua-duanya disimpan.

Kalau hasil kompres ternyata tidak lebih kecil dari aslinya, hasilnya dibuang sendiri supaya ukuran tidak nambah.

## Mode akses

Di bagian atas ada tiga pilihan:

- **Biasa**: duplikat, file sementara, APK sisa, folder kosong, dan kompres video. Cukup izin akses semua file.
- **Shizuku**: nyalain Shizuku dulu, lalu ketuk. Membuka cache app (folder Android/data), log sistem, dan TRIM.
- **Root**: Magisk, KernelSU, atau yang lain. Sama seperti Shizuku, plus bisa bersihin cache di /data dan ANR.

Pilihan terakhir diingat.

## Yang perlu kamu tahu

- Cache di /data/data (folder internal app lain) cuma bisa dibersihkan lewat root. Shizuku mentok di folder Android/data dan perintah pm trim-caches.
- TRIM lewat Shizuku tergantung ROM. Kalau ROM-nya memblokir, tidak ada yang terjadi dan itu bukan error.
- Video dikompres satu per satu. Kalau sistem mematikan app di tengah jalan (HP RAM kecil), prosesnya berhenti dan file hasil yang belum jadi dihapus.
- Duplikat dicek dari seluruh penyimpanan kecuali Android/. Lihat dulu daftarnya sebelum dihapus, terutama kalau ada folder game atau app yang menyimpan data di penyimpanan.

## Build

Upload project ini ke GitHub. Workflow di `.github/workflows/build.yml` jalan otomatis tiap push. APK ada di tab Actions, bagian Artifacts, namanya Starshore-apk. APK ditandatangani pakai debug key, jadi langsung bisa dipasang.

Kalau build error, pakai prompt di `OPENCODE_PROMPT.md`.
