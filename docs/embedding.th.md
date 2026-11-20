[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | ไทย | [简体中文](embedding.zh-CN.md)

> คำแปลนี้ดูแลโดยชุมชนและอาจล้าหลังฉบับภาษาอังกฤษ ฉบับภาษาอังกฤษเป็นฉบับที่ใช้อ้างอิงหลัก ยินดีรับการแก้ไข

# การฝัง Fasti BPM: ความเข้ากันได้และพิกัด Maven

สิ่งที่ Fasti คงไว้จาก Camunda 7 และสิ่งที่แตกต่างจาก 7.24.0 วิธีกำหนดหมายเลขเวอร์ชัน และพิกัด Maven สำหรับแอปพลิเคชันที่ฝังเอนจิน หากต้องการรัน Fasti เป็นเซิร์ฟเวอร์ ดู [การติดตั้งด้วย Docker](install-docker.th.md) หรือชุด Tomcat ที่ <https://fasti.dev/download>

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
[คู่มือการย้ายระบบ](migrate-from-camunda-7.th.md#what-differs) ก่อนอัปเกรด

<a id="versioning"></a>

## การกำหนดเวอร์ชัน

Fasti BPM Platform ใช้[การกำหนดเวอร์ชันเชิงความหมาย](https://semver.org/) โดยเริ่มที่ `1.0.0`
สาย `1.x` คง Java API, แพ็กเกจ `org.camunda.bpm.*`, คำนำหน้าการกำหนดค่า `camunda.bpm.*` และ
สคีมาฐานข้อมูลของ Camunda 7.24 ไว้ แอปพลิเคชันที่ฝังเอนจินต้องย้ายไปใช้ Java 17 ขึ้นไป และหากใช้
Spring Boot starter ต้องย้ายไปใช้ Spring Boot 4.1 ส่วนปลั๊กอินและแอปพลิเคชันกระบวนการต้องสร้างใหม่
โดยใช้อาร์ติแฟกต์ `dev.fasti.platform`

<a id="maven-coordinates"></a>

## พิกัด Maven

อาร์ติแฟกต์ของ Fasti BPM Platform มีพิกัด Maven ของตนเอง **แพ็กเกจ Java
ยังคงเป็น `org.camunda.bpm.*`** ดังนั้นโค้ดแอปพลิเคชันจึงคงคำสั่ง import เดิมไว้ได้; คำนำหน้าการกำหนดค่า `camunda.bpm.*`,
เนมสเปซ BPMN/DMN, สคีมาฐานข้อมูล และชื่อโมดูลบน
WildFly ก็ไม่เปลี่ยนแปลงเช่นกัน ในส่วนของ Maven มีเพียงการประกาศ dependency เท่านั้น
ที่เปลี่ยน (พาธเว็บแอปพลิเคชันเปลี่ยนแยกต่างหาก ดู
[พาธเว็บแอปพลิเคชัน](web-ui.th.md#web-application-path-fasti)):

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
