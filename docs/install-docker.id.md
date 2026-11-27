[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | Bahasa Indonesia | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

# Menginstal Fasti BPM dengan Docker

Panduan langkah demi langkah: uji coba cepat dengan H2, penggunaan produksi dengan PostgreSQL (`docker run` atau
`docker compose`), koneksi ke basis data Camunda 7 yang sudah ada, mode berdampingan, pemeriksaan
kesehatan, peningkatan versi, pengembalian versi, dan pemecahan masalah. Referensi lengkap image:
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM dibangun di atas Camunda 7 CE
7.24.0 dan merupakan proyek independen yang tidak berafiliasi dengan Camunda Services GmbH.

<a id="1-requirements"></a>

## 1. Persyaratan

- Docker Engine dengan Docker Compose v2 (`docker compose`).
- Untuk beban kerja nyata: setidaknya 2 CPU dan memori 2 GB untuk kontainer Fasti (di bawah itu,
  JVM memilih Serial GC).
- Basis data produksi: PostgreSQL, MariaDB, MySQL, SQL Server, Oracle, atau Db2 12.1 (lihat
  [matriks pengujian](migrate-from-camunda-7.id.md#limits); Db2 memerlukan tiga variabel registri).
  H2 hanya untuk evaluasi.

<a id="2-pull-the-image"></a>

## 2. Mengunduh image

<!-- update with the Docker Hub image name at release -->
Image dipublikasikan untuk amd64 dan arm64 di dua tempat:

```bash
docker pull fastidev/fasti-bpm:1.3.2              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.2     # GitHub Container Registry
```

Tag: `1.3.2` (rilis tersebut secara spesifik), `1.3` (rilis patch terbaru dari 1.3), dan `latest` (rilis yang
terakhir dipublikasikan). Image ditandatangani dengan cosign (tanpa kunci, GitHub OIDC) dan dilengkapi SBOM serta
atestasi asal-usul. Sejak 1.0.1, tanda tangan menggunakan format Sigstore bundle dari cosign 3; image 1.0.0
memiliki tanda tangan dalam format cosign 2 yang lebih lama.

Anda juga dapat membangun image dari kode sumber (memerlukan JDK 17+ dan akses jaringan ke Maven
Central, nodejs.org, dan registri npm), lalu menggunakan nama `fasti-bpm:local` sebagai pengganti
`fastidev/fasti-bpm:1.3.2` pada setiap perintah di bawah ini:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Uji coba cepat dengan H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.2
```

- Buka <http://localhost:8080/fasti/> dan masuk sebagai `admin`.
- Tanpa `DB_URL`, image menggunakan H2 di `/fasti/data`; volume `fasti-data` menyimpan data
  saat kontainer dibuat ulang. Hanya untuk evaluasi, jangan pernah digunakan untuk data nyata.
- Tambahkan `--webapps --rest --example` di akhir perintah untuk menyertakan contoh proses (faktur)
  juga. Jika hanya menggunakan `--example`, UI web dan REST API tetap dinonaktifkan.
- REST API tersedia di `http://localhost:8080/engine-rest` dan memerlukan kredensial HTTP Basic; lihat
  [panduan REST API](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Penggunaan produksi dengan PostgreSQL: `docker run`

Buat dua berkas rahasia (kata sandi basis data dan kata sandi admin). Dengan `--production`, mesin
menerapkan kebijakan kata sandinya: setidaknya 10 karakter yang mencakup huruf besar dan kecil, angka,
serta karakter khusus.

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # other users on the host cannot enter the directory
chmod 0644 secrets/*.txt     # the containers read them as uid 999 (PostgreSQL) and 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.3.2 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` hanya membuat tabel yang belum ada dalam basis data baru; Fasti tidak pernah
  meningkatkan versi skema yang sudah ada. Pengaturan ini dapat tetap digunakan pada startup berikutnya.
- `--production` mengaktifkan cookie `Secure` dan menolak basis data H2 implisit serta `DEBUG=true`.
  Lakukan terminasi TLS pada reverse proxy di depan Fasti; browser hanya mengirim cookie `Secure` melalui HTTPS (atau ke
  `localhost`).
- `WAIT_FOR` menunda startup hingga basis data menerima koneksi.
- Kata sandi admin hanya ditetapkan jika admin belum ada; ubah nanti di Admin.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Penggunaan produksi dengan PostgreSQL: `docker compose`

Berkas contoh: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, rahasia, pemeriksaan kesehatan, `--production`, batas memori 2 GB).

```bash
cd distro/docker/examples          # or copy the file to a directory of its own
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # both services "healthy"
```

Variabel yang dapat Anda tetapkan saat memulai: `FASTI_IMAGE` (nilai bawaan `fastidev/fasti-bpm:1.3.2`, misalnya
`FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (nilai bawaan `8080`), `TZ` (nilai bawaan `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Menghubungkan ke basis data Camunda 7 yang sudah ada

Fasti berjalan pada skema 7.24 tanpa mengubahnya. Sebelum memulai:

1. **Cadangkan** basis data.
2. Periksa versi skema: `select VERSION_ from ACT_GE_SCHEMA_LOG` — nilai tertinggi harus
   `7.24.x`. Skema 7.17–7.23: jalankan skrip `sql/upgrade` terlebih dahulu (disertakan dalam image di
   `/fasti/configuration/sql/upgrade`), lihat
   [panduan migrasi](migrate-from-camunda-7.id.md#older-versions-717723).
3. Periksa otorisasi, karena Fasti menerapkannya: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` dan `select count(*) from ACT_RU_AUTHORIZATION`. Jika sistem lama
   berjalan dengan otorisasi dinonaktifkan, berikan izin kepada pengguna biasa sebelum mengalihkan lalu lintas.
4. Catat `TZ`, tingkat riwayat, prefiks tabel/skema, dan pengaturan kata sandi pada
   node lama.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.2 --production
```

- Port hanya terikat ke mesin ini; untuk akses jarak jauh, gunakan reverse proxy dengan HTTPS.
- **Jangan** menetapkan `FASTI_DB_SCHEMA_UPDATE` atau `FASTI_ADMIN_*`: tabel dan akun sudah ada. Log
  startup harus memuat `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- Nilai bawaan tingkat riwayat adalah `auto`: tingkat yang tersimpan dalam basis data akan digunakan.
- Setiap klien REST kini harus mengirim kredensial Basic atau token bearer.
- Driver: PostgreSQL, MySQL, MariaDB, SQL Server, dan H2 sudah disertakan. Tambahkan sendiri driver **Oracle** dan **DB2** (`ojdbc11.jar`, `jcc.jar`) dengan `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: driver 13.x mengenkripsi koneksi dan memverifikasi sertifikat secara bawaan;
  server memerlukan sertifikat valid yang cocok dengan nama host dalam URL. `trustServerCertificate=true`
  (melewati pemeriksaan sertifikat) atau `encrypt=false` (tanpa enkripsi) hanya untuk uji coba lokal. MySQL/MariaDB memerlukan
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Berjalan berdampingan dengan node Camunda 7

Fasti dan Camunda 7.24 dapat berjalan bersamaan pada satu basis data (sudah diuji: keduanya dapat melihat data
satu sama lain). Persyaratan:

- tingkat riwayat, `TZ`, pengaturan kata sandi, dan prefiks tabel yang sama;
- jika node lama merupakan aplikasi proses dengan kelas Java (`JavaDelegate`, listener) yang tidak tersedia di Fasti, tetapkan `-e FASTI_JOB_EXECUTOR_ENABLED=false` pada Fasti atau buat **setiap** node
  mengenali deployment (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` untuk Fasti). Ini hanya melindungi
  pekerjaan asinkron: memulai instans atau menyelesaikan tugas melalui Fasti tetap menjalankan delegat
  di Fasti, jadi kirim permintaan tersebut ke node yang memiliki kelas Java terkait;
- penyeimbang beban: gunakan sesi persisten untuk aplikasi web; REST dengan kredensial Basic tidak memerlukannya.

Detail: [Operasi gabungan dengan node Camunda 7](migrate-from-camunda-7.id.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Variabel lingkungan yang umum digunakan

| Variabel | Makna |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | koneksi basis data (nama variabel dari image Camunda 7 Run sebelumnya; alias: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | menunggu `host:port` sebelum memulai |
| `TZ` | zona waktu JVM; harus sama pada setiap node |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: membuat tabel yang belum ada dalam basis data baru |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | membuat admin jika belum ada |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | eksekutor pekerjaan pada node ini |
| `FASTI_EXTRA_LIB` | direktori JAR tambahan (driver Oracle, DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | ukuran maksimum kumpulan koneksi (nilai bawaan 20; saat idle 10 tetap terbuka, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | opsi JVM, persentase memori untuk heap (75; < 1.5 GiB: 60) |

Tabel lengkap: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Pemeriksaan kesehatan

| Endpoint | Kegunaan |
|---|---|
| `/actuator/health/liveness` | probe keaktifan Kubernetes |
| `/actuator/health/readiness` | probe kesiapan, penyeimbang beban; mencakup koneksi basis data dan mesin |
| `/actuator/health` | keseluruhan |

Tidak perlu masuk; hanya endpoint kesehatan yang diekspos. `HEALTHCHECK` pada image menggunakan
kesiapan, sehingga `docker ps` menampilkan `healthy` setelah Fasti siap:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. Peningkatan dan pengembalian versi

**Meningkatkan versi Fasti** (misalnya `1.1.0` → `1.3.2`):

```bash
docker pull fastidev/fasti-bpm:1.3.2
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.2 --production   # same options as before
```

Dengan Docker Compose: ubah `FASTI_IMAGE` (atau baris `image:`), lalu
`docker compose -f docker-compose.postgres.yml up -d`. Baca [catatan perubahan](../CHANGELOG.md)
terlebih dahulu, terutama entri **BREAKING**. Seri `1.x` mempertahankan skema 7.24, sehingga peningkatan versi
dalam seri ini tidak mengubah skema. `docker stop -t 60` memberi kesempatan permintaan yang sedang berjalan untuk selesai sebelum
kontainer dihentikan.

**Pengembalian versi:**

- ke rilis Fasti sebelumnya: mulai kembali dengan tag lama;
- ke Camunda 7.24: hentikan Fasti dan mulai kembali node Camunda 7.24 pada basis data yang sama. Preferensi UI baru tetap tersimpan (`fasti.prefs.*` dalam `ACT_ID_INFO`, diabaikan oleh Camunda 7). Proses yang telah di-deploy dan menggunakan fitur khusus Fasti tidak akan berjalan di Camunda 7. Lihat
  [Pengembalian ke Camunda 7.24](migrate-from-camunda-7.id.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Pemecahan masalah

Baca log dengan `docker logs fasti` (atau `docker compose logs fasti`).

| Gejala | Penyebab dan solusi |
|---|---|
| REST API mengembalikan `401` | Fasti memerlukan autentikasi: kirim `-u user:password` atau token bearer |
| Daftar kosong atau `403` | pengguna tidak memiliki otorisasi: berikan izin yang diperlukan pada sumber daya terkait di Admin → Otorisasi. Tambahkan hanya administrator yang sebenarnya ke `camunda-admin` |
| Berhenti dengan `ENGINE-03111 The database schema has version …` | skema lebih lama dari 7.24: buat cadangan, lalu jalankan skrip `sql/upgrade` (bagian 6). Jangan melewati pemeriksaan dengan `FASTI_SCHEMA_VERSION_CHECK=warn`: penggunaan skema lama tidak didukung dan dapat menyebabkan kegagalan saat beroperasi |
| Berhenti dengan `Insecure configuration refused` | ada pengaturan yang menonaktifkan autentikasi REST atau otorisasi; hapus pengaturan tersebut (hanya `FASTI_INSECURE_DEMO=true` yang membuka akses instans, untuk demo) |
| Berhenti dengan `Password does not match policy` | `--production` menerapkan kebijakan kata sandi pada `FASTI_ADMIN_PASSWORD`: setidaknya 10 karakter yang mencakup huruf besar dan kecil, angka, serta karakter khusus |
| `password authentication failed` | kata sandi dalam berkas rahasia berbeda dari kata sandi basis data. Berkas yang dibuat di Windows mungkin diakhiri dengan `\r\n`: buat ulang dengan `tr -d '\r\n'`. PostgreSQL membaca `POSTGRES_PASSWORD_FILE` hanya saat volume diinisialisasi untuk pertama kali |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | tetapkan `FASTI_ADMIN_PASSWORD` atau `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | belum ada basis data yang dikonfigurasi; `--production` tidak beralih ke basis data H2 demo sebagai cadangan: tetapkan `DB_URL` ke basis data produksi |
| `… cannot be read` untuk variabel `_FILE` | berkas tidak dipasang ke dalam kontainer atau uid 1000 tidak dapat membacanya (berkas memerlukan mode `0644`; direktorinya dapat tetap menggunakan `0700`) |
| Login web melalui HTTP tidak mempertahankan sesi | cookie `Secure` (`--production`) hanya dikirim melalui HTTPS (atau ke `localhost`): lakukan terminasi TLS pada proxy |
| Tidak dapat terhubung ke SQL Server (kesalahan sertifikat) | berikan sertifikat valid pada SQL Server yang cocok dengan nama host dalam URL; `trustServerCertificate=true` hanya untuk uji coba lokal |
| Kontainer terus memulai ulang dan log menampilkan `OutOfMemoryError` | JVM berhenti saat memori habis: naikkan batas memori kontainer (2 GB) |

Masih mengalami kendala? Buat issue GitHub dengan menyertakan log (hapus kata sandi). Masalah keamanan: lihat
[SECURITY.md](../.github/SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Tanpa Docker

Paket Tomcat 1.3.1 siap pakai (halaman unduhan: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.3.1.zip](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip.sha256))
- [fasti-bpm-tomcat-1.3.1.tar.gz](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz.sha256))

Verifikasi checksum sebelum mengekstrak, misalnya `sha256sum -c fasti-bpm-tomcat-1.3.1.zip.sha256`.
