# Third-Party Notices — TidyNote (NotaRapi)

Aplikasi ini bergantung pada pustaka open-source berikut. Semua lisensi di bawah
ini mengizinkan penggunaan, modifikasi, dan distribusi dengan syarat yang
disebutkan. Daftar ini harus dirawat seiring perubahan `gradle/libs.versions.toml`.

| Library | Lisensi | Kegunaan |
|---|---|---|
| AndroidX Core / AppCompat / Activity | Apache-2.0 | Dasar Android & Compose |
| AndroidX Lifecycle, Navigation, Room | Apache-2.0 | Arsitektur, navigasi, database lokal |
| Jetpack Compose (Material3, Foundation, UI) | Apache-2.0 | UI deklaratif |
| Koin | Apache-2.0 | Dependency injection |
| Kotlin Coroutines & Serialization | Apache-2.0 | Konkurensi & JSON |
| ML Kit Text Recognition (bundled latin) | Lisensi ML Kit Terms | OCR on-device |
| ML Kit Document Scanner (Play Services) | Lisensi Google APIs | Scan/crop/galeri |
| Coil | Apache-2.0 | Memuat gambar struk |
| OkHttp | Apache-2.0 | HTTP ke backend LLM |
| (Opsional, saat online) Ktor Server/Client | Apache-2.0 | Backend proxy LLM |
| (Opsional, saat online) Kover | Apache-2.0 | Laporan coverage |

## AndroidX & Jetpack
    Licensed under the Apache License, Version 2.0.

## ML Kit (Google)
    Penggunaan tunduk pada Google APIs Terms of Service dan
    ML Kit-specific terms. Model OCR berjalan di perangkat (on-device);
    gambar struk tidak dikirim ke Google.

## OkHttp / Coil
    Licensed under the Apache License, Version 2.0.

> Verifikasi ulang lisensi sebelum rilis publik dengan
> `./gradlew :app:dependencies` dan generator lisensi (mis. AboutLibraries).
