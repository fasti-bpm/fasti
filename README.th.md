[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | ไทย | [简体中文](README.zh-CN.md)

> คำแปลนี้ดูแลโดยชุมชนและอาจล้าหลังฉบับภาษาอังกฤษ ฉบับภาษาอังกฤษเป็นฉบับที่ใช้อ้างอิงหลัก ยินดีรับการแก้ไข

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition สิ้นสุดลงที่ 7.24.0 ซึ่งเป็น
รุ่นสุดท้าย Fasti BPM สานต่อจากจุดนั้นด้วยเอนจินเดิมบนฐานข้อมูลเดิม พร้อม
การแก้ไขและอัปเกรดไลบรารีอย่างต่อเนื่อง และเว็บ UI ใหม่

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
  fastidev/fasti-bpm:1.0.0
```

เปิด <http://localhost:8080/fasti/> แล้วเข้าสู่ระบบ หากไม่ได้กำหนด `DB_URL` อิมเมจจะใช้ฐานข้อมูล H2
ภายในคอนเทนเนอร์ ซึ่งเหมาะสำหรับการประเมินเท่านั้น REST API ต้องใช้ข้อมูลรับรองแบบ Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**บิลด์อิมเมจด้วยตนเอง** จากซอร์สโค้ด โดยต้องมี Docker, JDK 17+ และการเข้าถึง
Maven Central, nodejs.org และ npm registry ผ่านเครือข่าย (ขั้นตอนบิลด์จะดาวน์โหลด Node/npm สำหรับ
เว็บ UI) การบิลด์ครั้งแรกใช้เวลาประมาณ 10–20 นาที:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

อิมเมจนี้**กำหนดให้ REST ต้องยืนยันตัวตนและบังคับใช้การตรวจสอบสิทธิ์โดยค่าเริ่มต้น** (ต่างจาก Camunda
Run) มีเพียง `FASTI_INSECURE_DEMO=true` เท่านั้นที่ปิดการทำงานเหล่านี้สำหรับการสาธิต

<a id="highlights"></a>

## จุดเด่น

- **เว็บ UI ใหม่** ที่ `/fasti/` (React):
  - ค้นหาอินสแตนซ์กระบวนการด้วยตัวแปรหลายตัวพร้อมกัน โดยเชื่อมเงื่อนไขด้วย AND/OR;
  - ไทม์ไลน์ของเคสภายในงาน (ความคิดเห็นและการดำเนินการในทุกขั้นตอน);
  - ปุ่ม "รับงานถัดไป";
  - วันครบกำหนดและความเร่งด่วน (SLA) รวมถึงงานที่เกินกำหนด;
  - ภาระงานของทีมแยกตามผู้ใช้และกลุ่ม;
  - คู่มือปฏิบัติงานที่แนบกับเหตุขัดข้อง;
  - หน้าสถานะระบบ (เหตุขัดข้อง งานเบื้องหลังที่ล้มเหลว งานเบื้องหลังที่ล่าช้า งานภายนอก และแบตช์);
  - เลย์เอาต์ที่รองรับอุปกรณ์เคลื่อนที่;
  - 13 ภาษา รวมถึงภาษาเวียดนาม เครื่องมือสร้างโมเดล BPMN ก็รองรับภาษาเวียดนามด้วย
- **การลงชื่อเข้าใช้ครั้งเดียว (OIDC)**: Keycloak, Microsoft Entra ID และผู้ให้บริการ OpenID Connect อื่น ๆ;
  แมปเคลมกับกลุ่มและเทแนนต์; ใช้โทเค็น JWT bearer สำหรับ REST API ใช้งานได้กับ Run,
  Spring Boot, Tomcat และ WildFly
- **การเสริมความปลอดภัย**: รายการอนุญาตสำหรับนิพจน์ในคิวรีและตัวกรอง การตรวจสอบไฟล์แนบและ
  ความคิดเห็นภายในเอนจิน การตั้งค่าเริ่มต้น สิทธิ์ของ Cockpit การเปลี่ยนเส้นทางแบบเปิด ส่วนหัวของเว็บ
  แอปพลิเคชัน คุกกี้ และ CSP ดูรายละเอียดใน[บันทึกการเปลี่ยนแปลง](CHANGELOG.md)
- **เปิดการยืนยันตัวตนโดยค่าเริ่มต้น**: อิมเมจกำหนดให้ REST ต้องใช้ข้อมูลรับรอง และบังคับใช้
  การตรวจสอบสิทธิ์ในทุกโหมด
- **การตรวจสอบเวอร์ชันสคีมา**: เอนจินจะปฏิเสธการเริ่มทำงานหากสคีมาเก่ากว่า 7.24 และจะไม่
  อัปเกรดสคีมาด้วยตนเอง
- **การลดความเสี่ยงจาก Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): อิมเมจ amd64
  มาพร้อม Temurin JRE ที่แก้ไขแล้วและปิด Base64 intrinsic; เอนจินจะแจ้งเตือนเมื่อ
  ทำงานบน JVM ที่ได้รับผลกระทบ
- **ประสิทธิภาพ**: ขนาดการโหลดเว็บ UI ใหม่ครั้งแรกลดลงจาก 960 KB เหลือ 330 KB (เปิด gzip โดย
  ค่าเริ่มต้น); อัตราการประมวลผลของ job executor เพิ่มขึ้นประมาณ 2.5 เท่าหลังปรับแต่ง (ดูตัวเลขและการตั้งค่าทดสอบใน
  [distro/docker/README.md](distro/docker/README.md#performance-tuning))
- **แพลตฟอร์มปัจจุบัน**: Java 17 ขึ้นไป, Spring Boot 4.1, GraalJS 25, Jackson 2.21

<a id="installation"></a>

## การติดตั้ง

<a id="one-command-on-your-existing-camunda-724-database"></a>

### ใช้คำสั่งเดียวกับฐานข้อมูล Camunda 7.24 ที่มีอยู่

อิมเมจรองรับตัวแปร `DB_*` ของอิมเมจ Camunda 7 Run เดิม สำรองฐานข้อมูล
แล้วรัน:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

ผู้ใช้ รหัสผ่าน กลุ่ม เทแนนต์ สิทธิ์ ตัวกรอง การปรับใช้ อินสแตนซ์ที่กำลังทำงาน
ประวัติ งานเบื้องหลัง และเหตุขัดข้องจะยังคงเดิม พอร์ตจะผูกกับเครื่องนี้เท่านั้น หากต้องการ
เข้าถึงจากระยะไกล ให้ผ่าน reverse proxy ที่ใช้ HTTPS เข้าสู่ระบบที่ `/fasti/` ด้วยบัญชีเดิม
เนื่องจากเปิดการยืนยันตัวตนอยู่ ไคลเอนต์ REST ทุกราย (ตัวประมวลผลงานภายนอกและระบบที่เชื่อมต่อ) ต้องส่ง
ข้อมูลรับรองแบบ Basic หรือโทเค็น bearer

- **ทำงานควบคู่กัน** กับโหนด Camunda 7.24 CE บนฐานข้อมูลเดียวกัน: ใช้ระดับประวัติ
  `TZ` และการตั้งค่ารหัสผ่านเดียวกัน หากโหนดเดิมเป็นแอปพลิเคชันกระบวนการที่มีคลาส Java ซึ่ง
  Fasti ไม่มี ให้ตั้ง `FASTI_JOB_EXECUTOR_ENABLED=false` บน Fasti (หรือทำให้ทุกโหนด
  ใช้โหมด deployment-aware) และอย่าเริ่มกระบวนการเหล่านั้นหรือทำงานของกระบวนการให้เสร็จผ่าน Fasti:
  การเรียกแบบซิงโครนัสจะรัน delegate บนโหนดที่รับคำขอ
- **ย้อนกลับ**: Fasti ไม่เปลี่ยนสคีมา จึงสามารถหยุด Fasti แล้วเริ่มโหนด Camunda 7.24
  อีกครั้งบนฐานข้อมูลเดิมได้ กระบวนการที่ปรับใช้และใช้ฟีเจอร์เฉพาะของ Fasti จะไม่ทำงานบน
  Camunda 7 ดู[การย้อนกลับไปใช้ Camunda 7.24](docs/migrate-from-camunda-7.th.md#rollback-to-camunda-724)
- **สคีมาเก่า (7.17–7.23)**: รันสคริปต์ `sql/upgrade` ที่มาพร้อมอิมเมจ
  (`/fasti/configuration/sql/upgrade`) เพื่ออัปเกรดเป็น 7.24 ก่อน ตัวอย่างเช่น 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`

ทดสอบกับอิมเมจแล้ว (รับช่วงฐานข้อมูลที่สร้างโดย Camunda Run 7.24.0 ย้อนกลับ
และทำงานควบคู่กัน): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (ไม่ได้รวมไดรเวอร์ Oracle ไว้ ต้องเพิ่ม `ojdbc11` เอง; ไม่ได้ทดสอบการย้อนกลับ
บน MySQL) **ยังไม่ได้
ตรวจสอบ DB2** H2 เหมาะสำหรับการประเมินเท่านั้น ผลการทดสอบแต่ละรายการและข้อจำกัด:
[คู่มือการย้ายระบบ](docs/migrate-from-camunda-7.th.md#limits)

คู่มือโดยละเอียด:

- [การติดตั้งด้วย Docker](docs/install-docker.th.md): H2, PostgreSQL ด้วย `docker run` และ
  `docker compose`, ฐานข้อมูลที่มีอยู่, โหมดทำงานควบคู่กัน, การตรวจสอบสถานะ, การอัปเกรด, การย้อนกลับ,
  การแก้ไขปัญหา
- [การย้ายจาก Camunda 7](docs/migrate-from-camunda-7.th.md): ขั้นตอน สิ่งที่เหมือนและ
  แตกต่าง การย้อนกลับ และข้อจำกัด
- [เอกสารอ้างอิงตัวแปรสภาพแวดล้อมของอิมเมจ](distro/docker/README.md#environment-variables)

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### ไม่ใช้ Docker: ชุด Tomcat (ดาวน์โหลดจาก fasti.dev)

ชุด Tomcat 1.0.0 ที่พร้อมใช้งาน (หน้าดาวน์โหลด: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

ตรวจสอบค่าเช็กซัมก่อนแตกไฟล์ ตัวอย่างเช่น `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`

นอกจากนี้ยังบิลด์ชุดแจกจ่าย Tomcat จากซอร์สโค้ดได้ (`distro/tomcat`) อ่าน `README.txt` ใน
ชุดก่อนเริ่มใช้งาน: REST API (`/engine-rest`) กำหนดให้ใช้การยืนยันตัวตนแบบ HTTP Basic
โดยค่าเริ่มต้น ไม่มีการสร้างผู้ใช้ไว้ให้ (สร้างผู้ดูแลระบบคนแรกบนหน้าตั้งค่าของ `/fasti/`)
และเมื่อรับช่วงฐานข้อมูล Camunda Run ให้ตั้ง `jobExecutorDeploymentAware=false` ใน
`conf/bpm-platform.xml` มิฉะนั้น ตัวตั้งเวลาและงานเบื้องหลังแบบอะซิงโครนัสของการปรับใช้เดิมจะไม่ทำงาน

<a id="a-ui-built-from-real-user-complaints"></a>

## UI ที่สร้างจากปัญหาที่ผู้ใช้พบจริง

ฟีเจอร์ของเว็บ UI ใหม่คัดเลือกจากสิ่งที่ผู้ใช้ Camunda 7 เขียนไว้สาธารณะบน
ฟอรัม Camunda และ GitHub ตัวอย่างเช่น:

| สิ่งที่ผู้ใช้ร้องขอ | แหล่งข้อมูล | ใน Fasti |
|---|---|---|
| ค้นหาอินสแตนซ์ด้วยตัวแปรหลายตัว โดยใช้ OR | ฟอรัม [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | เครื่องมือสร้างเงื่อนไขหลายตัวแปร AND/OR |
| ดูความคิดเห็นในทุกขั้นตอนของเคส | ฟอรัม [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | ไทม์ไลน์ของเคสในงาน; ความคิดเห็นบนอินสแตนซ์กระบวนการโดยตรง |
| เรียงงานตามตัวแปร โดยให้งานเก่าที่สุดขึ้นก่อน | ฟอรัม [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | เรียงตามตัวแปร วันครบกำหนด และความเร่งด่วน |
| มอบหมายงานต่อและกำหนดผู้ปฏิบัติงานแทนระหว่างไม่อยู่ | ฟอรัม [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | รองรับการมอบหมายต่อสำหรับแต่ละงานแล้ว; ผู้ปฏิบัติงานแทนระหว่างไม่อยู่ยังอยู่ใน[แผนงาน](#roadmap-plan) |
| ระบุคีย์ธุรกิจและตัวแปรเมื่อเริ่มกระบวนการโดยไม่มีฟอร์ม | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | รองรับ |
| ดาวน์โหลด BPMN XML ที่ปรับใช้แล้ว | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | รองรับ |
| ข้อความกำกับที่สอดคล้องกันและรองรับหลายภาษา | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 ภาษา |

<a id="comparison"></a>

## การเปรียบเทียบ

การเปรียบเทียบโดยย่อจากเอกสารสาธารณะของแต่ละโครงการ (อ่านในเดือนพฤศจิกายน 2026);
"ยังไม่ได้ตรวจสอบ" หมายถึงไม่พบแหล่งข้อมูลที่เชื่อถือได้ แต่ละโครงการมีทิศทางของตนเองและเป็น
ตัวเลือกที่เหมาะสมสำหรับความต้องการที่แตกต่างกัน โปรดตรวจสอบเอกสารของโครงการก่อนตัดสินใจ

| | สัญญาอนุญาต | ทำงานบนฐานข้อมูล C7 ที่มีอยู่ | ต้องย้ายระบบ | แพตช์ความปลอดภัย | Java / Spring Boot | UI | SSO | อิมเมจ Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | ได้ สคีมา 7.24 ไม่เปลี่ยนแปลง | อิมเมจ: ไม่ต้อง แอปที่ฝังเอนจิน: พิกัด Maven ใหม่, Spring Boot 4.1 และ Java 17+; คงแพ็กเกจ `org.camunda.*` ไว้ | มี ดู[นโยบายด้านล่าง](#commitments-support-and-security-patches) | 17+ / 4.1 | ใหม่ (React) + แบบคลาสสิก (เลิกแนะนำให้ใช้) | มี OIDC ในตัว | มี ตั้งแต่ 1.0.0 |
| Camunda 7 CE 7.24 | ซอร์สโค้ด Apache-2.0; ส่วนประกอบเว็บบางส่วนที่รวมมาด้วยใช้ MIT ([13]) | (ต้นฉบับ) | — | ไม่มีรุ่น CE หลัง 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 ผ่าน security starter | อิมเมจ Run เดิม |
| Camunda 7 Enterprise | เชิงพาณิชย์ | ได้ (สายผลิตภัณฑ์เดียวกัน) | ไม่ต้อง | ถึง 04/2030 และขยายแบบมีค่าใช้จ่ายถึง 04/2032 ([3]) | ยังไม่ได้ตรวจสอบ | Cockpit Enterprise | ยังไม่ได้ตรวจสอบ | ยังไม่ได้ตรวจสอบ |
| Camunda 8 | Camunda License 1.0; การใช้งานจริงแบบดูแลระบบเองต้องมีใบอนุญาตตั้งแต่ 8.6 ([4]) | ไม่ได้ (คนละเอนจิน) | ต้องย้าย (โมเดล อินสแตนซ์ ข้อมูล) | มี (ผู้จำหน่าย) | คนละเอนจิน จึงเปรียบเทียบกันไม่ได้ | Operate, Tasklist | ยังไม่ได้ตรวจสอบ | ยังไม่ได้ตรวจสอบ |
| Operaton | Apache-2.0 | โครงสร้างตารางเดียวกับ 7.24; 2.2 บันทึกเวอร์ชันสคีมา 7.25 ผ่านสคริปต์อัปเกรด ([5]) | เปลี่ยนชื่อแพ็กเกจเป็น `org.operaton.*` (มีสูตร OpenRewrite); สคริปต์เวอร์ชันสคีมา (2.2) | มี สาย 2.2.x ([6]) | 17+ / 4.1 ([5]) | แบบคลาสสิก + webapps-neo (รุ่นพรีวิว) | ยังไม่ได้ตรวจสอบ | มี ([7]) |
| CIB seven | Apache-2.0; EE ต้องมีคีย์ใบอนุญาต | ได้ (รวมการเปลี่ยนแปลง 7.24 แล้ว) | เปลี่ยนชื่อแพ็กเกจเป็น `org.cibseven.*` และคำนำหน้าเป็น `cibseven.*` | มี ตามแต่ละรุ่น ([8]) | 17, 21 / 3.5 และ 4.0 ([9]) | เว็บไคลเอนต์ Vue 3 | OAuth2/JWT สำหรับ REST ([8]) | มี ([8]) |
| OrqueIO | Apache-2.0 | เข้ากันได้กับตาราง C7 ตามเอกสารของโครงการ ([10]) | กลุ่ม Maven `io.orqueio`; ไม่ได้ระบุการเปลี่ยนชื่อแพ็กเกจ | มี ตามเอกสารของโครงการ ([10]) | 21 / 3.5, 4.0, 4.1 | เขียนใหม่ด้วย Angular | OIDC หลายผู้ให้บริการ | ยังไม่ได้ตรวจสอบ |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | โครงการระบุว่าสคีมายังคงเข้ากันได้กับ Camunda 7 (Fasti ยังไม่ได้ตรวจสอบ) ([14]) | เปลี่ยนชื่อแพ็กเกจเป็น `org.eximeebpms.*` และเปลี่ยนชื่อ Model API | มี สำหรับรุ่นย่อยล่าสุดสองรุ่น ([11]) | 21 / ไม่ได้ระบุเวอร์ชัน Spring Boot | Monitor แยกต่างหาก | ยังไม่ได้ตรวจสอบ | มี (ที่เก็บโค้ด `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | ยังไม่ได้ตรวจสอบ | ยังไม่ได้ตรวจสอบ | ยังไม่ได้ตรวจสอบ | 21 (อิมเมจ Corretto) / ยังไม่ได้ตรวจสอบ | Control Center | ยังไม่ได้ตรวจสอบ | มี ([12]) |

ข้อจำกัดของ Fasti เอง กล่าวอย่างตรงไปตรงมา: เป็นโครงการใหม่ที่มีทีมขนาดเล็ก ยังไม่มีการสนับสนุน
เชิงพาณิชย์ และยังไม่ได้ตรวจสอบ DB2

แหล่งข้อมูล: [1] [ประกาศการสิ้นสุดอายุการใช้งาน CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [สภาพแวดล้อมที่ 7.24 รองรับ](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [การขยายระยะเวลา Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [สัญญาอนุญาต Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [สภาพแวดล้อมที่ CIB seven รองรับ](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [เอกสาร OrqueIO](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [สัญญาอนุญาต Camunda 7](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
ภาพรวมจากแหล่งอิสระ: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/)

<a id="timeline"></a>

## ไทม์ไลน์

<a id="camunda-7-milestones"></a>

### เหตุการณ์สำคัญของ Camunda 7

| วันที่ | เหตุการณ์สำคัญ | แหล่งข้อมูล |
|---|---|---|
| 2024-03-06 | Camunda ประกาศสิ้นสุดอายุการใช้งาน Camunda 7 CE ในเดือนตุลาคม 2025 โดยมี 7.24 เป็นรุ่น CE สุดท้าย | [ฟอรัม Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda ขยายการสนับสนุน Camunda 7 Enterprise ถึง 2030-04-09 พร้อมการสนับสนุนเพิ่มเติมแบบมีค่าใช้จ่ายถึงเดือนเมษายน 2032 | [บล็อก Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | สร้างแท็ก `7.24.0` และเผยแพร่ไปยัง Maven Central: รุ่น CE สุดท้าย | [แท็ก 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | วันเผยแพร่ 7.24 ตามกำหนดของ Camunda; หลังจากวันนี้จะไม่มีรุ่น CE เพิ่มเติม รวมถึงแพตช์ความปลอดภัย หลังจากนั้นที่เก็บโค้ด CE ถูกเก็บถาวรแล้ว | [ประกาศ](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [หน้าการสนับสนุน](https://docs.camunda.org/enterprise/announcement) |
| เมษายน 2030 | สิ้นสุดการสนับสนุน Camunda 7 Enterprise (7.24 LTS: 2030-04-13) | [หน้าการสนับสนุน](https://docs.camunda.org/enterprise/announcement) |
| เมษายน 2032 | สิ้นสุดการสนับสนุนเพิ่มเติมแบบมีค่าใช้จ่ายสำหรับ Camunda 7 Enterprise | [หน้าการสนับสนุน](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti สานต่อโดยตรงจากแท็ก `7.24.0`; การแก้ไขและการเปลี่ยนแปลงทั้งหมดนับจากจุดนั้น
(`git log 7.24.0..`) ระยะต่าง ๆ ด้านล่างเรียงตามงานที่ทำ โดยไม่ระบุวันที่:

1. **รากฐาน**: Java 17, Spring Boot 4.1, การอัปเกรดไลบรารี; พิกัด Maven
   `dev.fasti.platform`
2. **ความปลอดภัย**: แก้ไขปัญหาที่เปิดเผยต่อสาธารณะของสาย 7.x รายการอนุญาตสำหรับนิพจน์
   การตรวจสอบไฟล์แนบ/ความคิดเห็นภายในเอนจิน และการเสริมความปลอดภัยของเว็บแอปพลิเคชัน
3. **เว็บ UI ใหม่** ที่ `/fasti/` ค่อย ๆ แทนที่ Cockpit, Tasklist และ Admin แบบคลาสสิก
4. **การลงชื่อเข้าใช้ครั้งเดียว** (OIDC) สำหรับ Run, Spring Boot, Tomcat และ WildFly
5. **ใช้แทนได้ทันทีบนฐานข้อมูลเดิม**: อิมเมจรองรับตัวแปร `DB_*` เดิมและตรวจสอบเวอร์ชันสคีมา; ทดสอบแล้วกับระบบฐานข้อมูลห้าระบบ
6. **การตรวจสอบความปลอดภัยก่อนเผยแพร่** (ทบทวนโค้ด ทดสอบเจาะระบบอิมเมจ สแกน ZAP baseline;
   แก้ไขสิ่งที่พบแล้ว) และ**การลดความเสี่ยงจาก Java4Shell**
7. **1.0.0**: รุ่นแรก (2026-11-20)

<a id="roadmap-plan"></a>

## แผนงาน (แผน)

> นี่คือ**แผน** ไม่ใช่ข้อผูกมัด; ลำดับอาจเปลี่ยนแปลงตามความคิดเห็นของผู้ใช้

- **การมอบหมายงานระหว่างไม่อยู่**: กำหนดผู้ปฏิบัติงานแทนในช่วงวันที่ที่ระบุ; งานใหม่จะถูกมอบหมายใหม่และ
  มีการบันทึกการเปลี่ยนแปลงเพื่อการตรวจสอบ ([ฟอรัม 2788](https://forum.camunda.io/t/2788),
  [38199](https://forum.camunda.io/t/38199))
- **การอนุมัติแบบผู้ตรวจสองคน**: ผู้อนุมัติในขั้นตอนภายหลังต้องไม่ใช่ผู้ที่เคยดำเนินการในขั้นตอนใด ๆ ก่อนหน้านั้น
  โดยบังคับใช้ภายในเอนจิน
- **Helm chart** สำหรับ Kubernetes
- **เมตริกและ OpenTelemetry**: รองรับ Micrometer/Prometheus โดยตรง และการติดตามกระบวนการกับงานเบื้องหลัง
- **อัปเกรด MyBatis** เป็นรุ่นปัจจุบัน
- **สายการสนับสนุนระยะยาว (LTS)** สำหรับผู้ใช้ที่ไม่ค่อยอัปเกรด
- **การตรวจสอบ DB2**
- **เอนด์พอยต์สรุปภาระงาน** เพื่อให้หน้าภาระงานยังคงรวดเร็วเมื่อมีชุดข้อมูลขนาดใหญ่

<a id="commitments-support-and-security-patches"></a>

## ข้อผูกมัด: การสนับสนุนและแพตช์ความปลอดภัย

> นโยบายนี้ดูแลโดยทีมโครงการ Fasti BPM และอาจมีการปรับปรุง; ฉบับใน
> ที่เก็บโค้ดนี้คือฉบับที่มีผลบังคับใช้

- **สายรุ่นที่รองรับ:** รุ่นย่อยล่าสุดของสาย `1.x` จะได้รับการแก้ไขข้อบกพร่องและแพตช์
  ความปลอดภัย (ในรูปแบบรุ่นแพตช์ เช่น `1.0.1`)
- **แพตช์ความปลอดภัย:** ช่องโหว่ใน Fasti และไลบรารีที่รวมมาด้วยจะมีลำดับความสำคัญเหนือ
  งานอื่น; จะบิลด์อิมเมจใหม่เมื่ออิมเมจพื้นฐานหรือ JRE ได้รับการแก้ไข โครงการติดตาม
  ประกาศความปลอดภัยสาธารณะเกี่ยวกับ Camunda 7 และโครงการที่แยกไปพัฒนาต่อ และตรวจสอบว่า Fasti ได้รับผลกระทบหรือไม่
- **ความเข้ากันได้:** สาย `1.x` คงสคีมาฐานข้อมูล 7.24 และแพ็กเกจ `org.camunda.bpm.*`
  ไว้ การเปลี่ยนแปลงที่ทำให้ความเข้ากันได้เสียไปจะระบุว่า **BREAKING** ใน[บันทึกการเปลี่ยนแปลง](CHANGELOG.md)
- **ระดับการสนับสนุน:** นี่เป็นโครงการชุมชน; ให้การสนับสนุนอย่างเต็มความสามารถ **โดยไม่มี SLA**
  และยังไม่มีการสนับสนุนเชิงพาณิชย์
- **การรายงานช่องโหว่:** โปรด**อย่า**เปิดประเด็นปัญหาสาธารณะ; ดู
  [SECURITY.md](SECURITY.md#security-policy) (รายงานแบบส่วนตัวผ่าน GitHub)
- **ข้อบกพร่องที่แก้ไขแล้ว:** ส่วน *แก้ไขแล้ว* และ *ความปลอดภัย* ใน[บันทึกการเปลี่ยนแปลง](CHANGELOG.md) ระบุ
  สิ่งที่แก้ไขเมื่อเทียบกับ Camunda 7 CE 7.24.0; ประกาศความปลอดภัยแสดงอยู่ใน
  [SECURITY.md](SECURITY.md#advisories)

<a id="documentation"></a>

## เอกสาร

| เอกสาร | เนื้อหา |
|---|---|
| [docs/install-docker.md](docs/install-docker.th.md) | การติดตั้งด้วย Docker ทีละขั้นตอน |
| [docs/api/README.md](docs/api/README.md) | การเชื่อมต่อ REST API และไคลเอนต์งานภายนอกสำหรับ Java กับ JavaScript |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.th.md) | การย้ายจาก Camunda 7 การย้อนกลับ และข้อจำกัด |
| [distro/docker/README.md](distro/docker/README.md) | อิมเมจ: ตัวแปรสภาพแวดล้อม ไดรเวอร์ การดูแลระบบ และการปรับแต่ง |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | เว็บ UI ใหม่ |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell และ Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | การเปลี่ยนแปลง ความปลอดภัย และการมีส่วนร่วม |

<a id="what-this-is"></a>

## โครงการนี้คืออะไร

ที่เก็บโค้ดนี้มีเอนจินกระบวนการ BPMN 2.0 / DMN, REST API, เว็บ
แอปพลิเคชัน (Cockpit, Tasklist, Admin) และชุดแจกจ่าย (Run, Tomcat, WildFly) โดย
เริ่มต้นจากแท็ก `7.24.0` ของ Camunda 7 CE และพัฒนาต่อด้วยการแก้ไขข้อบกพร่องและอัปเกรด
ไลบรารี

เป้าหมายคือ**ความเข้ากันได้แบบย้อนหลัง**กับแอปพลิเคชันที่ทำงานบน Camunda 7:

- คงแพ็กเกจ `org.camunda.bpm.*`, เนมสเปซ BPMN
  `http://camunda.org/schema/1.0/bpmn` และคำนำหน้าการกำหนดค่า `camunda.bpm.*` ไว้;
- คงสคีมาฐานข้อมูลของ 7.24 ไว้;
- คงรูปแบบการปรับใช้ทั้งหมดไว้: Spring Boot, Run, Tomcat, WildFly; `javax` และ `jakarta`

<a id="what-is-different-from-camunda-7240-ce"></a>

## สิ่งที่แตกต่างจาก Camunda 7.24.0 CE

- **ความปลอดภัย:** แก้ไขปัญหาที่เปิดเผยต่อสาธารณะของสาย 7.x (นิพจน์ใน
  คิวรี การตั้งค่าเริ่มต้น การเข้าถึงไฟล์แนบและความคิดเห็น สิทธิ์ของ Cockpit การเปลี่ยนเส้นทาง
  แบบเปิด) พร้อมมาตรการป้องกันเพิ่มเติม: รายการอนุญาตสำหรับนิพจน์ในคิวรีและ
  ตัวกรอง การตรวจสอบไฟล์แนบและความคิดเห็นภายในเอนจิน ตัวกรองความปลอดภัยที่เข้มงวดขึ้น
  รวมถึงส่วนหัวและคุกกี้ในเว็บแอปพลิเคชัน
- **เสถียรภาพ:** ไม่มีหน่วยความจำรั่วไหลอีกเมื่อประเมินสคริปต์และนิพจน์ DMN ด้วย
  JavaScript (GraalJS) และมีการแก้ไขเอนจินอื่น ๆ อีกจำนวนหนึ่ง
- **แพลตฟอร์ม:** Java 17 ขึ้นไป, Spring Boot 4.1, GraalJS 25, Jackson 2.21 และไลบรารีอื่น ๆ อีกมาก
  ที่อัปเกรดเป็นรุ่นซึ่งมีการแก้ไขความปลอดภัย

การแก้ไขบางส่วนเปลี่ยนค่าเริ่มต้นให้มีพฤติกรรมที่ปลอดภัยขึ้น; อ่าน "สิ่งที่แตกต่าง" ใน
[คู่มือการย้ายระบบ](docs/migrate-from-camunda-7.th.md#what-differs) ก่อนอัปเกรด

<a id="versioning"></a>

## การกำหนดเวอร์ชัน

Fasti BPM Platform ใช้[การกำหนดเวอร์ชันเชิงความหมาย](https://semver.org/) โดยเริ่มที่ `1.0.0`
สาย `1.x` เข้ากันได้กับแอปพลิเคชันที่เขียนสำหรับ Camunda 7.24

<a id="maven-coordinates"></a>

## พิกัด Maven

อาร์ติแฟกต์ของ Fasti BPM Platform มีพิกัด Maven ของตนเอง **แพ็กเกจ Java
ยังคงเป็น `org.camunda.bpm.*`** ดังนั้นโค้ดแอปพลิเคชันจึงคงคำสั่ง import เดิมไว้ได้; คำนำหน้าการกำหนดค่า `camunda.bpm.*`,
เนมสเปซ BPMN/DMN, สคีมาฐานข้อมูล และชื่อโมดูลบน
WildFly ก็ไม่เปลี่ยนแปลงเช่นกัน ในส่วนของ Maven มีเพียงการประกาศ dependency เท่านั้น
ที่เปลี่ยน (พาธเว็บแอปพลิเคชันเปลี่ยนแยกต่างหาก ดู
[พาธเว็บแอปพลิเคชัน](#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; กลุ่ม `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` และ `org.camunda.template-engines` → `dev.fasti.platform.<group name>`
- **artifactId:** คำนำหน้า `camunda-` เปลี่ยนเป็น `fasti-` ส่วนที่เหลือคงเดิม
- **version:** ใช้เวอร์ชันของ Fasti BPM Platform (ดู[การกำหนดเวอร์ชัน](#versioning)) แทน
  `7.24.0`

ยังไม่มีการเผยแพร่อาร์ติแฟกต์เหล่านี้รุ่นใดไปยัง Maven Central ระหว่างนี้ ให้บิลด์
จากซอร์สโค้ดและนำอาร์ติแฟกต์ไปไว้ในที่เก็บ Maven ที่โครงการของคุณใช้

จัดการเวอร์ชันด้วย BOM:

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

อาร์ติแฟกต์ที่ใช้บ่อย:

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

ไลบรารี FEEL `org.camunda.feel:feel-engine` เป็นไลบรารีภายนอกที่ไม่ได้เป็นส่วนหนึ่งของ
ที่เก็บโค้ดนี้; พิกัดจึงคงเดิม

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### ไลบรารีภายนอกที่ยังดึงเอนจินต้นฉบับเข้ามา

เนื่องจากแพ็กเกจไม่เปลี่ยนแปลง เอนจินต้นฉบับ (`org.camunda.bpm:camunda-engine`)
และ `fasti-engine` จึงมีคลาสชื่อเดียวกัน Maven ถือว่าทั้งสองเป็นไลบรารีที่ไม่เกี่ยวข้องกัน
และไม่ตัดตัวใดออก หากไลบรารีภายนอก (เช่น ส่วนขยายจากชุมชน)
พึ่งพาอาร์ติแฟกต์ต้นฉบับ ให้ยกเว้นอาร์ติแฟกต์เหล่านั้นและประกาศอาร์ติแฟกต์
Fasti ที่ตรงกันแทน:

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

`mvn dependency:tree -Dincludes="org.camunda*"` แสดงอาร์ติแฟกต์ต้นฉบับที่ยัง
มีอยู่ (`org.camunda.feel:feel-engine` เป็นรายการที่ถูกต้อง อย่ายกเว้น)

<a id="startup-check"></a>

### การตรวจสอบเมื่อเริ่มทำงาน

เมื่อสร้างเอนจิน เอนจินจะตรวจสอบ class path และ**ปฏิเสธการเริ่มทำงาน**หากพบ
คลาสของเอนจินกระบวนการในไลบรารีมากกว่าหนึ่งตัว; ข้อผิดพลาด `ENGINE-12021` จะระบุ
ตำแหน่งของแต่ละตัว Spring Boot starter ทำการตรวจสอบแบบเดียวกันทันทีที่
แอปพลิเคชันเริ่มทำงาน ไลบรารีที่มองเห็นได้ผ่าน parent class loader เท่านั้น (เช่น เอนจิน
ที่ใช้ร่วมกันของเซิร์ฟเวอร์ซึ่งอยู่ควบคู่กับเอนจินที่ฝังในเว็บแอปพลิเคชัน) จะไม่ถูก
นับรวม หากคุณตั้งใจจัดระบบเช่นนี้ สามารถปิดการตรวจสอบด้วยพร็อพเพอร์ตีระบบ:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

ข้อจำกัด: หากไลบรารีเอนจินต้นฉบับอยู่ก่อน `fasti-engine` ใน class path
เอนจินจะถูกสร้างโดยคลาสของไลบรารีต้นฉบับ ซึ่งไม่มีการตรวจสอบนี้
ในกรณีนั้น มีเพียงแอปพลิเคชันที่ใช้ Fasti Spring Boot starter เท่านั้นที่จะถูกหยุด; สำหรับ
การเชื่อมต่อรูปแบบอื่น ให้ตรวจสอบ dependency ตามที่อธิบายข้างต้น

<a id="web-application-path-fasti"></a>

## พาธเว็บแอปพลิเคชัน: `/fasti`

Cockpit, Tasklist, Admin และเว็บ UI ใหม่ให้บริการภายใต้ `/fasti` (เดิมคือ `/camunda`)
ในทุกชุดแจกจ่าย: `http://localhost:8080/fasti/` เปิด UI ใหม่
(`/fasti/app/next/default/`) ส่วนแอปพลิเคชันแบบคลาสสิกอยู่ที่ `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` และ `/fasti/app/admin/...` REST API ยังคงอยู่ที่ `/engine-rest`

**แอปพลิเคชันแบบคลาสสิกเลิกแนะนำให้ใช้แล้ว** Cockpit, Tasklist, Admin และ Welcome แบบคลาสสิก
สร้างบน AngularJS 1.8.2 และ Bootstrap 3 ซึ่งทั้งคู่สิ้นสุดอายุการใช้งานแล้ว **อิมเมจคอนเทนเนอร์ไม่
ให้บริการแอปเหล่านี้โดยค่าเริ่มต้น** (การเปลี่ยนแปลงที่กระทบความเข้ากันได้): หน้าเว็บและบุ๊กมาร์กเดิมจะเปลี่ยนเส้นทางไปยังส่วนที่ตรงกันของ UI ใหม่ และคำขอไฟล์ของแอปเหล่านี้จะได้รับ `404`; `FASTI_CLASSIC_WEBAPPS=true` จะเปิดให้ใช้งาน
อีกครั้ง (พร้อมคำเตือน) ดู [distro/docker/README.md](distro/docker/README.md) ชุด
Tomcat และ WildFly ก็ไม่ให้บริการแอปเหล่านี้โดยค่าเริ่มต้นเช่นกัน (เปิดใช้งานอีกครั้งด้วย
`-Dfasti.webapp.classic.enabled=true` หรือ `FASTI_CLASSIC_WEBAPPS=true`) ส่วน Run zip และ Spring
Boot starter ยังคงให้บริการเหมือนเดิม เพื่อให้ระบบที่ติดตั้งและกำลังทำงานอยู่ไม่เสียหาย; ปิดได้
ด้วย `fasti.webapp.classic.enabled: false` แอปพลิเคชันแบบคลาสสิกจะถูกนำออกใน
รุ่นใดรุ่นหนึ่งในอนาคต

พาธเดิมยังใช้งานได้: ทุกคำขอไปยัง `/camunda/...` จะเปลี่ยนเส้นทางไปยังพาธเดียวกัน
ภายใต้ `/fasti/...` โดยคงส่วนที่เหลือของพาธและสตริงคิวรีไว้ GET และ HEAD จะได้รับ
`301`; เมธอดอื่นทั้งหมด (POST, PUT, DELETE เช่น ไคลเอนต์เดิมที่เรียก `/camunda/api/...`)
จะได้รับ `308` เพื่อให้ไคลเอนต์ส่งคำขอซ้ำด้วยเมธอดและเนื้อหาเดิม การเปลี่ยนเส้นทาง
มี `Cache-Control: no-store` จึงสลับกลับไปใช้ `/camunda` ได้โดยมีผลทันที

หากต้องการกลับไปใช้ `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (จะไม่มีการเปลี่ยนเส้นทาง)
  หากต้องการปิดเฉพาะการเปลี่ยนเส้นทาง: `fasti.webapp.legacy-path-redirect: false`
- **Tomcat:** ลบ `webapps/camunda` (แอปพลิเคชันเปลี่ยนเส้นทาง) แล้วเปลี่ยนชื่อ
  `webapps/fasti` เป็น `webapps/camunda`
- **WildFly:** ลบ `standalone/deployments/camunda.war` (และ `camunda.war.dodeploy`) แล้ว
  ตั้ง `<context-root>/camunda</context-root>` ใน `WEB-INF/jboss-web.xml` ของ WAR
  เว็บแอปพลิเคชัน

**Reverse proxy:** หากพร็อกซีส่งต่อเฉพาะ `/camunda/` (หรือเขียนพาธใหม่ ตั้งพาธ
คุกกี้ CSP หรือการตรวจสอบสถานะสำหรับ `/camunda`) ให้เพิ่ม `/fasti/` หรือย้ายไปใช้ `/fasti/` ทั้งหมด
คุกกี้ `XSRF-TOKEN` (และ `JSESSIONID` บน Tomcat กับ WildFly) มีพาธเป็น `/fasti` แล้ว
ผู้ใช้จึงต้องเข้าสู่ระบบอีกครั้ง บุ๊กมาร์กและลิงก์เดิมยังใช้งานได้ผ่านการเปลี่ยนเส้นทาง

<a id="single-sign-on-openid-connect"></a>

## การลงชื่อเข้าใช้ครั้งเดียว (OpenID Connect)

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

<a id="build"></a>

## การบิลด์

ต้องใช้ JDK 17 ขึ้นไป (แนะนำ 21)

```bash
./mvnw clean verify
```

การบิลด์เต็มรูปแบบจะรันการทดสอบ Java ทั้งหมดและการทดสอบหน่วยของเว็บ UI (เกือบหนึ่งชั่วโมง; การทดสอบ
แบบครบวงจรด้วย Playwright รันแยกต่างหาก ดู [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) และต้องเข้าถึง Maven Central,
nodejs.org และ npm registry ผ่านเครือข่าย ดู [TESTING.md](TESTING.md)

<a id="acknowledgements"></a>

## กิตติกรรมประกาศ

- Fasti BPM Platform ต่อยอดจากผลงานกว่าทศวรรษของ Camunda และชุมชน Camunda 7
- ระหว่างทบทวนข้อบกพร่อง โครงการได้ศึกษาข้อมูลสาธารณะ (ประเด็นปัญหา ประกาศความปลอดภัย
  และบันทึกประจำรุ่น) ของโครงการชุมชนอื่นที่สืบทอดจาก Camunda 7 ได้แก่ Operaton, CIB
  seven, OrqueIO และ EximeeBPMS ซอร์สโค้ดในที่เก็บโค้ดนี้เขียนขึ้น
  อย่างอิสระโดยต่อยอดจาก Camunda 7 CE

<a id="license"></a>

## สัญญาอนุญาต

[สัญญาอนุญาต Apache 2.0](LICENSE) ดูลิขสิทธิ์ของโค้ดต้นฉบับใน [NOTICE](NOTICE)
