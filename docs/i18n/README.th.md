[English](../../README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | ไทย | [简体中文](README.zh-CN.md)

> คำแปลนี้ดูแลโดยชุมชนและอาจล้าหลังฉบับภาษาอังกฤษ ฉบับภาษาอังกฤษเป็นฉบับที่ใช้อ้างอิงหลัก ยินดีรับการแก้ไข

# Fasti BPM Platform

Fasti BPM เป็น engine แบบโอเพนซอร์สที่เป็นอิสระ สร้างบน Camunda 7 Community Edition 7.24.0
ทำงานบนฐานข้อมูล Camunda 7 ที่คุณมีอยู่แล้ว พร้อมไลบรารีรุ่นปัจจุบัน การแก้ไขด้านความปลอดภัย
และเว็บอินเทอร์เฟซใหม่ เว็บไซต์: <https://fasti.dev>

> **Fasti BPM ไม่มีความเกี่ยวข้องกับ Camunda Services GmbH; Camunda เป็นเครื่องหมายการค้าของ Camunda Services GmbH**
> Fasti BPM เป็นโครงการโอเพนซอร์สอิสระ (Apache-2.0) ไม่ได้เป็นส่วนหนึ่งของ ได้รับการสนับสนุน
> หรือได้รับการรับรองจาก Camunda Services GmbH ชื่อ "Camunda" ใช้เพื่ออธิบายที่มาของ
> ซอร์สโค้ดและสิ่งที่ Fasti ทำงานร่วมกันได้เท่านั้น

<a id="quick-start"></a>

## เริ่มต้นอย่างรวดเร็ว

อิมเมจมีให้ใช้งานบน Docker Hub (`fastidev/fasti-bpm`) และ GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`) สำหรับ amd64 และ arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.3.2
```

เปิด <http://localhost:8080/fasti/> แล้วเข้าสู่ระบบ หากไม่ได้กำหนด `DB_URL` อิมเมจจะใช้ฐานข้อมูล H2
ภายในคอนเทนเนอร์ ซึ่งเหมาะสำหรับการประเมินเท่านั้น REST API ต้องใช้ข้อมูลรับรองแบบ Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

คู่มือฉบับเต็ม รวมถึงการใช้ฐานข้อมูลของคุณเอง: <https://fasti.dev/install>

<a id="highlights"></a>

## จุดเด่น

- **เปลี่ยนการเชื่อมต่อ เท่านั้นคือการย้ายระบบ** Fasti ใช้ schema ของ Camunda 7.24 ตามเดิม และคง
  แพ็กเกจ `org.camunda.bpm.*` ไว้ ([การย้ายระบบ](https://fasti.dev/install#migrate))
- **หนึ่ง container หนึ่งคำสั่ง** โดยใช้ตัวแปร `DB_*` ชุดเดียวกับ Camunda Run ([ติดตั้ง](https://fasti.dev/install))
- **สร้างมาเพื่อคนที่จัดการคิวงาน**: เว็บอินเทอร์เฟซใหม่ รองรับ 13 ภาษา ธีมสว่างและมืด และใช้งาน
  บนโทรศัพท์ได้ ([ฟีเจอร์](https://fasti.dev/#features))
- **สร้างโมเดลและ deploy ในเบราว์เซอร์**: BPMN, DMN และฟอร์ม ([โมเดลเลอร์](https://fasti.dev/#modeler))
- **เครื่องมือสำหรับดูแลระบบมีมาให้พร้อม**: ประวัติ การซ่อม instance การลอง job ใหม่แบบกลุ่ม และ
  operation log ([การดูแลระบบ](https://fasti.dev/#operate))
- **image คอนเทนเนอร์ปิดไว้เป็นค่าเริ่มต้น**: ต้องลงชื่อเข้าใช้ตั้งแต่เริ่มทำงานครั้งแรก ไม่มีผู้ใช้
  เริ่มต้น ปฏิเสธการตั้งค่าที่ปิดการยืนยันตัวตนของ REST หรือการตรวจสอบสิทธิ์ และบรรเทา Java4Shell แล้ว
  Tomcat และ WildFly ต้องใช้ข้อมูลรับรองสำหรับ REST API และไม่สร้างผู้ใช้ใด ๆ (ผู้ดูแลระบบคนแรกสร้าง
  ในหน้า setup) ส่วน Run zip และ Spring Boot starter ยังคงค่าเริ่มต้นของ Camunda 7 ทุกรูปแบบการแจกจ่าย
  มีการแก้ไขด้านความปลอดภัย และแจ้งเตือนตอนเริ่มทำงานหาก JVM ได้รับผลกระทบจาก Java4Shell
  ([ความปลอดภัย](https://fasti.dev/security))
- **Single sign-on**: OpenID Connect และ JWT bearer token ([คู่มือ](../single-sign-on.th.md))
- **แพลตฟอร์มปัจจุบัน**: Java 17 ขึ้นไป, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([ประสิทธิภาพ](https://fasti.dev/#performance))

<a id="installation"></a><a id="get-fasti"></a>

## วิธีรับ Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**อิมเมจ Docker** (`fastidev/fasti-bpm`, amd64 และ arm64): <https://fasti.dev/install> ดูขั้นตอน
  ทีละขั้นใน [docs/install-docker.th.md](../install-docker.th.md) และตัวแปรทั้งหมดใน
  [distro/docker/README.md](../../distro/docker/README.md#environment-variables)
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**ชุด Tomcat** โดยไม่ใช้ Docker: <https://fasti.dev/download> ชุด Tomcat 1.3.1 ที่พร้อมใช้งาน:
  - [fasti-bpm-tomcat-1.3.1.zip](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip.sha256))
  - [fasti-bpm-tomcat-1.3.1.tar.gz](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz.sha256))

  ตรวจสอบค่าเช็กซัมก่อนแตกไฟล์ ตัวอย่างเช่น `sha256sum -c fasti-bpm-tomcat-1.3.1.zip.sha256`
  และอ่าน `README.txt` ในชุดก่อนเริ่มใช้งาน
- **แบบฝัง** ใน Spring Boot หรือแอปพลิเคชัน Java อื่น: พิกัด Maven ใหม่
  (`dev.fasti.platform:fasti-*`) แพ็กเกจ `org.camunda.bpm.*` ชุดเดิม ดู
  [docs/embedding.th.md](../embedding.th.md)

<a id="moving-from-camunda-7"></a>

## ย้ายจาก Camunda 7

หากคุณใช้ Camunda 7.24 อยู่ Fasti ทำงานบนฐานข้อมูลเดียวกันนั้นได้โดยไม่เปลี่ยน schema คุณสามารถ
แทนที่ node เดิม รัน Fasti คู่กับ node เดิม และย้อนกลับไปใช้ Camunda 7.24 ได้ทุกเมื่อ
image ของ Fasti ผ่านการทดสอบการรับช่วงต่อฐานข้อมูลที่ Camunda Run 7.24 สร้างไว้บน PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022 และ Oracle Database 23ai แล้ว และทดสอบการย้อนกลับไปใช้ Camunda Run บนฐานข้อมูลเดียวกันนี้แล้ว ยกเว้น MySQL ส่วน Db2 12.1 ผ่านการทดสอบแบบเดียวกันแล้ว และต้องตั้งค่าตัวแปร registry ของ Db2 สามตัว ([ข้อจำกัด](../migrate-from-camunda-7.th.md#limits)) ทุก REST client รวมถึง
external task worker ต้องส่งข้อมูลรับรองแบบ Basic หรือ bearer token

แอปพลิเคชัน Spring Boot แบบ embedded ต้องย้ายไปใช้ Spring Boot 4.1 และ Java 17 ด้วย ส่วน schema รุ่นเก่าต้องอัปเกรดด้วยสคริปต์มาตรฐานก่อน plugin และ process application ที่สร้างสำหรับ Camunda 7 ต้อง build ใหม่กับ artifact ของ Fasti จึงจะทำงานภายใน Fasti ได้ การทำงานคู่กันและการย้อนกลับมีข้อจำกัด (การตั้งค่าเดียวกันทุกโหนด โหนดใดรัน job และ process ที่ใช้ฟีเจอร์เฉพาะของ Fasti) ดู [การทำงานแบบผสม](../migrate-from-camunda-7.th.md#mixed-operation-with-camunda-7-nodes) และ [การย้อนกลับ](../migrate-from-camunda-7.th.md#rollback-to-camunda-724)

ขั้นตอนและข้อจำกัดของการย้ายระบบ: <https://fasti.dev/install#migrate> และ
[คู่มือการย้ายระบบ](../migrate-from-camunda-7.th.md) (การย้อนกลับ schema รุ่นเก่า และผลการทดสอบ)

<a id="comparison"></a><a id="how-it-compares"></a>

## การเปรียบเทียบ

มีหลายโครงการที่ดีที่สานต่อ Camunda 7 Fasti คงแพ็กเกจ `org.camunda.bpm.*` และ schema ของ 7.24
ไว้ การย้ายจึงเป็นเพียงการเปลี่ยนการเชื่อมต่อ ไม่ใช่โครงการ migration และยังเพิ่มอินเทอร์เฟซใหม่ให้
ด้วย โครงการอื่นก็เป็นตัวเลือกที่ดีเช่นกัน ตารางฉบับเต็มพร้อมแหล่งที่มา:
<https://fasti.dev/compare>

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## ความปลอดภัยและการสนับสนุน

รีลีส minor ล่าสุดของสาย `1.x` จะได้รับการแก้บั๊กและแพตช์ความปลอดภัย ในรูปแบบรีลีส patch เช่น
`1.0.1` โปรดรายงานปัญหาด้านความปลอดภัยเป็นการส่วนตัว ไม่ใช่ใน issue สาธารณะ ดู
[SECURITY.md](../../.github/SECURITY.md#security-policy) ซึ่งมี
[นโยบายการสนับสนุน](../../.github/SECURITY.md#support-policy) อยู่ด้วย รายการการแก้ไข Java4Shell และการตรวจทาน
ก่อนรีลีส: <https://fasti.dev/security> หากต้องการความช่วยเหลือด้านการติดตั้ง การย้ายระบบ หรือ
การใช้งานจริง และเงื่อนไขการสนับสนุน ส่งอีเมลมาที่ <contact@fasti.dev>

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## แผนงาน

นี่คือแผน ไม่ใช่คำมั่นเรื่องวันที่ เผยแพร่แล้วใน `1.2.0` (เน้นประสิทธิภาพ): endpoint สรุปปริมาณงาน,
ดัชนีเสริมของฐานข้อมูล, คำแนะนำด้านประสิทธิภาพตอนเริ่มระบบ, การปรับแต่ง audit trail
และอินเทอร์เฟซเว็บที่เร็วขึ้น เผยแพร่แล้วใน `1.1.0`: Helm chart และ manifest ของ Kubernetes,
metrics ของ Prometheus, tracing ของ OpenTelemetry, log แบบมีโครงสร้างในรูปแบบ JSON,
ผู้ปฏิบัติงานแทนระหว่างที่ไม่อยู่, การควบคุมแบบ four-eyes สำหรับการอนุมัติ, audit trail ที่แก้ไขไม่ได้,
MCP server สำหรับผู้ช่วย AI และเคส (CMMN) ในอินเทอร์เฟซใหม่ ลำดับถัดไปใน `1.x`: การเผยแพร่อาร์ติแฟกต์
Maven บน Maven Central, การตรวจทานคำแปลโดยเจ้าของภาษา และการเลิกใช้แอปพลิเคชัน
AngularJS ไทม์ไลน์ตั้งแต่การสิ้นสุดของ Camunda 7 CE ไปจนถึงเป้าหมายปี 2027:
<https://fasti.dev/#timeline>

<a id="documentation"></a>

## เอกสาร

บน [fasti.dev](https://fasti.dev): [ติดตั้งด้วย Docker](https://fasti.dev/install) ·
[ดาวน์โหลดสำหรับ Tomcat](https://fasti.dev/download) · [คู่มือการเชื่อมต่อ API](https://fasti.dev/docs) ·
[ความปลอดภัยและการสนับสนุน](https://fasti.dev/security) · [เปรียบเทียบ](https://fasti.dev/compare) ·
[บล็อก](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| เอกสาร | เนื้อหา |
|---|---|
| [docs/install-docker.md](../install-docker.th.md) | การติดตั้งด้วย Docker ทีละขั้นตอน |
| [docs/install-kubernetes.md](../install-kubernetes.md) | การติดตั้งบน Kubernetes: Helm chart และ manifest แบบธรรมดา (ตั้งแต่ 1.1.0) |
| [docs/migrate-from-camunda-7.md](../migrate-from-camunda-7.th.md) | การย้ายจาก Camunda 7 การย้อนกลับ และข้อจำกัด |
| [docs/embedding.md](../embedding.th.md) | ความเข้ากันได้ สิ่งที่แตกต่างจาก 7.24.0 การกำหนดเวอร์ชัน พิกัด Maven และการตรวจสอบเมื่อเริ่มทำงาน |
| [docs/web-ui.md](../web-ui.th.md) | เว็บอินเทอร์เฟซ: ฟีเจอร์ที่ผู้ใช้ร้องขอ พาธ `/fasti` และแอปแบบคลาสสิก |
| [docs/absence.md](../absence.md) | ผู้ปฏิบัติงานแทนระหว่างที่ไม่อยู่ (ตั้งแต่ 1.1.0) |
| [docs/four-eyes.md](../four-eyes.md) | การควบคุมแบบ four-eyes สำหรับการอนุมัติ (ตั้งแต่ 1.1.0) |
| [docs/audit-trail.md](../audit-trail.md) | Audit trail ที่แก้ไขไม่ได้ (ตั้งแต่ 1.1.0) |
| [docs/single-sign-on.md](../single-sign-on.th.md) | การลงชื่อเข้าใช้ครั้งเดียว (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](../api/README.md) | การเชื่อมต่อ REST API และไคลเอนต์งานภายนอกสำหรับ Java กับ JavaScript |
| [distro/docker/README.md](../../distro/docker/README.md) | อิมเมจ: ตัวแปรสภาพแวดล้อม ไดรเวอร์ การดูแลระบบ และการปรับแต่ง |
| [webapps/frontend-next/README.md](../../webapps/frontend-next/README.md#new-web-ui-frontend-next) | เว็บ UI ใหม่ |
| [docs/security/CVE-2026-114938.md](../security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell และ Fasti |
| [CHANGELOG.md](../../CHANGELOG.md), [SECURITY.md](../../.github/SECURITY.md#security-policy) | การเปลี่ยนแปลง ความปลอดภัย และนโยบายการสนับสนุน |

<a id="build"></a>

## บิลด์จากซอร์สโค้ด

ต้องใช้ JDK 17 ขึ้นไป (แนะนำ 21) และการเข้าถึง Maven Central, nodejs.org และ npm registry ผ่าน
เครือข่าย (ขั้นตอนบิลด์จะดาวน์โหลด Node/npm สำหรับเว็บ UI)

```bash
./mvnw clean verify
```

การบิลด์เต็มรูปแบบจะรันการทดสอบ Java ทั้งหมดและการทดสอบหน่วยของเว็บ UI (เกือบหนึ่งชั่วโมง; การทดสอบ
แบบครบวงจรด้วย Playwright รันแยกต่างหาก ดู [webapps/frontend-next](../../webapps/frontend-next/README.md#end-to-end-tests-playwright))
ดู [TESTING.md](../testing.md) หากต้องการบิลด์อิมเมจคอนเทนเนอร์ (การบิลด์ครั้งแรกใช้เวลาประมาณ 10–20
นาที):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## ร่วมพัฒนา

ยินดีรับคำถาม รายงานบั๊ก และ pull request บน GitHub ดู
[CONTRIBUTING.md](../../.github/CONTRIBUTING.md#how-to-contribute) และ[จรรยาบรรณ](../../.github/CODE_OF_CONDUCT.md)
ยินดีเป็นอย่างยิ่งหากเจ้าของภาษาช่วยตรวจทานคำแปล

<a id="acknowledgements"></a>

## กิตติกรรมประกาศ

- Fasti BPM Platform ต่อยอดจากผลงานกว่าทศวรรษของ Camunda และชุมชน Camunda 7
- ระหว่างทบทวนข้อบกพร่อง โครงการได้ศึกษาข้อมูลสาธารณะ (ประเด็นปัญหา ประกาศความปลอดภัย
  และบันทึกประจำรุ่น) ของโครงการชุมชนอื่นที่สืบทอดจาก Camunda 7 ได้แก่ Operaton, CIB
  seven, OrqueIO และ EximeeBPMS ซอร์สโค้ดในที่เก็บโค้ดนี้เขียนขึ้น
  อย่างอิสระโดยต่อยอดจาก Camunda 7 CE

<a id="license"></a>

## สัญญาอนุญาต

[สัญญาอนุญาต Apache 2.0](../../LICENSE) ดูลิขสิทธิ์ของโค้ดต้นฉบับใน [NOTICE](../../NOTICE)
