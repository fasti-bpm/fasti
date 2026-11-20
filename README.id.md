[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | Bahasa Indonesia | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

# Fasti BPM Platform

Fasti BPM adalah engine sumber terbuka yang independen, dibangun di atas Camunda 7 Community
Edition 7.24.0. Fasti berjalan di database Camunda 7 yang sudah Anda miliki, dengan library
terkini, perbaikan keamanan, dan antarmuka web baru. Situs web: <https://fasti.dev>

> **Fasti BPM tidak berafiliasi dengan Camunda Services GmbH; Camunda adalah merek dagang Camunda Services GmbH.**
> Fasti BPM adalah proyek sumber terbuka independen (Apache-2.0). Proyek ini bukan bagian dari, tidak disponsori oleh,
> dan tidak didukung secara resmi oleh Camunda Services GmbH. Nama "Camunda" digunakan hanya untuk menjelaskan asal
> kode sumber dan kompatibilitas Fasti.

<a id="quick-start"></a>

## Mulai cepat

Image tersedia di Docker Hub (`fastidev/fasti-bpm`) dan GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), untuk amd64 dan arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.1
```

Buka <http://localhost:8080/fasti/> dan masuk. Tanpa `DB_URL`, image menggunakan basis data H2
di dalam container, hanya untuk evaluasi. REST API memerlukan kredensial Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Panduan lengkap, termasuk dengan database Anda sendiri: <https://fasti.dev/install>

<a id="highlights"></a>

## Fitur unggulan

- **Ganti koneksinya. Itulah migrasinya.** Fasti menggunakan skema Camunda 7.24 apa adanya;
  package `org.camunda.bpm.*` tetap dipertahankan ([migrasi](https://fasti.dev/install#migrate)).
- **Satu container, satu perintah**, dengan variabel `DB_*` yang sama seperti Camunda Run ([instalasi](https://fasti.dev/install)).
- **Dibuat untuk orang yang mengerjakan antrean tugas**: antarmuka web baru dalam 13 bahasa, tema
  terang dan gelap, nyaman dipakai di ponsel ([fitur](https://fasti.dev/#features)).
- **Buat model dan deploy di browser**: BPMN, DMN, dan formulir ([modeler](https://fasti.dev/#modeler)).
- **Alat untuk menjalankannya sudah tersedia**: riwayat, perbaikan instans, retry massal, log
  operasi ([operasional](https://fasti.dev/#operate)).
- **Tertutup secara default**: login wajib sejak start pertama, tanpa pengguna bawaan, celah yang
  diketahui sudah ditutup, Java4Shell dimitigasi ([keamanan](https://fasti.dev/security)).
- **Single sign-on**: OpenID Connect dan JWT bearer token ([panduan](docs/single-sign-on.id.md)).
- **Platform terkini**: Java 17 atau yang lebih baru, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([kinerja](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Dapatkan Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Image Docker** (`fastidev/fasti-bpm`, amd64 dan arm64): <https://fasti.dev/install>; langkah
  demi langkah di [docs/install-docker.id.md](docs/install-docker.id.md), semua variabel di
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Paket Tomcat**, tanpa Docker: <https://fasti.dev/download>. Paket Tomcat 1.0.0 siap pakai:
  - [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
  - [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

  Verifikasi checksum sebelum mengekstrak, misalnya `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`,
  dan baca `README.txt` dalam paket sebelum menjalankannya.
- **Tertanam** di Spring Boot atau aplikasi Java lainnya: koordinat Maven baru
  (`dev.fasti.platform:fasti-*`), package `org.camunda.bpm.*` yang sama; lihat
  [docs/embedding.id.md](docs/embedding.id.md).

<a id="moving-from-camunda-7"></a>

## Pindah dari Camunda 7

Jika Anda menjalankan Camunda 7.24, Fasti berjalan di database yang sama tanpa mengubah skemanya.
Anda dapat mengganti node lama, menjalankan Fasti berdampingan dengannya, dan kembali ke Camunda
7.24 kapan saja. Image Fasti telah diuji saat mengambil alih database yang dibuat oleh Camunda Run
7.24 di PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, dan Oracle Database 23ai; DB2 belum
diuji. Setiap client REST, termasuk external task worker, kini harus mengirim kredensial Basic atau
bearer token.

Aplikasi Spring Boot embedded juga pindah ke Spring Boot 4.1 dan Java 17; skema yang lebih lama
di-upgrade terlebih dahulu dengan skrip standar. Plugin dan process application yang dibuat untuk
Camunda 7 harus di-build ulang terhadap artefak Fasti agar dapat berjalan di dalam Fasti.
Menjalankan berdampingan dan kembali ke Camunda 7.24 memiliki batasan (konfigurasi yang sama di
setiap node, node mana yang menjalankan job, proses yang memakai fitur khusus Fasti): lihat
[operasi campuran](docs/migrate-from-camunda-7.id.md#mixed-operation-with-camunda-7-nodes) dan
[rollback](docs/migrate-from-camunda-7.id.md#rollback-to-camunda-724).

Langkah dan batasan: <https://fasti.dev/install#migrate> dan
[panduan migrasi](docs/migrate-from-camunda-7.id.md) (rollback, skema yang lebih lama, hasil
pengujian).

<a id="comparison"></a><a id="how-it-compares"></a>

## Perbandingan dengan proyek lain

Beberapa proyek bagus melanjutkan Camunda 7. Fasti mempertahankan package `org.camunda.bpm.*`
dan skema 7.24, sehingga perpindahannya cukup berupa pergantian koneksi, bukan proyek migrasi,
dan Fasti menambahkan antarmuka baru. Proyek lain juga merupakan pilihan yang baik. Tabel
lengkapnya, beserta sumber: <https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Keamanan dan dukungan

Rilis minor terbaru dari lini `1.x` menerima perbaikan bug dan patch keamanan, sebagai rilis
patch seperti `1.0.1`. Mohon laporkan masalah keamanan secara privat, bukan di issue publik:
lihat [SECURITY.md](SECURITY.md#security-policy), yang juga memuat
[kebijakan dukungan](SECURITY.md#support-policy). Daftar perbaikan, Java4Shell, dan tinjauan
pra-rilis: <https://fasti.dev/security>. Untuk bantuan instalasi, migrasi, atau produksi, serta
ketentuan dukungan, kirim email ke <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Peta jalan

Ini adalah rencana, bukan komitmen tanggal. Berikutnya di `1.x`: artefak Maven di Maven Central,
Helm chart, metrik Prometheus dan tracing OpenTelemetry, log JSON terstruktur. Untuk `2.0`:
pengganti selama berhalangan, kontrol empat mata untuk persetujuan, jejak audit yang tidak dapat
diubah, dan CMMN di antarmuka baru. Lini masa, dari berakhirnya Camunda 7 CE hingga sasaran 2027:
<https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Dokumentasi

Di [fasti.dev](https://fasti.dev): [Instalasi dengan Docker](https://fasti.dev/install) ·
[Unduhan Tomcat](https://fasti.dev/download) · [Panduan integrasi API](https://fasti.dev/docs) ·
[Keamanan dan dukungan](https://fasti.dev/security) · [Perbandingan](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Dokumen | Isi |
|---|---|
| [docs/install-docker.id.md](docs/install-docker.id.md) | Instalasi dengan Docker, langkah demi langkah |
| [docs/migrate-from-camunda-7.id.md](docs/migrate-from-camunda-7.id.md) | Beralih dari Camunda 7, rollback, batasan |
| [docs/embedding.id.md](docs/embedding.id.md) | Kompatibilitas, perbedaan dari 7.24.0, penomoran versi, koordinat Maven, pemeriksaan saat startup |
| [docs/web-ui.id.md](docs/web-ui.id.md) | Antarmuka web: fitur yang diminta pengguna, path `/fasti`, aplikasi klasik |
| [docs/single-sign-on.id.md](docs/single-sign-on.id.md) | Single sign-on (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | Integrasi REST API, klien tugas eksternal Java dan JavaScript |
| [distro/docker/README.md](distro/docker/README.md) | Image: variabel lingkungan, driver, operasi, penyesuaian |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | UI web baru |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell dan Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | Perubahan, keamanan, kebijakan dukungan |

<a id="build"></a>

## Build dari kode sumber

Memerlukan JDK 17 atau yang lebih baru (21 direkomendasikan) dan akses jaringan ke Maven Central,
nodejs.org, serta registri npm (proses build mengunduh Node/npm untuk UI web).

```bash
./mvnw clean verify
```

Build lengkap menjalankan semua pengujian Java dan pengujian unit UI web (hampir satu jam;
pengujian end-to-end Playwright dijalankan secara terpisah, lihat [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
Lihat [TESTING.md](TESTING.md). Untuk membangun image container (build pertama memerlukan waktu
sekitar 10–20 menit):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Berkontribusi

Pertanyaan, laporan bug, dan pull request dipersilakan di GitHub; lihat
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) dan
[Kode Etik](CODE_OF_CONDUCT.md). Tinjauan terjemahan oleh penutur asli sangat kami harapkan.

<a id="acknowledgements"></a>

## Ucapan terima kasih

- Fasti BPM Platform dibangun di atas hasil kerja Camunda dan komunitas Camunda 7 selama lebih dari
  satu dekade.
- Saat meninjau cacat, proyek merujuk informasi publik (isu, advisori,
  catatan rilis) dari proyek komunitas lain yang merupakan turunan Camunda 7: Operaton, CIB
  seven, OrqueIO, dan EximeeBPMS. Kode sumber dalam repositori ini ditulis
  secara independen di atas Camunda 7 CE.

<a id="license"></a>

## Lisensi

[Lisensi Apache 2.0](LICENSE). Lihat [NOTICE](NOTICE) untuk hak cipta kode asli.
