# Starshore

Satu aplikasi buat dua hal: ngebersihin sampah di HP dan ngecilin ukuran video. Ringan, tanpa iklan, tanpa internet.

## Bersihkan

Ketuk Pindai, muncul daftar hasil dan ukurannya. Kamu centang mana yang mau dihapus, baru ketuk Bersihkan. Ketuk nama kategori buat lihat daftar filenya.

- **File duplikat**: dicocokkan dari isi file, bukan nama. Satu file asli (yang paling lama) tiap grup tetap ada.
- **File sementara dan log**: .tmp, .log, .bak, thumbnail lama, isi folder cache, dan sampah galeri (.trashed-, .pending-).
- **APK sisa** dan **folder kosong**.
- **Sisa aplikasi yang sudah dihapus**: folder di Android/data, obb, dan media milik app yang sudah tidak terpasang. Butuh Shizuku atau root.
- **Cache aplikasi**: cache semua app. Butuh Shizuku atau root.
- **Log sistem**: logcat, plus ANR dan tombstone kalau root. Butuh Shizuku atau root.
- **TRIM (fstrim)**: merapikan blok kosong di penyimpanan. Butuh Shizuku atau root. Kalau ROM tidak punya perintahnya, app bilang dilewati.

## Kompres video

1. Ketuk Cari video. Daftar video terbesar muncul lengkap dengan gambar kecilnya. Ketuk gambarnya buat memutar video itu.
2. Centang satu atau banyak video.
3. Atur kualitas: pilih Jernih, Seimbang, Kecil, atau Rek. layar, lalu ubah sendiri kalau mau. Yang bisa diatur: codec, resolusi, FPS, dan audio.
4. Ukuran hasil punya tiga cara:
   - **Otomatis**: persen dari bitrate video aslinya.
   - **Target ukuran**: misal 10, 25, atau 50 MB per video. Kalau hasilnya meleset, dicoba sekali lagi dengan bitrate yang dikoreksi.
   - **Manual**: bitrate sendiri.
   Apa pun pilihannya, bitrate hasil dibatasi di bawah bitrate asli, jadi file tidak pernah membesar.
5. **Preview 5 detik** (opsional): app mengompres klip 5 detik dari tengah video pakai pengaturanmu, lalu menampilkannya di pembanding. Ukuran hasil penuh diperkirakan dari klip itu.
6. Ketuk Kompres. Prosesnya jalan di latar belakang dengan notifikasi, jadi aman kalau layar dimatikan.
7. Selesai, muncul **pembanding**: satu video, bagian kiri video asli, bagian kanan hasil kompres, garis putih bisa digeser. Kalau banyak video, ada tombol angka buat ganti.
8. Di bawah pembanding kamu pilih Hapus file asli atau Simpan keduanya. Kalau dihapus, hasil kompres memakai nama file asli.

Hasil yang tidak lebih kecil dari aslinya dibuang sendiri. Di bawah tombol Kompres ada catatan tiap video: ukuran sebelum dan sesudah, kecepatan (misal 2,3x real-time), dan nama encoder yang dipakai. Kalau kompres terasa lambat, catatan itu yang dikirim buat dicek.

Soal codec: HEVC lebih kecil tapi hanya cepat kalau HP punya encoder HEVC hardware. Kalau tidak ada, app otomatis memakai H.264.

## Analisis

Folder dan file terbesar di penyimpanan. Ketuk file buat menghapusnya. Folder Android/ tidak ikut dihitung.

## Mode akses

- **Biasa**: duplikat, file sementara, APK sisa, folder kosong, kompres video, analisis. Cukup izin akses semua file.
- **Shizuku**: nyalain Shizuku dulu, lalu ketuk. Membuka cache app, sisa app, log sistem, dan TRIM.
- **Root**: Magisk, KernelSU, atau yang lain. Sama seperti Shizuku, plus cache di /data dan ANR.

Pilihan terakhir diingat.

## Yang perlu kamu tahu

- Cache di /data/data (folder internal app lain) cuma bisa dibersihkan lewat root.
- TRIM tergantung ROM. Kalau diblokir, tidak ada yang terjadi dan itu bukan error.
- Duplikat dicek dari seluruh penyimpanan kecuali Android/. Lihat dulu daftarnya, terutama kalau ada folder game atau app yang menyimpan data di penyimpanan.
- Pembanding memutar dua video sekaligus. Di HP yang sangat lemot bisa tersendat. Pakai tombol Jeda kalau terasa berat.
- Kompres memakai encoder hardware lewat Media3, jadi tidak ada FFmpeg dan APK tetap kecil.

## Build

Upload project ini ke GitHub. Workflow di `.github/workflows/build.yml` jalan otomatis tiap push. APK ada di tab Actions, bagian Artifacts, namanya Starshore-apk. APK ditandatangani pakai debug key, jadi langsung bisa dipasang.

Kalau build error atau ada bug, pakai prompt di `OPENCODE_PROMPT.md`.
