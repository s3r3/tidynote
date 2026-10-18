# Kebijakan Privasi TidyNote (NotaRapi)

- OCR berjalan di perangkat (ML Kit on-device). Foto struk disimpan lokal di penyimpanan aplikasi.
- Bantuan AI cloud **default mati**. Hanya jika Anda mengaktifkannya di Pengaturan dan menekan
  **Perbaiki dengan AI**, teks OCR (maks 8000 karakter, bukan foto) dikirim ke backend Anda sendiri.
- Tidak ada akun, pelacakan iklan, atau pengiriman data otomatis.
- Hapus data di Pengaturan menghapus record database beserta file gambar terkait.
- Backup sistem Android dinonaktifkan untuk database (`allowBackup=false`); gunakan ekspor CSV/PDF manual.
