[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | Bahasa Indonesia | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition berakhir dengan 7.24.0 sebagai
rilis terakhirnya. Fasti BPM melanjutkannya dari titik tersebut: mesin yang sama pada basis data yang sama, dengan
perbaikan dan pembaruan pustaka yang berkelanjutan, serta UI web baru.

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

**Bangun image sendiri** dari kode sumber. Ini memerlukan Docker, JDK 17+, dan akses jaringan
ke Maven Central, nodejs.org, serta registri npm (proses build mengunduh Node/npm untuk
UI web); build pertama memerlukan waktu sekitar 10–20 menit:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.1
```

Image **mewajibkan autentikasi REST dan menerapkan otorisasi secara default** (berbeda dengan Camunda
Run); hanya `FASTI_INSECURE_DEMO=true` yang menonaktifkannya, untuk demo.

<a id="highlights"></a>

## Fitur unggulan

- **UI web baru** di `/fasti/` (React):
  - mencari instans proses berdasarkan beberapa variabel sekaligus, digabungkan dengan AND/OR;
  - linimasa kasus di dalam tugas (komentar dan tindakan pada setiap langkah);
  - tombol "Ambil pekerjaan berikutnya";
  - tenggat waktu dan urgensi (SLA), pekerjaan yang melewati tenggat;
  - beban kerja tim per pengguna dan grup;
  - panduan operasional yang dilampirkan pada insiden;
  - halaman kesehatan sistem (insiden, job gagal, job terlambat, tugas eksternal, batch);
  - tata letak yang ramah perangkat seluler;
  - 13 bahasa.
- **Single sign-on (OIDC)**: Keycloak, Microsoft Entra ID, dan penyedia OpenID Connect lainnya;
  klaim dipetakan ke grup dan tenant; token bearer JWT untuk REST API. Tersedia untuk Run,
  Spring Boot, Tomcat, dan WildFly.
- **Penguatan keamanan**: daftar izin untuk ekspresi dalam kueri dan filter, pemeriksaan lampiran dan
  komentar di dalam mesin, penyiapan awal, izin Cockpit, pengalihan terbuka, header
  aplikasi web, cookie, dan CSP. Detail tersedia di [CHANGELOG](CHANGELOG.md).
- **Autentikasi aktif secara default**: image mewajibkan kredensial untuk REST dan menerapkan
  otorisasi dalam setiap mode.
- **Pemeriksaan versi skema**: mesin menolak untuk berjalan pada skema yang lebih lama dari 7.24 dan tidak pernah
  memperbarui skema secara otomatis.
- **Mitigasi Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): image amd64
  menyertakan Temurin JRE yang telah diperbaiki dan menonaktifkan intrinsik Base64; mesin memberikan peringatan saat
  berjalan pada JVM yang terdampak.
- **Performa**: ukuran pemuatan pertama UI web baru turun dari 960 KB menjadi 330 KB (gzip aktif secara
  default); throughput job executor meningkat sekitar 2.5x dengan penyesuaian (angka dan konfigurasi pengujian di
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Platform terkini**: Java 17 atau yang lebih baru, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## Instalasi

<a id="one-command-on-your-existing-camunda-724-database"></a>

### Satu perintah, pada basis data Camunda 7.24 yang sudah ada

Image menerima variabel `DB_*` dari image Camunda 7 Run sebelumnya. Cadangkan basis data,
lalu:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.1
```

Pengguna, kata sandi, grup, tenant, otorisasi, filter, deployment, instans yang sedang berjalan,
riwayat, job, dan insiden tetap seperti semula. Port hanya terikat ke mesin ini; untuk akses
jarak jauh, gunakan reverse proxy dengan HTTPS. Masuk di `/fasti/` menggunakan akun yang sudah ada.
Karena autentikasi aktif, setiap klien REST (worker tugas eksternal, integrasi) harus mengirimkan
kredensial Basic atau token bearer.

- **Berjalan berdampingan** dengan node Camunda 7.24 CE pada basis data yang sama: tingkat riwayat yang sama,
  `TZ` yang sama, pengaturan kata sandi yang sama. Jika node lama merupakan aplikasi proses dengan kelas Java yang
  tidak tersedia di Fasti, atur `FASTI_JOB_EXECUTOR_ENABLED=false` pada Fasti (atau aktifkan
  kesadaran deployment pada setiap node), dan jangan memulai proses tersebut atau menyelesaikan tugasnya melalui Fasti:
  panggilan sinkron menjalankan delegate pada node yang menerimanya.
- **Rollback**: Fasti tidak mengubah skema, jadi hentikan Fasti dan jalankan kembali node Camunda 7.24
  pada basis data yang sama. Proses yang telah di-deploy dan menggunakan fitur khusus Fasti tidak akan berjalan pada
  Camunda 7; lihat [Rollback ke Camunda 7.24](docs/migrate-from-camunda-7.id.md#rollback-to-camunda-724).
- **Skema lama (7.17–7.23)**: jalankan skrip `sql/upgrade` yang disertakan dalam image
  (`/fasti/configuration/sql/upgrade`) hingga 7.24 terlebih dahulu; misalnya 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Telah diuji dengan image (mengambil alih basis data yang dibuat oleh Camunda Run 7.24.0, melakukan
rollback, berjalan berdampingan): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (driver Oracle tidak disertakan; tambahkan `ojdbc11` sendiri; rollback belum diuji
pada MySQL). **DB2 belum
diverifikasi.** H2 hanya untuk evaluasi. Hasil setiap pengujian dan batasannya:
[panduan migrasi](docs/migrate-from-camunda-7.id.md#limits).

Panduan terperinci:

- [Instalasi dengan Docker](docs/install-docker.id.md): H2, PostgreSQL dengan `docker run` dan
  `docker compose`, basis data yang sudah ada, mode berdampingan, pemeriksaan kesehatan, pembaruan, rollback,
  pemecahan masalah.
- [Beralih dari Camunda 7](docs/migrate-from-camunda-7.id.md): langkah-langkah, hal yang sama dan yang
  berbeda, rollback, batasan.
- [Referensi variabel lingkungan untuk image](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Tanpa Docker: paket Tomcat (unduh dari fasti.dev)

Paket Tomcat 1.0.0 siap pakai (halaman unduhan: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Verifikasi checksum sebelum mengekstrak, misalnya `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

Distribusi Tomcat juga dapat dibangun dari kode sumber (`distro/tomcat`). Baca `README.txt` dalam
paket sebelum menjalankannya: REST API (`/engine-rest`) mewajibkan autentikasi HTTP Basic
secara default, tidak ada pengguna yang dibuat (buat administrator pertama pada halaman penyiapan di `/fasti/`),
dan saat mengambil alih basis data Camunda Run, atur `jobExecutorDeploymentAware=false` dalam
`conf/bpm-platform.xml`; jika tidak, timer dan job asinkron dari deployment yang sudah ada tidak akan berjalan.

<a id="a-ui-built-from-real-user-complaints"></a>

## UI yang dibangun berdasarkan keluhan nyata pengguna

Fitur UI web baru dipilih berdasarkan hal-hal yang ditulis pengguna Camunda 7 secara publik di
forum Camunda dan GitHub, misalnya:

| Permintaan pengguna | Sumber | Di Fasti |
|---|---|---|
| Mencari instans berdasarkan beberapa variabel, dengan OR | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Penyusun kondisi multivariabel, AND/OR |
| Melihat komentar pada setiap langkah kasus | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Linimasa kasus di dalam tugas; komentar pada instans proses itu sendiri |
| Mengurutkan tugas berdasarkan variabel, yang terlama terlebih dahulu | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Pengurutan berdasarkan variabel, tenggat waktu, dan urgensi |
| Delegasi, pengganti selama ketidakhadiran | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Delegasi per tugas tersedia; pengganti saat tidak di kantor masuk dalam [peta jalan](#roadmap-plan) |
| Kunci bisnis dan variabel saat memulai proses tanpa formulir | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Ya |
| Mengunduh BPMN XML yang telah di-deploy | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Ya |
| Label yang konsisten, beberapa bahasa | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 bahasa |

<a id="comparison"></a>

## Perbandingan

Perbandingan ringkas berdasarkan dokumentasi publik setiap proyek (dibaca pada November 2026);
"belum diverifikasi" berarti kami tidak menemukan sumber yang dapat diandalkan. Setiap proyek memiliki arah sendiri dan merupakan
pilihan yang baik untuk kebutuhan yang berbeda; periksa dokumentasinya sebelum memutuskan.

| | Lisensi | Berjalan pada DB C7 yang sudah ada | Migrasi diperlukan | Patch keamanan | Java / Spring Boot | UI | SSO | Image Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Ya, skema 7.24 tidak berubah | Image: tidak ada. Aplikasi tertanam: koordinat Maven baru, Spring Boot 4.1 dan Java 17+; paket `org.camunda.*` dipertahankan | Ya, lihat [kebijakan di bawah](#commitments-support-and-security-patches) | 17+ / 4.1 | Baru (React) + klasik (usang) | OIDC bawaan | Ya, mulai 1.0.0 |
| Camunda 7 CE 7.24 | Kode sumber Apache-2.0; beberapa komponen web yang disertakan berlisensi MIT ([13]) | (versi asal) | — | Tidak ada rilis CE setelah 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 melalui security starter | Image Run sebelumnya |
| Camunda 7 Enterprise | Komersial | Ya (lini produk yang sama) | Tidak | Hingga 04/2030, perpanjangan berbayar hingga 04/2032 ([3]) | belum diverifikasi | Cockpit Enterprise | belum diverifikasi | belum diverifikasi |
| Camunda 8 | Camunda License 1.0; produksi yang dikelola sendiri memerlukan lisensi sejak 8.6 ([4]) | Tidak (mesin berbeda) | Ya (model, instans, data) | Ya (vendor) | mesin berbeda, tidak dapat dibandingkan | Operate, Tasklist | belum diverifikasi | belum diverifikasi |
| Operaton | Apache-2.0 | Struktur tabel sama dengan 7.24; 2.2 mencatat versi skema 7.25 melalui skrip pembaruan ([5]) | Paket diganti namanya menjadi `org.operaton.*` (resep OpenRewrite tersedia); skrip versi skema (2.2) | Ya, lini 2.2.x ([6]) | 17+ / 4.1 ([5]) | Klasik + webapps-neo (pratinjau) | belum diverifikasi | Ya ([7]) |
| CIB seven | Apache-2.0; EE memerlukan kunci lisensi | Ya (perubahan 7.24 telah digabungkan) | Paket diganti namanya menjadi `org.cibseven.*`, prefiks `cibseven.*` | Ya, per rilis ([8]) | 17, 21 / 3.5 dan 4.0 ([9]) | Klien web Vue 3 | OAuth2/JWT untuk REST ([8]) | Ya ([8]) |
| OrqueIO | Apache-2.0 | Kompatibel dengan tabel C7 menurut dokumentasinya ([10]) | Grup Maven `io.orqueio`; penggantian nama paket tidak disebutkan | Ya, menurut dokumentasinya ([10]) | 21 / 3.5, 4.0, 4.1 | Ditulis ulang dengan Angular | OIDC multipenyedia | belum diverifikasi |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | Proyek menyatakan skemanya tetap kompatibel dengan Camunda 7 (belum diverifikasi oleh Fasti) ([14]) | Paket diganti namanya menjadi `org.eximeebpms.*`, Model API diganti namanya | Ya, dua rilis minor terbaru ([11]) | 21 / versi Spring Boot tidak disebutkan | Monitor terpisah | belum diverifikasi | Ya (repositori `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | belum diverifikasi | belum diverifikasi | belum diverifikasi | 21 (image Corretto) / belum diverifikasi | Control Center | belum diverifikasi | Ya ([12]) |

Kelemahan Fasti sendiri, secara terus terang: proyek yang masih muda dengan tim kecil, belum ada dukungan
komersial, DB2 belum diverifikasi.

Sumber: [1] [Pengumuman akhir masa pakai CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [Lingkungan yang didukung 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Perpanjangan Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Lisensi Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [Lingkungan yang didukung CIB seven](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [Dokumentasi OrqueIO](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Lisensi Camunda 7](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
tinjauan independen: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## Linimasa

<a id="camunda-7-milestones"></a>

### Tonggak Camunda 7

| Tanggal | Tonggak | Sumber |
|---|---|---|
| 2024-03-06 | Camunda mengumumkan akhir masa pakai Camunda 7 CE pada Oktober 2025, dengan 7.24 sebagai rilis CE terakhir | [Forum Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda memperpanjang dukungan Camunda 7 Enterprise hingga 2030-04-09, dengan dukungan tambahan berbayar hingga April 2032 | [Blog Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Tag `7.24.0` dibuat dan dipublikasikan ke Maven Central: rilis CE terakhir | [tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Tanggal rilis 7.24 sesuai jadwal Camunda; setelah tanggal ini tidak ada lagi rilis CE, termasuk patch keamanan. Repositori CE sejak itu telah diarsipkan | [pengumuman](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [halaman dukungan](https://docs.camunda.org/enterprise/announcement) |
| April 2030 | Akhir dukungan Camunda 7 Enterprise (7.24 LTS: 2030-04-13) | [halaman dukungan](https://docs.camunda.org/enterprise/announcement) |
| April 2032 | Akhir dukungan tambahan berbayar untuk Camunda 7 Enterprise | [halaman dukungan](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti melanjutkan langsung dari tag `7.24.0`; setiap perbaikan dan perubahan dihitung dari titik tersebut
(`git log 7.24.0..`). Tahapan di bawah diurutkan berdasarkan pekerjaan yang telah dilakukan, tanpa tanggal:

1. **Fondasi**: Java 17, Spring Boot 4.1, pembaruan pustaka; koordinat Maven
   `dev.fasti.platform`.
2. **Keamanan**: perbaikan untuk masalah lini 7.x yang telah diungkapkan secara publik, daftar izin ekspresi,
   pemeriksaan lampiran/komentar di dalam mesin, penguatan aplikasi web.
3. **UI web baru** di `/fasti/`, secara bertahap menggantikan Cockpit, Tasklist, dan Admin klasik.
4. **Single sign-on** (OIDC) untuk Run, Spring Boot, Tomcat, dan WildFly.
5. **Penggantian langsung pada basis data yang sudah ada**: image menerima variabel `DB_*` sebelumnya dan memeriksa versi skema; telah diuji pada lima sistem basis data.
6. **Tinjauan keamanan prarilis** (tinjauan kode, uji penetrasi image, pemindaian dasar ZAP;
   temuan telah diperbaiki) dan **mitigasi Java4Shell**.
7. **1.0.0**: rilis pertama (2026-11-20).

<a id="roadmap-plan"></a>

## Peta jalan (rencana)

> Ini adalah **rencana**, bukan komitmen tanggal. Cakupan dan urutannya dapat berubah.

### November dan Desember 2026 (sedang berjalan)

Pertama, semua yang sudah diminta pengguna.

**1.x: Lebih mudah dijalankan**

- Artefak Maven di Maven Central.
- Helm chart dan contoh manifest Kubernetes.
- Metrik Prometheus dan pelacakan OpenTelemetry.
- Log JSON terstruktur.
- Pembaruan MyBatis, yang sekaligus memperbarui OGNL.
- Verifikasi DB2 dan tinjauan terjemahan oleh penutur asli.

**2.0: Yang diminta tim di sektor yang diregulasi**

- Pengganti selama ketidakhadiran, agar pekerjaan tidak menunggu orang yang sedang cuti
  ([forum 2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199)).
- Kontrol empat mata untuk persetujuan.
- Jejak audit yang tidak dapat diubah, ditandatangani atau di-hash, beserta laporan audit.
- CMMN di antarmuka baru dan penghentian aplikasi AngularJS.

**Ranah baru**

- Lini dukungan jangka panjang.
- Server MCP untuk mesin.
- Langkah AI dalam proses.

### Target 2027

- **Kuartal I 2027: Berjalan dalam skala besar**: operator Kubernetes untuk pembaruan bergilir dan
  penskalaan; riwayat dialirkan ke Kafka dan OpenSearch agar basis data runtime tetap kecil;
  benchmark kinerja yang dipublikasikan, diulang untuk setiap rilis.
- **Kuartal II 2027: Perangkat bagi pengembang**: SDK konektor, dengan konektor untuk REST, Kafka,
  email, dan object storage; kit pengujian proses dengan uji skenario dan laporan cakupan; pembuat
  formulir dengan komponen yang dapat digunakan ulang.
- **Kuartal III 2027: Melihat kinerja proses**: analitik proses (heatmap, hambatan, dan laporan
  durasi); target tingkat layanan dengan peringatan; asisten yang menyusun draf BPMN dari teks dan
  menjelaskan insiden.
- **Kuartal IV 2027: Kepercayaan di skala perusahaan**: administrasi multi-tenant dengan kuota untuk
  setiap tenant; audit keamanan independen, dengan laporan yang dipublikasikan; build yang dapat
  direproduksi dengan provenans bertanda tangan untuk setiap rilis.

<a id="commitments-support-and-security-patches"></a>

## Komitmen: dukungan dan patch keamanan

> Kebijakan ini dikelola oleh tim proyek Fasti BPM dan dapat diperbarui; versi dalam
> repositori ini adalah versi yang berlaku.

- **Lini yang didukung:** rilis minor terbaru dari lini `1.x` menerima perbaikan bug dan patch
  keamanan (sebagai rilis patch, misalnya `1.0.1`).
- **Patch keamanan:** kerentanan pada Fasti dan pustaka yang disertakannya diprioritaskan di atas
  pekerjaan lain; image dibangun ulang ketika image dasar atau JRE mendapatkan perbaikan. Proyek mengikuti
  advisori keamanan publik tentang Camunda 7 dan proyek turunannya, serta memeriksa apakah Fasti terdampak.
- **Kompatibilitas:** lini `1.x` mempertahankan skema basis data 7.24 dan paket `org.camunda.bpm.*`.
  Perubahan yang merusak kompatibilitas ditandai **BREAKING** dalam [CHANGELOG](CHANGELOG.md).
- **Tingkat dukungan:** ini adalah proyek komunitas; dukungan diberikan semaksimal mungkin, **tanpa SLA**,
  dan belum ada dukungan komersial.
- **Pelaporan kerentanan:** mohon **jangan** membuka isu publik; lihat
  [SECURITY.md](SECURITY.md#security-policy) (pelaporan privat melalui GitHub).
- **Cacat yang diperbaiki:** bagian *Diperbaiki* dan *Keamanan* dalam [CHANGELOG](CHANGELOG.md) mencantumkan
  hal-hal yang telah diperbaiki dibandingkan dengan Camunda 7 CE 7.24.0; advisori dicantumkan dalam
  [SECURITY.md](SECURITY.md#advisories).

<a id="documentation"></a>

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [docs/install-docker.md](docs/install-docker.id.md) | Instalasi dengan Docker, langkah demi langkah |
| [docs/api/README.md](docs/api/README.md) | Integrasi REST API, klien tugas eksternal Java dan JavaScript |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.id.md) | Beralih dari Camunda 7, rollback, batasan |
| [distro/docker/README.md](distro/docker/README.md) | Image: variabel lingkungan, driver, operasi, penyesuaian |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | UI web baru |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell dan Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Perubahan, keamanan, kontribusi |

<a id="what-this-is"></a>

## Tentang proyek ini

Repositori ini berisi mesin proses BPMN 2.0 / DMN, REST API, aplikasi
web (Cockpit, Tasklist, Admin), dan distribusi (Run, Tomcat, WildFly). Proyek ini
berawal dari tag `7.24.0` Camunda 7 CE dan dilanjutkan dengan perbaikan bug serta pembaruan
pustaka.

Tujuannya adalah **kompatibilitas mundur** dengan aplikasi yang berjalan pada Camunda 7:

- paket `org.camunda.bpm.*`, namespace BPMN
  `http://camunda.org/schema/1.0/bpmn`, dan prefiks konfigurasi `camunda.bpm.*` dipertahankan;
- skema basis data 7.24 dipertahankan;
- semua varian deployment dipertahankan: Spring Boot, Run, Tomcat, WildFly; `javax` dan `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Perbedaan dari Camunda 7.24.0 CE

- **Keamanan:** perbaikan untuk masalah lini 7.x yang telah diungkapkan secara publik (ekspresi dalam
  kueri, penyiapan awal, akses ke lampiran dan komentar, izin Cockpit, pengalihan
  terbuka) serta perlindungan tambahan: daftar izin untuk ekspresi dalam kueri dan
  filter, pemeriksaan lampiran dan komentar di dalam mesin, filter keamanan yang diperkuat,
  header dan cookie pada aplikasi web.
- **Stabilitas:** tidak ada lagi kebocoran memori saat skrip dan ekspresi DMN dievaluasi dengan
  JavaScript (GraalJS), serta sejumlah perbaikan mesin lainnya.
- **Platform:** Java 17 atau yang lebih baru, Spring Boot 4.1, GraalJS 25, Jackson 2.21, dan banyak pustaka
  lain yang diperbarui ke rilis dengan perbaikan keamanan.

Beberapa perbaikan mengubah pengaturan default agar perilakunya lebih aman; baca "Hal yang berbeda" dalam
[panduan migrasi](docs/migrate-from-camunda-7.id.md#what-differs) sebelum memperbarui.

<a id="versioning"></a>

## Penomoran versi

Fasti BPM Platform mengikuti [Penomoran Versi Semantik](https://semver.org/), dimulai dari `1.0.0`.
Lini `1.x` kompatibel dengan aplikasi yang ditulis untuk Camunda 7.24.

<a id="maven-coordinates"></a>

## Koordinat Maven

Artefak Fasti BPM Platform memiliki koordinat Maven sendiri. **Paket Java
tetap `org.camunda.bpm.*`**, sehingga kode aplikasi mempertahankan impor yang ada; prefiks konfigurasi `camunda.bpm.*`,
namespace BPMN/DMN, skema basis data, dan nama modul pada
WildFly juga tidak berubah. Dari sisi Maven, hanya deklarasi dependensi yang
berubah (path aplikasi web berubah secara terpisah, lihat
[Path aplikasi web](#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; grup `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin`, dan `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** prefiks `camunda-` menjadi `fasti-`, sisanya dipertahankan.
- **version:** versi Fasti BPM Platform (lihat [Penomoran versi](#versioning)) menggantikan
  `7.24.0`.

Belum ada rilis artefak ini yang dipublikasikan ke Maven Central. Untuk sementara, bangun
dari kode sumber dan sediakan artefak di repositori Maven yang digunakan proyek Anda.

Kelola versi dengan BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Artefak yang sering digunakan:

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

Pustaka FEEL `org.camunda.feel:feel-engine` merupakan pustaka eksternal yang bukan bagian dari
repositori ini; koordinatnya tetap seperti semula.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Pustaka pihak ketiga yang masih menarik mesin asli

Karena paket tidak berubah, mesin asli (`org.camunda.bpm:camunda-engine`)
dan `fasti-engine` berisi kelas dengan nama yang sama. Maven memperlakukannya sebagai dua pustaka
yang tidak terkait dan tidak menghapus salah satunya. Jika pustaka pihak ketiga (ekstensi komunitas,
misalnya) bergantung pada artefak asli, kecualikan artefak tersebut dan deklarasikan
artefak Fasti yang sesuai sebagai gantinya:

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` mencantumkan artefak asli yang masih
ada (`org.camunda.feel:feel-engine` memang diperlukan, jangan mengecualikannya).

<a id="startup-check"></a>

### Pemeriksaan saat startup

Saat dibuat, mesin memeriksa classpath dan **menolak untuk berjalan** jika menemukan
kelas mesin proses di lebih dari satu pustaka; kesalahan `ENGINE-12021` mencantumkan
lokasi masing-masing. Spring Boot starter melakukan pemeriksaan yang sama segera setelah
aplikasi dimulai. Pustaka yang hanya terlihat melalui class loader induk (misalnya mesin
bersama pada server yang berdampingan dengan mesin tertanam dalam aplikasi web) tidak
dihitung. Jika konfigurasi Anda memang disengaja, pemeriksaan dapat dinonaktifkan dengan properti sistem:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Batasan: jika pustaka mesin asli mendahului `fasti-engine` pada classpath,
mesin dibangun oleh kelas dari pustaka asli, yang tidak menyertakan pemeriksaan ini.
Dalam kasus tersebut, hanya aplikasi yang menggunakan Fasti Spring Boot starter yang dihentikan; untuk
integrasi lain, tinjau dependensi seperti dijelaskan di atas.

<a id="web-application-path-fasti"></a>

## Path aplikasi web: `/fasti`

Cockpit, Tasklist, Admin, dan UI web baru disajikan di bawah `/fasti` (sebelumnya `/camunda`)
oleh setiap distribusi: `http://localhost:8080/fasti/` membuka UI baru
(`/fasti/app/next/default/`), aplikasi klasik berada di `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...`, dan `/fasti/app/admin/...`. REST API tetap berada di `/engine-rest`.

**Aplikasi klasik telah dinyatakan usang.** Cockpit, Tasklist, Admin, dan Welcome klasik
dibangun menggunakan AngularJS 1.8.2 dan Bootstrap 3, yang keduanya telah mencapai akhir masa pakai. **Image container tidak
menyajikannya secara default** (perubahan yang merusak kompatibilitas): halaman dan bookmark lamanya dialihkan ke bagian yang sesuai pada UI baru, dan permintaan berkasnya mengembalikan `404`; `FASTI_CLASSIC_WEBAPPS=true` mengaktifkannya
kembali (dengan peringatan), lihat [distro/docker/README.md](distro/docker/README.md). Paket
Tomcat dan WildFly juga tidak menyajikannya secara default (aktifkan kembali dengan
`-Dfasti.webapp.classic.enabled=true` atau `FASTI_CLASSIC_WEBAPPS=true`). Zip Run dan Spring
Boot starter tetap menyajikannya seperti sebelumnya, agar instalasi yang sedang berjalan tidak terganggu; nonaktifkan
dengan `fasti.webapp.classic.enabled: false`. Aplikasi klasik akan dihapus dalam
rilis mendatang.

Path lama tetap berfungsi: setiap permintaan ke `/camunda/...` dialihkan ke path yang sama
di bawah `/fasti/...`, dengan mempertahankan sisa path dan string kueri. GET dan HEAD mendapatkan
`301`; semua metode lain (POST, PUT, DELETE, misalnya klien lama yang memanggil `/camunda/api/...`)
mendapatkan `308`, sehingga klien mengulangi permintaan dengan metode dan isi yang sama. Pengalihan
menyertakan `Cache-Control: no-store`, sehingga peralihan kembali ke `/camunda` langsung berlaku.

Untuk memulihkan `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (tanpa pengalihan).
  Untuk menonaktifkan pengalihan saja: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** hapus `webapps/camunda` (aplikasi pengalihan) dan ubah nama
  `webapps/fasti` menjadi `webapps/camunda`.
- **WildFly:** hapus `standalone/deployments/camunda.war` (dan `camunda.war.dodeploy`) lalu
  atur `<context-root>/camunda</context-root>` dalam `WEB-INF/jboss-web.xml` pada WAR
  aplikasi web.

**Reverse proxy:** jika proxy hanya meneruskan `/camunda/` (atau menulis ulang path, mengatur path
cookie, CSP, atau pemeriksaan kesehatan untuk `/camunda`), tambahkan `/fasti/` atau beralih sepenuhnya ke `/fasti/`.
Cookie `XSRF-TOKEN` (dan `JSESSIONID` pada Tomcat dan WildFly) kini memiliki path `/fasti`,
sehingga pengguna perlu masuk kembali. Bookmark dan tautan lama tetap berfungsi melalui pengalihan.

<a id="single-sign-on-openid-connect"></a>

## Single sign-on (OpenID Connect)

Didukung pada **Run** (`./start.sh --oauth2`, modul `internal/oauth2`) dan aplikasi **Spring Boot**
dengan `fasti-bpm-spring-boot-starter-security`. Fitur ini diaktifkan dengan mendeklarasikan
registrasi klien Spring Security (`spring.security.oauth2.client.*`). Untuk **Tomcat** dan
**WildFly**, lihat bagian akhir bagian ini.

- **Masuk:** halaman masuk aplikasi web baru menampilkan "Masuk dengan &lt;provider&gt;"
  sebagai pengganti formulir kata sandi (label: `fasti.security.oidc.login-label`, secara default menggunakan
  `client-name`; jika ada beberapa registrasi, pilih salah satu dengan
  `fasti.security.oidc.registration-id`). Alur kode otorisasi menggunakan `state`, `nonce`,
  dan PKCE (S256, juga untuk klien rahasia); ID sesi diganti saat masuk. Pengguna
  kembali ke halaman aplikasi web baru tempat mereka memulai (hanya path di bawah
  `/fasti/app/next/` yang diterima, sehingga tidak ada pengalihan terbuka).
- **Keluar:** `POST /logout` (hanya permintaan dari origin yang sama) mengakhiri sesi; dengan
  `camunda.bpm.oauth2.sso-logout.enabled=true`, pengguna melanjutkan ke endpoint pengakhiran
  sesi penyedia (logout yang dimulai RP, `post-logout-redirect-uri`). Logout back-channel Spring Security
  tetap aktif. `GET /logout` tidak lagi mengeluarkan pengguna.
- **Kedaluwarsa sesi:** ketika token akses tidak dapat diperbarui atau sesi habis waktu,
  permintaan ke `/fasti/api/**` mengembalikan `401` (tanpa pengalihan) dan aplikasi web baru menampilkan halaman masuknya.
- **CSRF:** `/fasti/api` dan `/fasti/app` tetap menggunakan filter CSRF milik aplikasi web
  (`X-XSRF-TOKEN`); perlindungan CSRF Spring Security tetap dinonaktifkan, seperti pada upstream.
- **Grup, administrator, tenant:** klaim grup adalah
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nama properti upstream; klaim
  bertingkat seperti `realm_access.roles` berfungsi). `fasti.security.oidc.groups.admin` mencantumkan
  nilai klaim yang memberikan `camunda-admin`; `groups.mapping` memetakan nilai klaim ke ID grup
  mesin; `groups.include-unmapped=false` membuang nilai yang tidak dipetakan. **Perubahan dari upstream:** nilai
  klaim `camunda-admin` tidak lagi memberikan hak administrator dengan sendirinya, dan
  otoritas seperti `SCOPE_openid` atau `OIDC_USER` tidak lagi menjadi grup.
  `fasti.security.oidc.tenants.claim` memetakan tenant. Nama dan email:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Penyedia identitas:** secara default, mesin membaca pengguna, grup, dan tenant dari
  token sesi saat ini (hanya baca). Atur
  `camunda.bpm.oauth2.identity-provider.enabled=false` untuk menggunakan basis data sebagai gantinya (grup
  kemudian berasal dari tabel keanggotaan). Penyediaan otomatis belum diimplementasikan.
  Selama pengguna berasal dari token (default), halaman penyiapan awal (administrator pertama)
  dinonaktifkan, karena administrator berasal dari klaim (`groups.admin`); sebelumnya, pengguna di luar grup administrator diarahkan ke halaman penyiapan.
- **REST API dengan token bearer:** `fasti.security.oidc.rest.bearer-enabled=true` menerima
  `Authorization: Bearer <JWT>` pada `/engine-rest` selain autentikasi Basic.
  `rest.issuer-uri` dan `rest.audiences` wajib diisi; tanda tangan diperiksa terhadap
  JWKS penerbit hanya dengan algoritme asimetris (`rest.jws-algorithms`, default `RS256`), dan
  `iss`, `aud`, `exp`, `nbf` divalidasi. Run juga memerlukan `camunda.bpm.run.auth.enabled=true`.
  Cookie sesi browser tidak pernah digunakan untuk mengautentikasi `/engine-rest`.

Contoh Keycloak (`configuration/default.yml` pada Run):

```yaml
spring.security.oauth2.client:
  registration.keycloak:
    client-id: fasti
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    client-name: Keycloak
    scope: openid, profile, email
  provider.keycloak:
    issuer-uri: https://sso.example.com/realms/fasti
    user-name-attribute: preferred_username
camunda.bpm:
  oauth2:
    # the client's "Group Membership" mapper, with "Full group path" off
    identity-provider.group-name-attribute: groups
    sso-logout:
      enabled: true
      post-logout-redirect-uri: "{baseUrl}/fasti/app/next/default/"
  run.auth.enabled: true
fasti.security.oidc:
  groups.admin: [fasti-admins]
  rest:
    bearer-enabled: true
    issuer-uri: https://sso.example.com/realms/fasti
    audiences: [fasti-api]          # "Audience" mapper in Keycloak
    user-id-claim: preferred_username
```

Contoh Microsoft Entra ID:

```yaml
spring.security.oauth2.client:
  registration.entra:
    client-id: <application-id>
    client-secret: ${ENTRA_CLIENT_SECRET}
    client-name: Microsoft
    scope: openid, profile, email
  provider.entra:
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    user-name-attribute: preferred_username
camunda.bpm.oauth2:
  # app registration manifest: "groupMembershipClaims": "SecurityGroup"
  identity-provider.group-name-attribute: groups
  sso-logout.enabled: true
fasti.security.oidc:
  groups:
    admin: ["<object id of the administrators group>"]
    mapping:
      "<object id of the accounting group>": accounting
    include-unmapped: false          # Entra sends object ids, not group names
  rest:
    bearer-enabled: true
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    audiences: ["api://<application-id>"]   # v2 access tokens: accessTokenAcceptedVersion = 2
    user-id-claim: preferred_username
```

**Tomcat, WildFly (WAR):** fitur yang sama, dikonfigurasi dalam `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; `fasti-oidc.properties.example` dengan anotasi
berada di sebelahnya). Tanpa berkas tersebut, tidak ada yang berubah. Kuncinya sesuai dengan properti `fasti.security.oidc.*` pada Run, ditambah `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`.
Penyedia harus mengizinkan URI pengalihan `<base>/fasti/sso/callback`; URL logout back-channel adalah
`<base>/fasti/sso/backchannel-logout`. Token bearer pada `/engine-rest` diaktifkan dengan
`fasti.security.oidc.rest.bearer-enabled=true` (autentikasi Basic tetap berfungsi kecuali
`rest.basic-enabled=false`; daftar mesin `/engine` juga memerlukan kredensial). Pengguna tidak harus
ada dalam basis data. Agar mesin dapat menjawab kueri tentang grup dan tenant
pengguna yang masuk berdasarkan klaim, aktifkan `OidcIdentityProviderPlugin` (dinonaktifkan melalui komentar dalam
`bpm-platform.xml` / `standalone.xml`). Batasan: sesi yang dilacak untuk logout back-channel
disimpan dalam memori per server (tidak dibagikan antar-node klaster); aplikasi klasik hanya mengalihkan ke penyedia; logout lengkap memerlukan UI web baru.

<a id="build"></a>

## Build

Memerlukan JDK 17 atau yang lebih baru (21 direkomendasikan).

```bash
./mvnw clean verify
```

Build lengkap menjalankan semua pengujian Java dan pengujian unit UI web (hampir satu jam; pengujian
end-to-end Playwright dijalankan secara terpisah, lihat [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) dan memerlukan akses jaringan ke Maven Central,
nodejs.org, serta registri npm. Lihat [TESTING.md](TESTING.md).

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
