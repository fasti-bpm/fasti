[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | Bahasa Indonesia | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> Terjemahan ini dikelola oleh komunitas dan mungkin tertinggal dari versi bahasa Inggris; versi bahasa Inggris adalah acuan resmi. Koreksi sangat diterima.

<a id="single-sign-on-openid-connect"></a>

# Single sign-on (OpenID Connect)

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
