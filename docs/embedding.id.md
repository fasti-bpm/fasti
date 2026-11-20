[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | Bahasa Indonesia | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

# Menyematkan Fasti BPM: kompatibilitas dan koordinat Maven

Apa yang dipertahankan Fasti dari Camunda 7 dan apa yang berbeda dari 7.24.0, cara penomoran versi, serta koordinat Maven untuk aplikasi yang menyematkan mesin. Untuk menjalankan Fasti sebagai server, lihat [Instalasi dengan Docker](install-docker.id.md) atau paket Tomcat di <https://fasti.dev/download>.

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
[panduan migrasi](migrate-from-camunda-7.id.md#what-differs) sebelum memperbarui.

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
[Path aplikasi web](web-ui.id.md#web-application-path-fasti)):

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
