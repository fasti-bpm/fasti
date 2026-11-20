[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | Bahasa Indonesia | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

# Antarmuka web Fasti

Mengapa antarmuka web baru memiliki fitur-fitur ini, dan di path mana aplikasi web disajikan. Tur antarmuka: <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## UI yang dibangun berdasarkan keluhan nyata pengguna

Fitur UI web baru dipilih berdasarkan hal-hal yang ditulis pengguna Camunda 7 secara publik di
forum Camunda dan GitHub, misalnya:

| Permintaan pengguna | Sumber | Di Fasti |
|---|---|---|
| Mencari instans berdasarkan beberapa variabel, dengan OR | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Penyusun kondisi multivariabel, AND/OR |
| Melihat komentar pada setiap langkah kasus | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Linimasa kasus di dalam tugas; komentar pada instans proses itu sendiri |
| Mengurutkan tugas berdasarkan variabel, yang terlama terlebih dahulu | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Pengurutan berdasarkan variabel, tenggat waktu, dan urgensi |
| Delegasi, pengganti selama ketidakhadiran | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Delegasi per tugas tersedia; pengganti saat tidak di kantor masuk dalam [peta jalan](https://fasti.dev/#timeline) |
| Kunci bisnis dan variabel saat memulai proses tanpa formulir | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Ya |
| Mengunduh BPMN XML yang telah di-deploy | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Ya |
| Label yang konsisten, beberapa bahasa | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 bahasa |

<a id="web-application-path-fasti"></a>

## Path aplikasi web: `/fasti`

Cockpit, Tasklist, Admin, dan UI web baru disajikan di bawah `/fasti` (sebelumnya `/camunda`)
oleh setiap distribusi: `http://localhost:8080/fasti/` membuka UI baru
(`/fasti/app/next/default/`), aplikasi klasik berada di `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...`, dan `/fasti/app/admin/...`. REST API tetap berada di `/engine-rest`.

**Aplikasi klasik telah dinyatakan usang.** Cockpit, Tasklist, Admin, dan Welcome klasik
dibangun menggunakan AngularJS 1.8.2 dan Bootstrap 3, yang keduanya telah mencapai akhir masa pakai. **Image container tidak
menyajikannya secara default** (perubahan yang merusak kompatibilitas): halaman dan bookmark lamanya dialihkan ke bagian yang sesuai pada UI baru, dan permintaan berkasnya mengembalikan `404`; `FASTI_CLASSIC_WEBAPPS=true` mengaktifkannya
kembali (dengan peringatan), lihat [distro/docker/README.md](../distro/docker/README.md). Paket
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
