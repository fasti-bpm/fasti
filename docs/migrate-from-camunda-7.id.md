[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | Bahasa Indonesia | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

# Beralih dari Camunda 7 ke Fasti BPM

Fasti BPM dibangun di atas Camunda 7 Community Edition 7.24.0 dan kompatibel dengan basis data Camunda 7.
Ini adalah proyek independen yang tidak berafiliasi dengan Camunda Services GmbH.

<a id="summary"></a>

## Ringkasan

Jika Anda menjalankan Camunda 7 **7.24** (Run, image kontainer, Spring Boot starter, Tomcat atau
WildFly), Fasti berjalan pada **basis data yang sama tanpa mengubah skemanya**: Fasti menggunakan skema 7.24
tanpa perubahan dan tidak menambahkan tabel, kolom, atau versi skemanya sendiri. Anda dapat mengganti
node lama, menjalankan Fasti berdampingan dengannya, dan kembali ke Camunda 7.24 (lihat
[Kembali ke Camunda 7.24](#rollback-to-camunda-724) dan basis data yang telah diuji di [Batasan](#limits)).

Basis data Camunda 7.23 dan versi sebelumnya harus terlebih dahulu ditingkatkan ke 7.24 menggunakan skrip SQL (lihat
[Versi lama](#older-versions-717723)). Fasti tidak pernah meningkatkan skema sendiri; Fasti menolak
memulai pada skema lama dan menyebutkan skrip yang perlu dijalankan.

<a id="steps-container-image"></a>

## Langkah-langkah (image kontainer)

1. **Cadangkan basis data.** Tidak ada langkah di bawah ini yang mengubah skema, tetapi pencadangan
   wajib dilakukan sebelum perubahan apa pun pada sistem produksi.
2. **Periksa versi skema:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — nilai tertinggi
   harus `7.24.x`.
3. **Catat konfigurasi saat ini** dari node lama: variabel `DB_*`, tingkat riwayat
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), autentikasi REST
   (`camunda.bpm.run.auth.enabled`), otorisasi (`camunda.bpm.authorization.enabled`),
   plugin, LDAP/SSO, zona waktu JVM, hashing kata sandi (`passwordEncryptor`, `saltGenerator`),
   prefiks tabel/skema.
4. **Tarik image** `fastidev/fasti-bpm:1.0.1` (atau bangun dari kode sumber seperti yang dijelaskan dalam
   [distro/docker/README.md](../distro/docker/README.md)).
5. **Jalankan Fasti dengan variabel `DB_*` yang sama** (dan `TZ` yang sama). Berbeda dengan Camunda Run,
   image Fasti **mewajibkan autentikasi REST dan menerapkan otorisasi secara default**: akun basis data
   tetap berfungsi, tetapi setiap klien REST harus mengirim kredensial Basic atau token bearer,
   dan pengguna di luar `camunda-admin` memerlukan pemberian hak akses di `ACT_RU_AUTHORIZATION` (lihat "Aman secara
   default" dalam [distro/docker/README.md](../distro/docker/README.md)).
   `FASTI_INSECURE_DEMO=true` hanya untuk demo. Fasti mencatat
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Periksa:** masuk ke `/fasti/` dengan akun yang sudah ada; pastikan filter, tugas, instans, dan riwayat tersedia; panggil `GET /engine-rest/task` (dengan kredensial Basic) dan `GET /actuator/health/readiness`.
7. **Alihkan lalu lintas** (penyeimbang beban, DNS) ke Fasti; hentikan node lama atau biarkan tetap berjalan (lihat
   [Operasi gabungan](#mixed-operation-with-camunda-7-nodes)).

Spring Boot starter / Tomcat / WildFly: ganti artefak `org.camunda.bpm:camunda-*` dengan
artefak `dev.fasti.platform:fasti-*` yang sesuai dan pertahankan `camunda.bpm.*`, `bpm-platform.xml` serta
paket `org.camunda.bpm.*`. Jangan menyimpan kedua JAR engine di classpath; jika demikian, Fasti menolak memulai (untuk batasan pemeriksaan ini, lihat "Pemeriksaan saat memulai" dalam
[embedding.id.md](embedding.id.md#startup-check)).

> **Spring Boot starter:** Fasti berjalan pada **Spring Boot 4.1** dan memerlukan **Java 17 atau yang lebih baru**.
> Aplikasi yang menggunakan starter Camunda 7.24 (Spring Boot 3.5) harus beralih ke Spring Boot 4.1 dan
> Java 17+ bersamaan dengan penggantian artefak. Lihat
> [embedding.id.md](embedding.id.md#what-is-different-from-camunda-7240-ce) dan
> [CHANGELOG](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Distribusi Tomcat dan WildFly

> **Wajib saat mengambil alih basis data Camunda Run** (atau server yang prosesnya
> di-deploy melalui REST API): distribusi Tomcat dan WildFly mempertahankan
> `jobExecutorDeploymentAware=true` seperti Camunda 7. Job (timer, kelanjutan asinkron, percobaan ulang)
> dari deployment yang tidak terdaftar pada node **tidak berjalan, dan tidak ada kesalahan yang dilaporkan**.
> Sebelum memulai, tetapkan `<property name="jobExecutorDeploymentAware">false</property>` dalam
> `conf/bpm-platform.xml` (Tomcat) atau pada properti engine di `standalone.xml` (WildFly).
> Pertahankan `true` jika node lain menjalankan aplikasi proses dengan kelas Java sendiri (lihat
> [Operasi gabungan](#mixed-operation-with-camunda-7-nodes)).

Dibandingkan dengan distribusi Tomcat/WildFly Camunda 7.24:

- **REST API mewajibkan autentikasi** (perubahan yang memutus kompatibilitas). Filter `camunda-auth`
  (HTTP Basic) diaktifkan pada `/engine-rest`: setiap klien REST harus mengirim nama pengguna dan kata sandi pengguna engine; jika tidak, klien menerima 401; hanya daftar engine (`/engine`) yang tetap terbuka.
  Konfigurasikan kredensial pada klien REST, worker tugas eksternal, dan skrip yang memanggil REST
  API secara anonim sebelum beralih. Token bearer OpenID Connect (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) juga berfungsi: permintaan dengan token yang valid tidak memerlukan kata sandi. Untuk menonaktifkan autentikasi (**tidak disarankan**), hapus
  filter `camunda-auth` dan `filter-mapping`-nya dari `WEB-INF/web.xml` pada WAR `engine-rest`.
- **Tidak ada pengguna default** (perubahan yang memutus kompatibilitas). Contoh invoice tidak lagi di-deploy, sehingga tidak
  membuat `demo/demo` (administrator), `john`, `mary`, dan `peter`; WAR-nya adalah
  `examples/camunda-invoice.war`, yang dapat disalin ke `webapps/` (Tomcat) atau
  `standalone/deployments/` (WildFly) untuk mencobanya. Pada basis data kosong, buka `/fasti/`
  dan buat administrator pertama di halaman penyiapan; pada basis data Camunda 7, gunakan
  akun yang sudah ada.
- **Cockpit, Tasklist, Admin, dan Welcome klasik tidak disajikan** (perubahan yang memutus kompatibilitas), seperti pada
  image: halaman dan markahnya mengarah ke bagian yang sesuai pada UI baru, sedangkan permintaan untuk berkasnya mengembalikan 404. Untuk mengaktifkannya kembali sementara (sudah usang, akan dihapus), gunakan salah satu pengaturan berikut:
  `-Dfasti.webapp.classic.enabled=true` dalam `CATALINA_OPTS` (Tomcat) atau `JAVA_OPTS` (WildFly),
  variabel lingkungan `FASTI_CLASSIC_WEBAPPS=true`, atau parameter konteks
  `fasti.webapp.classic.enabled` dalam `WEB-INF/web.xml` pada aplikasi web.

<a id="what-is-identical"></a>

## Apa yang tetap sama

- Skema dan baris pada `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti tidak menambahkan baris versi),
  ID instalasi, pembuatan ID (`StrongUuidGenerator` pada Run/Spring Boot,
  `DbIdGenerator` default engine).
- Kata sandi: SHA-512 dengan salt 16 byte seperti Camunda 7; pengguna yang sudah ada dapat masuk, dan
  kata sandi yang diubah pada Fasti berfungsi pada Camunda 7.
- REST API di `/engine-rest`: endpoint, parameter, dan payload yang sama, dengan tambahan endpoint
  (autentikasi dan beberapa kode status berbeda, lihat [Apa yang berbeda](#what-differs)), tugas eksternal
  (`fetchAndLock`, long polling), klien Java dan JS, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, properti `camunda.bpm.*`
  , namespace BPMN `camunda:`.
- Filter dari Tasklist klasik (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) dibaca dan ditulis dalam format yang sama oleh Tasklist baru.

<a id="what-differs"></a>

## Apa yang berbeda

- Aplikasi web berada di `/fasti/app/...`; `/camunda/...` mengalihkan ke sana (301/308). Jika proksi Anda hanya mengekspos `/camunda`, ekspos juga `/fasti` atau tetapkan `FASTI_WEBAPP_PATH=/camunda`. Path akar
  membuka aplikasi web baru (`fasti.webapp.default-app: welcome` memulihkan Welcome, kecuali
  pada image, yang menonaktifkan aplikasi klasik).
- **Perubahan yang memutus kompatibilitas, image kontainer:** Cockpit, Tasklist, Admin, dan Welcome klasik tidak
  disajikan karena AngularJS 1.8.2 dan Bootstrap 3 telah mencapai akhir masa dukungan. Halaman dan markahnya,
  termasuk yang berada di bawah `/camunda/app/`, mengalihkan ke bagian yang sesuai pada UI web baru (`#/cockpit`,
  `#/tasklist`, `#/admin`); permintaan untuk berkasnya mengembalikan 404. Alihkan pengguna ke UI web baru;
  `FASTI_CLASSIC_WEBAPPS=true` menyajikan kembali aplikasi klasik selama masa transisi, dengan
  peringatan. Tomcat dan WildFly juga tidak menyajikannya (lihat
  [Distribusi Tomcat dan WildFly](#tomcat-and-wildfly-distributions)). ZIP Run dan
  Spring Boot starter masih menyajikannya, tetapi aplikasi tersebut sudah usang dan akan dihapus pada
  rilis mendatang (`fasti.webapp.classic.enabled: false` menonaktifkannya lebih awal).
- Dengan skema lama, Fasti menolak memulai dengan `ENGINE-03111` alih-alih mengalami kegagalan kemudian
  (`FASTI_SCHEMA_VERSION_CHECK=warn` untuk tetap memulai).
- Ekspresi dalam kueri dan filter dibatasi oleh daftar yang diizinkan (fungsi tanggal, pengguna
  saat ini); filter yang menggunakan bean atau fungsi kustom memerlukan deklarasi pada properti konfigurasi engine
  `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries`, dan
  `allowedExpressionPropertiesInQueries`.
- Perilaku REST API: pada image container serta distribusi Tomcat dan WildFly, setiap permintaan
  kecuali daftar engine (`/engine`) memerlukan kredensial Basic atau bearer token, sehingga klien
  REST dan worker tugas eksternal harus dikonfigurasi untuk mengirimnya (kodenya tidak berubah).
  Di semua distribusi, ekspresi kueri di luar daftar yang diizinkan ditolak dengan `400`; membaca
  atau mengubah lampiran dan komentar tugas tanpa izin dijawab dengan `403` (saat pemeriksaan
  otorisasi aktif); beberapa permintaan tidak valid yang sebelumnya dijawab `500` kini dijawab
  `400` (sumber deployment yang tidak dapat diurai, variabel dalam format serialisasi Java yang
  dilarang, parameter kueri dengan tipe yang salah, kata sandi yang melanggar kebijakan kata
  sandi). Lihat [Differences from Camunda 7](api/README.md#differences-from-camunda-7) (bahasa Inggris).
- Pengaturan default image: pembaruan skema `false` (`FASTI_DB_SCHEMA_UPDATE=true` untuk basis data baru); tanpa
  pengguna demo, contoh invoice, atau filter "Semua tugas"; CORS nonaktif (`FASTI_CORS_ALLOWED_ORIGINS=*`
  mengaktifkannya kembali); `/actuator/health` ditambahkan; berkas berada di bawah `/fasti` (berkas yang dipasang di
  `/camunda/configuration` juga dibaca); tanpa `--swaggerui`.
- OAuth2/OIDC: `camunda-admin` dalam klaim grup tidak lagi dengan sendirinya memberikan hak administrator
  (`fasti.security.oidc.groups.admin`); `group-name-delimiter` adalah teks literal, bukan regex;
  logout memerlukan POST dari origin yang sama.
- Tasklist baru: filter tanpa `refresh` diperbarui secara otomatis.
- JAR Run adalah `fasti-bpm-run-core.jar`.
- Perbaikan keamanan dibandingkan dengan 7.24.0 (otorisasi lampiran/komentar, penyiapan awal, ...) dapat menyebabkan beberapa panggilan yang sebelumnya berhasil mengembalikan 403; lihat bagian *Security* di [CHANGELOG](../CHANGELOG.md) dan <https://fasti.dev/security>.

<a id="rollback-to-camunda-724"></a>

## Kembali ke Camunda 7.24

Hentikan Fasti dan jalankan kembali node Camunda 7.24 pada basis data yang sama. Semua yang dilakukan pada
Fasti terlihat di sana. Yang tersisa: baris `fasti.prefs.*` di `ACT_ID_INFO` (preferensi
UI web baru, diabaikan oleh Camunda 7) dan `refresh: true` pada filter yang disimpan oleh Tasklist baru jika
sebelumnya properti tersebut tidak ada. Proses yang di-deploy dengan fitur khusus Fasti tidak berjalan pada Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Operasi gabungan dengan node Camunda 7

Fasti dan Camunda 7.24 dapat berjalan pada satu basis data secara bersamaan (telah diuji: keduanya melihat
data satu sama lain dan job berjalan pada salah satu node). Persyaratan:

- **tingkat riwayat yang sama** (image menggunakan tingkat yang tersimpan), **zona waktu JVM yang sama**,
  pengaturan kata sandi dan prefiks tabel yang sama;
- **eksekutor job:** secara default Fasti (image, Run, Spring Boot) mengambil job dari setiap deployment, seperti Run.
  Distribusi Tomcat dan WildFly menggunakan `jobExecutorDeploymentAware=true` secara default (seperti Camunda
  7): job (timer, kelanjutan asinkron, percobaan ulang) dari deployment yang tidak terdaftar pada
  node tersebut, termasuk deployment yang dibuat oleh Run atau melalui REST sebelum mulai ulang terakhir, **tidak
  berjalan**; saat mengambil alih basis data Run, tetapkan ke `false` dalam `conf/bpm-platform.xml`
  (Tomcat) atau `standalone.xml` (WildFly). Jika node lama menjalankan aplikasi proses dengan kelas yang tidak dimiliki Fasti (`JavaDelegate`, listener, bean),
  Fasti akan mengambil job-nya, yang kemudian gagal dan menimbulkan insiden. Dalam hal ini, tetapkan
  `FASTI_JOB_EXECUTOR_ENABLED=false` pada Fasti, atau jadikan **setiap** node sadar deployment (eksekutor
  job suatu node hanya menjalankan job dari deployment yang terdaftar pada node tersebut; deployment yang dibuat melalui
  REST hanya terdaftar pada node yang menerimanya, hingga mulai ulang). Panggilan REST sinkron (menyelesaikan tugas,
  memulai instans) tetap menjalankan delegate pada node yang menerimanya — jangan arahkan
  proses tersebut ke Fasti;
- **penyeimbang beban:** sesi persisten untuk aplikasi web; panggilan REST dengan autentikasi Basic tidak memerlukannya;
- aplikasi web klasik (pada node Camunda 7, atau Fasti dengan
  `FASTI_CLASSIC_WEBAPPS=true`) dan aplikasi baru berbagi filter dan pengguna.

<a id="older-versions-717723"></a>

## Versi lama (7.17–7.23)

Cadangkan; jalankan `sql/upgrade/<db>_engine_<from>_to_<to>.sql` secara berurutan hingga 7.24, termasuk
skrip patch yang belum diterapkan untuk versi minor Anda saat ini (disertakan dalam `configuration/sql/upgrade`, pada
image di `/fasti/configuration/sql/upgrade`); periksa bahwa `ACT_GE_SCHEMA_LOG` berisi `7.24.0`;
jalankan Fasti. Contoh 7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, lalu
`postgres_engine_7.23_to_7.24.sql`. Node Camunda yang lebih lama dari 7.24 tidak boleh tetap berjalan pada
skema yang telah ditingkatkan, kecuali sesuai aturan pembaruan bergulir Camunda (hanya satu versi minor, dan
node lama tidak boleh menggunakan fitur baru).

<a id="performance"></a>

## Performa

Pengaturan default image (10 koneksi dalam pool, eksekutor job dengan inti 3/maksimum 10) sama dengan Camunda Run. Jika Anda telah menyesuaikan pool koneksi atau eksekutor job pada instalasi Camunda 7 sebelumnya, terapkan kembali nilai tersebut
(`FASTI_DB_CONN_MAXACTIVE`, `CAMUNDA_BPM_JOB_EXECUTION_*`); panduan dan hasil pengukuran tersedia dalam
`distro/docker/README.md`, "Penyesuaian performa". Fasti tidak menambahkan indeks pada skema.

<a id="limits"></a>

## Batasan

- Hanya skema Community Edition 7.24 yang didukung; skema patch Enterprise belum diuji.
- Diuji dengan image kontainer (Camunda Run 7.24.0 membuat dan mengisi basis data → Fasti
  mengambil alih → kembali ke Run → keduanya sekaligus):

  | Basis data | Driver | Pengambilalihan | Pengembalian | Gabungan | Peningkatan 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | disertakan | lulus | lulus | lulus | belum diuji |
  | MariaDB 11.8 | MariaDB Connector/J (disertakan) atau MySQL Connector/J | lulus | lulus | lulus | lulus |
  | MySQL 8.4 | disertakan | lulus | — | lulus | lulus |
  | SQL Server 2022 (Developer) | disertakan (`encrypt=false`) | lulus | lulus | lulus | belum diuji |
  | Oracle Database 23ai Free | `ojdbc11` melalui `FASTI_EXTRA_LIB` | lulus | lulus | lulus | belum diuji |
  | DB2 | — | belum diuji | | | |

  Selain itu: dua node Fasti pada MariaDB (MariaDB Connector/J) yang menyelesaikan tugas yang sama dan mengambil tugas eksternal secara paralel tidak kehilangan atau menduplikasi apa pun; transaksi yang kalah menerima kesalahan penguncian optimistis. MySQL/MariaDB memerlukan `transaction-isolation=READ-COMMITTED`. Pada MySQL,
  berikan basis data tersendiri untuk setiap engine: engine (baik Camunda Run maupun Fasti) mencari tabel
  di setiap basis data yang dapat dilihat akun tersebut.
- Plugin dan aplikasi proses yang dibangun untuk Camunda 7 harus dibangun ulang menggunakan artefak
  Fasti agar dapat berjalan di dalam Fasti. Klien REST dan klien tugas eksternal tidak memerlukan perubahan kode;
  dengan image container, Tomcat, atau WildFly, keduanya harus dikonfigurasi untuk mengirim kredensial Basic atau
  bearer token.
- Pemeriksaan skema hanya membaca `ACT_GE_SCHEMA_LOG`; pemeriksaan ini tidak membandingkan kolom.
