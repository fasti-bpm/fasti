[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | ไทย | [简体中文](install-docker.zh-CN.md)

> คำแปลนี้ดูแลโดยชุมชนและอาจล้าหลังฉบับภาษาอังกฤษ ฉบับภาษาอังกฤษเป็นฉบับที่ใช้อ้างอิงหลัก ยินดีรับการแก้ไข

# การติดตั้ง Fasti BPM ด้วย Docker

คู่มือทีละขั้นตอน: ทดลองใช้อย่างรวดเร็วด้วย H2, ใช้งานจริงด้วย PostgreSQL (`docker run` หรือ
`docker compose`), เชื่อมต่อกับฐานข้อมูล Camunda 7 ที่มีอยู่, ทำงานร่วมกัน, ตรวจสอบสถานะ
อัปเกรด ย้อนกลับ และแก้ไขปัญหา เอกสารอ้างอิงฉบับเต็มของอิมเมจ:
[distro/docker/README.md](../distro/docker/README.md) Fasti BPM พัฒนาบน Camunda 7 CE
7.24.0 และเป็นโครงการอิสระที่ไม่มีความเกี่ยวข้องกับ Camunda Services GmbH

<a id="1-requirements"></a>

## 1. ข้อกำหนด

- Docker Engine พร้อม Docker Compose v2 (`docker compose`)
- สำหรับภาระงานจริง: จัดสรรอย่างน้อย 2 CPU และหน่วยความจำ 2 GB ให้คอนเทนเนอร์ Fasti (หากต่ำกว่านี้
  JVM จะเลือกใช้ Serial GC)
- ฐานข้อมูลสำหรับใช้งานจริง: PostgreSQL, MariaDB, MySQL, SQL Server หรือ Oracle (ดู
  [ตารางระบบที่ผ่านการทดสอบ](migrate-from-camunda-7.th.md#limits)) ยังไม่ได้ตรวจสอบ DB2 ส่วน H2 ใช้สำหรับการประเมินเท่านั้น

<a id="2-pull-the-image"></a>

## 2. ดึงอิมเมจ

<!-- update with the Docker Hub image name at release -->
อิมเมจเผยแพร่สำหรับ amd64 และ arm64 ในสองแหล่ง:

```bash
docker pull fastidev/fasti-bpm:1.1.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.1.0     # GitHub Container Registry
```

แท็ก: `1.1.0` (รุ่นนั้นโดยเฉพาะ), `1.1` (รุ่นแพตช์ล่าสุดของ 1.1) และ `latest` (รุ่นที่เผยแพร่
ล่าสุด) อิมเมจลงลายเซ็นด้วย cosign (แบบไม่ใช้คีย์, GitHub OIDC) และมาพร้อม SBOM และเอกสารรับรอง
แหล่งที่มา ตั้งแต่ 1.0.1 ลายเซ็นอยู่ในรูปแบบ Sigstore bundle ของ cosign 3 ส่วนอิมเมจ 1.0.0
มีลายเซ็นในรูปแบบเดิมของ cosign 2

คุณยังสามารถสร้างอิมเมจจากซอร์สโค้ดได้ (ต้องใช้ JDK 17+ และเชื่อมต่อเครือข่ายไปยัง Maven
Central, nodejs.org และ npm registry ได้) โดยใช้ชื่อ `fasti-bpm:local` แทน
`fastidev/fasti-bpm:1.1.0` ในทุกคำสั่งด้านล่าง:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. ทดลองใช้อย่างรวดเร็วด้วย H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.1.0
```

- เปิด <http://localhost:8080/fasti/> แล้วลงชื่อเข้าใช้เป็น `admin`
- หากไม่ได้กำหนด `DB_URL` อิมเมจจะใช้ H2 ใน `/fasti/data` โดยวอลุ่ม `fasti-data` จะเก็บข้อมูลไว้
  เมื่อสร้างคอนเทนเนอร์ใหม่ ใช้สำหรับการประเมินเท่านั้น ห้ามใช้กับข้อมูลจริง
- เพิ่ม `--webapps --rest --example` ต่อท้ายคำสั่งเพื่อให้มีกระบวนการตัวอย่าง (ใบแจ้งหนี้)
  ด้วย การใช้ `--example` เพียงอย่างเดียวจะไม่เปิด UI บนเว็บและ REST API
- REST API อยู่ที่ `http://localhost:8080/engine-rest` และต้องใช้ข้อมูลรับรอง HTTP Basic ดู
  [คู่มือ REST API](api/README.md)

<a id="4-production-with-postgresql-docker-run"></a>

## 4. ใช้งานจริงด้วย PostgreSQL: `docker run`

สร้างไฟล์ข้อมูลลับสองไฟล์ (รหัสผ่านฐานข้อมูลและรหัสผ่านผู้ดูแลระบบ) เมื่อใช้ `--production` เอนจิน
จะบังคับใช้นโยบายรหัสผ่าน: อย่างน้อย 10 อักขระ โดยมีตัวอักษรพิมพ์ใหญ่และพิมพ์เล็ก ตัวเลข
และอักขระพิเศษ

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
  fastidev/fasti-bpm:1.1.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` จะสร้างเฉพาะตารางที่ยังไม่มีในฐานข้อมูลใหม่เท่านั้น Fasti จะไม่
  อัปเกรดสคีมาที่มีอยู่ สามารถคงค่านี้ไว้ในการเริ่มทำงานครั้งต่อไปได้
- `--production` เปิดใช้คุกกี้ `Secure` และปฏิเสธการใช้ฐานข้อมูล H2 โดยปริยาย รวมถึง `DEBUG=true`
  ให้สิ้นสุดการเชื่อมต่อ TLS ที่ reverse proxy ด้านหน้า Fasti เบราว์เซอร์จะส่งคุกกี้ `Secure` ผ่าน HTTPS เท่านั้น (หรือไปยัง
  `localhost`)
- `WAIT_FOR` หน่วงการเริ่มทำงานจนกว่าฐานข้อมูลจะยอมรับการเชื่อมต่อ
- รหัสผ่านผู้ดูแลระบบจะถูกตั้งค่าเฉพาะเมื่อยังไม่มีผู้ดูแลระบบเท่านั้น หากต้องการเปลี่ยนภายหลัง ให้เปลี่ยนใน Admin

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. ใช้งานจริงด้วย PostgreSQL: `docker compose`

ไฟล์ตัวอย่าง: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, ข้อมูลลับ, การตรวจสอบสถานะ, `--production`, จำกัดหน่วยความจำ 2 GB)

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

ตัวแปรที่ตั้งค่าได้เมื่อเริ่มทำงาน: `FASTI_IMAGE` (ค่าเริ่มต้น `fastidev/fasti-bpm:1.1.0` เช่น
`FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (ค่าเริ่มต้น `8080`), `TZ` (ค่าเริ่มต้น `UTC`)

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. การเชื่อมต่อกับฐานข้อมูล Camunda 7 ที่มีอยู่

Fasti ทำงานบนสคีมา 7.24 ได้โดยไม่เปลี่ยนแปลงสคีมา ก่อนเริ่มทำงาน:

1. **สำรองข้อมูล**ฐานข้อมูล
2. ตรวจสอบเวอร์ชันสคีมา: `select VERSION_ from ACT_GE_SCHEMA_LOG` — ค่าสูงสุดต้องเป็น
   `7.24.x` สำหรับสคีมา 7.17–7.23: เรียกใช้สคริปต์ `sql/upgrade` ก่อน (รวมอยู่ในอิมเมจที่
   `/fasti/configuration/sql/upgrade`) ดู
   [คู่มือการย้ายระบบ](migrate-from-camunda-7.th.md#older-versions-717723)
3. ตรวจสอบสิทธิ์การเข้าถึง เพราะ Fasti บังคับใช้สิทธิ์เหล่านี้: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` และ `select count(*) from ACT_RU_AUTHORIZATION` หากระบบเดิม
   ปิดการตรวจสอบสิทธิ์ไว้ ให้มอบสิทธิ์แก่ผู้ใช้ทั่วไปก่อนย้ายทราฟฟิก
4. จดค่า `TZ`, ระดับการเก็บประวัติ, คำนำหน้าตาราง/สคีมา และการตั้งค่ารหัสผ่านของ
   โหนดเดิมไว้

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.1.0 --production
```

- พอร์ตผูกกับเครื่องนี้เท่านั้น หากต้องการเข้าถึงจากระยะไกล ให้ผ่าน reverse proxy ที่ใช้ HTTPS
- **ห้าม**ตั้งค่า `FASTI_DB_SCHEMA_UPDATE` หรือ `FASTI_ADMIN_*`: มีตารางและบัญชีอยู่แล้ว
  บันทึกการเริ่มทำงานต้องมีข้อความ `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- ระดับการเก็บประวัติมีค่าเริ่มต้นเป็น `auto`: ใช้ระดับที่เก็บไว้ในฐานข้อมูล
- ตอนนี้ไคลเอนต์ REST ทุกตัวต้องส่งข้อมูลรับรอง Basic หรือ bearer token
- ไดรเวอร์: มี PostgreSQL, MySQL, MariaDB, SQL Server และ H2 รวมมาให้แล้ว เพิ่มไดรเวอร์ **Oracle** และ **DB2** เอง (`ojdbc11.jar`, `jcc.jar`) ด้วย `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`
  SQL Server: ไดรเวอร์ 13.x เข้ารหัสการเชื่อมต่อและตรวจสอบใบรับรองโดยค่าเริ่มต้น
  เซิร์ฟเวอร์ต้องมีใบรับรองที่ถูกต้องและตรงกับชื่อโฮสต์ใน URL ส่วน `trustServerCertificate=true`
  (ข้ามการตรวจสอบใบรับรอง) หรือ `encrypt=false` (ไม่เข้ารหัส) ใช้สำหรับการทดลองในเครื่องเท่านั้น MySQL/MariaDB ต้องใช้
  `transaction-isolation=READ-COMMITTED`

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. การทำงานร่วมกับโหนด Camunda 7

Fasti และ Camunda 7.24 สามารถทำงานพร้อมกันบนฐานข้อมูลเดียวได้ (ผ่านการทดสอบ: ทั้งสองระบบมองเห็น
ข้อมูลของกันและกัน) เงื่อนไข:

- ใช้ระดับการเก็บประวัติ, `TZ`, การตั้งค่ารหัสผ่าน และคำนำหน้าตารางเดียวกัน;
- หากโหนดเดิมเป็นแอปพลิเคชันกระบวนการที่มีคลาส Java (`JavaDelegate`, listeners) ซึ่ง Fasti ไม่มี ให้ตั้งค่า `-e FASTI_JOB_EXECUTOR_ENABLED=false` บน Fasti หรือทำให้**ทุก**โหนด
  รับรู้การปรับใช้ (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` สำหรับ Fasti) วิธีนี้ป้องกันเฉพาะ
  งานแบบอะซิงโครนัส: การเริ่มอินสแตนซ์หรือทำงานให้เสร็จผ่าน Fasti ยังคงเรียกใช้ delegate
  บน Fasti ดังนั้นให้ส่งคำขอเหล่านั้นไปยังโหนดที่มีคลาส Java;
- ตัวกระจายโหลด: ใช้ sticky session สำหรับเว็บแอปพลิเคชัน ส่วน REST ที่ใช้ข้อมูลรับรอง Basic ไม่จำเป็นต้องใช้

รายละเอียด: [การทำงานร่วมกับโหนด Camunda 7](migrate-from-camunda-7.th.md#mixed-operation-with-camunda-7-nodes)

<a id="8-common-environment-variables"></a>

## 8. ตัวแปรสภาพแวดล้อมที่ใช้บ่อย

| ตัวแปร | ความหมาย |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | การเชื่อมต่อฐานข้อมูล (ชื่อตัวแปรจากอิมเมจ Camunda 7 Run เดิม; ชื่อแทน: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | รอให้ `host:port` พร้อมก่อนเริ่มทำงาน |
| `TZ` | เขตเวลาของ JVM; ต้องเหมือนกันทุกโหนด |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: สร้างตารางที่ยังไม่มีในฐานข้อมูลใหม่ |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | สร้างผู้ดูแลระบบหากยังไม่มี |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | ตัวดำเนินงานของโหนดนี้ |
| `FASTI_EXTRA_LIB` | ไดเรกทอรีของไฟล์ jar เพิ่มเติม (ไดรเวอร์ Oracle, DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | ขนาดสูงสุดของพูลการเชื่อมต่อ (ค่าเริ่มต้น 20 ขณะว่างเปิดค้างไว้ 10 รายการ `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | ตัวเลือก JVM, เปอร์เซ็นต์หน่วยความจำสำหรับฮีป (75) |

ตารางฉบับเต็ม: [distro/docker/README.md](../distro/docker/README.md#environment-variables)

<a id="9-health-checks"></a>

## 9. การตรวจสอบสถานะ

| เอนด์พอยต์ | การใช้งาน |
|---|---|
| `/actuator/health/liveness` | โพรบตรวจสอบการทำงานของ Kubernetes |
| `/actuator/health/readiness` | โพรบตรวจสอบความพร้อม, ตัวกระจายโหลด; รวมการเชื่อมต่อฐานข้อมูลและเอนจิน |
| `/actuator/health` | สถานะโดยรวม |

ไม่ต้องลงชื่อเข้าใช้ โดยเปิดให้เข้าถึงเฉพาะเอนด์พอยต์ตรวจสอบสถานะเท่านั้น `HEALTHCHECK` ของอิมเมจใช้
การตรวจสอบความพร้อม ดังนั้น `docker ps` จะแสดง `healthy` เมื่อ Fasti พร้อม:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`

<a id="10-upgrade-and-rollback"></a>

## 10. การอัปเกรดและย้อนกลับ

**การอัปเกรด Fasti** (เช่น `1.0.1` → `1.1.0`):

```bash
docker pull fastidev/fasti-bpm:1.1.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.1.0 --production   # same options as before
```

เมื่อใช้ Docker Compose: เปลี่ยน `FASTI_IMAGE` (หรือบรรทัด `image:`) จากนั้นเรียกใช้
`docker compose -f docker-compose.postgres.yml up -d` อ่าน[บันทึกการเปลี่ยนแปลง](../CHANGELOG.md)
ก่อน โดยเฉพาะรายการ **BREAKING** รุ่นในสาย `1.x` ยังคงใช้สคีมา 7.24 ดังนั้นการอัปเกรด
ภายในสายนี้จะไม่เปลี่ยนแปลงสคีมา `docker stop -t 60` เปิดโอกาสให้คำขอที่กำลังทำงานเสร็จสิ้นก่อน
หยุดคอนเทนเนอร์

**การย้อนกลับ:**

- กลับไปยัง Fasti รุ่นก่อนหน้า: เริ่มทำงานอีกครั้งด้วยแท็กเดิม;
- กลับไปยัง Camunda 7.24: หยุด Fasti แล้วเริ่มโหนด Camunda 7.24 อีกครั้งบนฐานข้อมูลเดิม การตั้งค่าของ UI ใหม่จะยังคงอยู่ (`fasti.prefs.*` ใน `ACT_ID_INFO` ซึ่ง Camunda 7 จะละเว้น) กระบวนการที่ปรับใช้และใช้คุณสมบัติเฉพาะของ Fasti จะไม่ทำงานบน Camunda 7 ดู
  [การย้อนกลับไปยัง Camunda 7.24](migrate-from-camunda-7.th.md#rollback-to-camunda-724)

<a id="11-troubleshooting"></a>

## 11. การแก้ไขปัญหา

อ่านบันทึกด้วย `docker logs fasti` (หรือ `docker compose logs fasti`)

| อาการ | สาเหตุและวิธีแก้ไข |
|---|---|
| REST API ส่งกลับ `401` | Fasti ต้องมีการยืนยันตัวตน: ส่ง `-u user:password` หรือ bearer token |
| รายการว่างเปล่าหรือ `403` | ผู้ใช้ไม่มีสิทธิ์การเข้าถึง: มอบสิทธิ์ที่จำเป็นสำหรับทรัพยากรที่เกี่ยวข้องใน Admin → Authorizations เพิ่มเฉพาะผู้ดูแลระบบจริงลงใน `camunda-admin` |
| หยุดทำงานพร้อมข้อความ `ENGINE-03111 The database schema has version …` | สคีมาเก่ากว่า 7.24: สำรองข้อมูล แล้วเรียกใช้สคริปต์ `sql/upgrade` (หัวข้อ 6) ห้ามข้ามการตรวจสอบด้วย `FASTI_SCHEMA_VERSION_CHECK=warn`: ไม่รองรับการทำงานบนสคีมาที่เก่ากว่า และอาจเกิดข้อผิดพลาดระหว่างการทำงาน |
| หยุดทำงานพร้อมข้อความ `Insecure configuration refused` | มีการตั้งค่าที่ปิดการยืนยันตัวตน REST หรือการตรวจสอบสิทธิ์; ให้ลบการตั้งค่านั้น (มีเพียง `FASTI_INSECURE_DEMO=true` ที่เปิดอินสแตนซ์ให้เข้าถึงได้โดยไม่จำกัด สำหรับการสาธิต) |
| หยุดทำงานพร้อมข้อความ `Password does not match policy` | `--production` บังคับใช้นโยบายรหัสผ่านกับ `FASTI_ADMIN_PASSWORD`: อย่างน้อย 10 อักขระ โดยมีตัวอักษรพิมพ์ใหญ่และพิมพ์เล็ก ตัวเลข และอักขระพิเศษ |
| `password authentication failed` | รหัสผ่านในไฟล์ข้อมูลลับต่างจากรหัสผ่านของฐานข้อมูล ไฟล์ที่สร้างบน Windows อาจลงท้ายด้วย `\r\n`: สร้างไฟล์ใหม่ด้วย `tr -d '\r\n'` PostgreSQL อ่าน `POSTGRES_PASSWORD_FILE` เฉพาะเมื่อเริ่มต้นวอลุ่มเป็นครั้งแรก |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | ตั้งค่า `FASTI_ADMIN_PASSWORD` หรือ `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | ยังไม่ได้กำหนดค่าฐานข้อมูล; `--production` จะไม่เปลี่ยนไปใช้ฐานข้อมูล H2 สำหรับสาธิตโดยอัตโนมัติ: ตั้งค่า `DB_URL` ให้ชี้ไปยังฐานข้อมูลสำหรับใช้งานจริง |
| `… cannot be read` สำหรับตัวแปร `_FILE` | ไม่ได้เมานต์ไฟล์เข้าในคอนเทนเนอร์ หรือ uid 1000 อ่านไฟล์ไม่ได้ (ไฟล์ต้องมีโหมด `0644`; ไดเรกทอรีของไฟล์ยังคงใช้ `0700` ได้) |
| การลงชื่อเข้าใช้เว็บผ่าน HTTP ไม่คงเซสชันไว้ | คุกกี้ `Secure` (`--production`) จะส่งผ่าน HTTPS เท่านั้น (หรือไปยัง `localhost`): ให้สิ้นสุดการเชื่อมต่อ TLS ที่พร็อกซี |
| เชื่อมต่อกับ SQL Server ไม่ได้ (ข้อผิดพลาดเกี่ยวกับใบรับรอง) | กำหนดใบรับรองที่ถูกต้องให้ SQL Server โดยให้ตรงกับชื่อโฮสต์ใน URL; `trustServerCertificate=true` ใช้สำหรับการทดลองในเครื่องเท่านั้น |
| คอนเทนเนอร์เริ่มใหม่ซ้ำ ๆ และบันทึกแสดง `OutOfMemoryError` | JVM ออกจากการทำงานเมื่อหน่วยความจำหมด: เพิ่มขีดจำกัดหน่วยความจำของคอนเทนเนอร์ (2 GB) |

ยังแก้ไขไม่ได้ใช่ไหม? เปิด issue บน GitHub พร้อมบันทึก (ลบรหัสผ่านออกแล้ว) สำหรับปัญหาด้านความปลอดภัย: ดู
[SECURITY.md](../SECURITY.md#security-policy)

<a id="12-without-docker"></a>

## 12. การใช้งานโดยไม่ใช้ Docker

ชุด Tomcat 1.1.0 พร้อมใช้งาน (หน้าดาวน์โหลด: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
- [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

ตรวจสอบเช็กซัมก่อนแตกไฟล์ เช่น `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`
