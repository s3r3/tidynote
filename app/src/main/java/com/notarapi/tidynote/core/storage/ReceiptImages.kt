package com.notarapi.tidynote.core.storage

import android.content.Context
import java.io.File

/**
 * Satu-satunya sumber kebenaran lokasi gambar struk.
 *
 * Sebelumnya penulis dan pembaca memakai path berbeda tanpa ada yang sadar:
 * `ScanScreen` menulis ke `filesDir/receipts`, sementara `SettingsScreen`
 * mengukur `context.getDir("receipts")` yang menghasilkan `files/app_receipts`.
 * Akibatnya "Ukuran penyimpanan" tidak pernah menghitung satu pun gambar struk,
 * dan hapus-semua-data meninggalkan file yatim. Semua pihak sekarang lewat objek
 * ini supaya tidak bisa melenceng lagi.
 */
object ReceiptImages {

    private const val DIR_NAME = "receipts"

    /** Direktori gambar struk. Belum tentu ada di disk. */
    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    /**
     * File baru untuk menyimpan hasil scan. Nama berbasis waktu supaya unik;
     * direktori dibuat kalau belum ada. Pemanggil yang menulis isinya.
     */
    fun newFile(context: Context): File {
        val dir = dir(context).apply { mkdirs() }
        return File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Total ukuran gambar struk dalam byte. 0 kalau direktori belum ada.
     *
     * Filter `isFile` itu penting: `File.length()` pada direktori mengembalikan
     * ukuran entri direktori, bukan isinya, sehingga tanpa filter hasilnya membengkak.
     */
    fun sizeBytes(context: Context): Long {
        val dir = dir(context)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Hapus seluruh gambar struk. `true` kalau direktori sudah tidak ada
     * (termasuk kalau memang belum pernah ada).
     */
    fun deleteAll(context: Context): Boolean {
        val dir = dir(context)
        return !dir.exists() || dir.deleteRecursively()
    }
}
