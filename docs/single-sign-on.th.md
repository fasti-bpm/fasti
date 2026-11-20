[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | ไทย | [简体中文](single-sign-on.zh-CN.md)

> คำแปลนี้ดูแลโดยชุมชนและอาจล้าหลังฉบับภาษาอังกฤษ ฉบับภาษาอังกฤษเป็นฉบับที่ใช้อ้างอิงหลัก ยินดีรับการแก้ไข

<a id="single-sign-on-openid-connect"></a>

# การลงชื่อเข้าใช้ครั้งเดียว (OpenID Connect)

รองรับบน **Run** (`./start.sh --oauth2`, โมดูล `internal/oauth2`) และแอปพลิเคชัน **Spring Boot**
ที่ใช้ `fasti-bpm-spring-boot-starter-security` เปิดใช้งานโดยประกาศ
การลงทะเบียนไคลเอนต์ Spring Security (`spring.security.oauth2.client.*`) สำหรับ **Tomcat** และ
**WildFly** ดูท้ายส่วนนี้

- **เข้าสู่ระบบ:** หน้าเข้าสู่ระบบของเว็บแอปพลิเคชันใหม่แสดง "ลงชื่อเข้าใช้ด้วย &lt;provider&gt;"
  แทนฟอร์มรหัสผ่าน (ข้อความกำกับ: `fasti.security.oidc.login-label` ค่าเริ่มต้นคือ
  `client-name`; หากมีหลายการลงทะเบียน ให้เลือกหนึ่งรายการด้วย
  `fasti.security.oidc.registration-id`) โฟลว์ authorization code ใช้ `state`, `nonce`
  และ PKCE (S256 รวมถึงไคลเอนต์แบบ confidential); รหัสเซสชันจะถูกเปลี่ยนเมื่อเข้าสู่ระบบ ผู้ใช้
  จะกลับไปยังหน้าเว็บแอปพลิเคชันใหม่ที่เริ่มต้นมา (ยอมรับเฉพาะพาธภายใต้
  `/fasti/app/next/` จึงไม่มีการเปลี่ยนเส้นทางแบบเปิด)
- **ออกจากระบบ:** `POST /logout` (เฉพาะคำขอจากต้นทางเดียวกัน) สิ้นสุดเซสชัน; เมื่อกำหนด
  `camunda.bpm.oauth2.sso-logout.enabled=true` ผู้ใช้จะไปยังเอนด์พอยต์สิ้นสุด
  เซสชันของผู้ให้บริการต่อ (การออกจากระบบที่เริ่มโดย RP, `post-logout-redirect-uri`) การออกจากระบบแบบ
  back-channel ของ Spring Security ยังคงเปิดใช้งานอยู่ `GET /logout` จะไม่ทำให้ผู้ใช้ออกจากระบบอีกต่อไป
- **เซสชันหมดอายุ:** เมื่อไม่สามารถรีเฟรชโทเค็นการเข้าถึงได้หรือเซสชันหมดเวลา
  คำขอไปยัง `/fasti/api/**` จะได้รับ `401` (ไม่เปลี่ยนเส้นทาง) และเว็บแอปพลิเคชันใหม่จะแสดงหน้าเข้าสู่ระบบ
- **CSRF:** `/fasti/api` และ `/fasti/app` ยังคงใช้ตัวกรอง CSRF ของเว็บแอปพลิเคชันเอง
  (`X-XSRF-TOKEN`); การป้องกัน CSRF ของ Spring Security ยังคงปิดไว้เช่นเดียวกับโครงการต้นทาง
- **กลุ่ม ผู้ดูแลระบบ เทแนนต์:** เคลมกลุ่มคือ
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (ชื่อพร็อพเพอร์ตีจากโครงการต้นทาง; รองรับเคลม
  แบบซ้อน เช่น `realm_access.roles`) `fasti.security.oidc.groups.admin` ระบุ
  ค่าเคลมที่ให้สิทธิ์ `camunda-admin`; `groups.mapping` แมปค่าเคลมกับรหัสกลุ่ม
  ของเอนจิน; `groups.include-unmapped=false` ตัดค่าที่ไม่ได้แมปออก **เปลี่ยนจากโครงการต้นทาง:** ค่า
  เคลม `camunda-admin` จะไม่ให้สิทธิ์ผู้ดูแลระบบด้วยตัวเองอีกต่อไป และ
  authority เช่น `SCOPE_openid` หรือ `OIDC_USER` จะไม่กลายเป็นกลุ่มอีกต่อไป
  `fasti.security.oidc.tenants.claim` แมปเทแนนต์ ชื่อและอีเมล:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`
- **ผู้ให้บริการข้อมูลตัวตน:** โดยค่าเริ่มต้น เอนจินอ่านผู้ใช้ กลุ่ม และเทแนนต์จาก
  โทเค็นของเซสชันปัจจุบัน (อ่านอย่างเดียว) ตั้ง
  `camunda.bpm.oauth2.identity-provider.enabled=false` เพื่อใช้ฐานข้อมูลแทน (กลุ่ม
  จะมาจากตารางสมาชิก) ยังไม่ได้พัฒนาการสร้างบัญชีอัตโนมัติ
  ขณะที่ข้อมูลผู้ใช้มาจากโทเค็น (ค่าเริ่มต้น) หน้าตั้งค่าเริ่มต้น (ผู้ดูแลระบบคนแรก)
  จะถูกปิด เนื่องจากผู้ดูแลระบบมาจากเคลม (`groups.admin`); ก่อนหน้านี้ ผู้ใช้ที่อยู่นอกกลุ่มผู้ดูแลระบบจะถูกส่งไปยังหน้าตั้งค่า
- **REST API พร้อมโทเค็น bearer:** `fasti.security.oidc.rest.bearer-enabled=true` ยอมรับ
  `Authorization: Bearer <JWT>` บน `/engine-rest` เพิ่มเติมจากการยืนยันตัวตนแบบ Basic
  ต้องระบุ `rest.issuer-uri` และ `rest.audiences`; ลายเซ็นจะถูกตรวจสอบกับ
  JWKS ของผู้ออกโทเค็น โดยใช้อัลกอริทึมแบบอสมมาตรเท่านั้น (`rest.jws-algorithms` ค่าเริ่มต้น `RS256`) และ
  ตรวจสอบ `iss`, `aud`, `exp`, `nbf` สำหรับ Run ต้องกำหนด `camunda.bpm.run.auth.enabled=true` ด้วย
  คุกกี้เซสชันของเบราว์เซอร์จะไม่ใช้ยืนยันตัวตนสำหรับ `/engine-rest` ในทุกกรณี

ตัวอย่าง Keycloak (`configuration/default.yml` ของ Run):

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

ตัวอย่าง Microsoft Entra ID:

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

**Tomcat, WildFly (WAR):** มีฟีเจอร์เดียวกัน โดยกำหนดค่าใน `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; มี `fasti-oidc.properties.example` พร้อมคำอธิบาย
อยู่ข้างกัน) หากไม่มีไฟล์นี้ จะไม่มีอะไรเปลี่ยนแปลง คีย์ตรงกับพร็อพเพอร์ตี `fasti.security.oidc.*` ของ Run และมี `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim` เพิ่มเติม
ผู้ให้บริการต้องอนุญาต URI เปลี่ยนเส้นทาง `<base>/fasti/sso/callback`; URL ออกจากระบบแบบ back-channel คือ
`<base>/fasti/sso/backchannel-logout` เปิดใช้โทเค็น bearer บน `/engine-rest` ด้วย
`fasti.security.oidc.rest.bearer-enabled=true` (การยืนยันตัวตนแบบ Basic ยังใช้งานได้ เว้นแต่กำหนด
`rest.basic-enabled=false`; รายการเอนจิน `/engine` ก็ต้องใช้ข้อมูลรับรองเช่นกัน) ผู้ใช้ไม่จำเป็นต้อง
มีอยู่ในฐานข้อมูล หากต้องการให้เอนจินตอบคิวรีเกี่ยวกับกลุ่มและเทแนนต์ของ
ผู้ใช้ที่เข้าสู่ระบบจากเคลม ให้เปิดใช้ `OidcIdentityProviderPlugin` (ถูกคอมเมนต์ไว้ใน
`bpm-platform.xml` / `standalone.xml`) ข้อจำกัด: เซสชันที่ติดตามสำหรับการออกจากระบบแบบ back-channel
จะเก็บในหน่วยความจำแยกตามเซิร์ฟเวอร์ (ไม่ใช้ร่วมกันระหว่างโหนดคลัสเตอร์); แอปพลิเคชันแบบคลาสสิกทำได้เพียงเปลี่ยนเส้นทางไปยังผู้ให้บริการ; การออกจากระบบอย่างสมบูรณ์ต้องใช้เว็บ UI ใหม่
