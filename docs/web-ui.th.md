[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | ไทย | [简体中文](web-ui.zh-CN.md)

> คำแปลนี้ดูแลโดยชุมชนและอาจล้าหลังฉบับภาษาอังกฤษ ฉบับภาษาอังกฤษเป็นฉบับที่ใช้อ้างอิงหลัก ยินดีรับการแก้ไข

# เว็บอินเทอร์เฟซของ Fasti

เหตุใดเว็บอินเทอร์เฟซใหม่จึงมีฟีเจอร์เหล่านี้ และเว็บแอปพลิเคชันให้บริการที่พาธใด ภาพรวมของอินเทอร์เฟซ: <https://fasti.dev/#features>

<a id="a-ui-built-from-real-user-complaints"></a>

## UI ที่สร้างจากปัญหาที่ผู้ใช้พบจริง

ฟีเจอร์ของเว็บ UI ใหม่คัดเลือกจากสิ่งที่ผู้ใช้ Camunda 7 เขียนไว้สาธารณะบน
ฟอรัม Camunda และ GitHub ตัวอย่างเช่น:

| สิ่งที่ผู้ใช้ร้องขอ | แหล่งข้อมูล | ใน Fasti |
|---|---|---|
| ค้นหาอินสแตนซ์ด้วยตัวแปรหลายตัว โดยใช้ OR | ฟอรัม [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | เครื่องมือสร้างเงื่อนไขหลายตัวแปร AND/OR |
| ดูความคิดเห็นในทุกขั้นตอนของเคส | ฟอรัม [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | ไทม์ไลน์ของเคสในงาน; ความคิดเห็นบนอินสแตนซ์กระบวนการโดยตรง |
| เรียงงานตามตัวแปร โดยให้งานเก่าที่สุดขึ้นก่อน | ฟอรัม [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | เรียงตามตัวแปร วันครบกำหนด และความเร่งด่วน |
| มอบหมายงานต่อและกำหนดผู้ปฏิบัติงานแทนระหว่างไม่อยู่ | ฟอรัม [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | มอบหมายงานต่อทีละงาน และ[ผู้ปฏิบัติงานแทนระหว่างลา](absence.md) พร้อมการส่งมอบงาน (ตั้งแต่ 1.1.0) |
| ระบุคีย์ธุรกิจและตัวแปรเมื่อเริ่มกระบวนการโดยไม่มีฟอร์ม | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | รองรับ |
| ดาวน์โหลด BPMN XML ที่ปรับใช้แล้ว | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | รองรับ |
| ข้อความกำกับที่สอดคล้องกันและรองรับหลายภาษา | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 ภาษา; ภายในตัวแก้ไข BPMN, DMN และฟอร์ม ข้อความกำกับในตัวของตัวแก้ไขมีเฉพาะภาษาอังกฤษและภาษาเวียดนาม |

<a id="web-application-path-fasti"></a>

## พาธเว็บแอปพลิเคชัน: `/fasti`

Cockpit, Tasklist, Admin และเว็บ UI ใหม่ให้บริการภายใต้ `/fasti` (เดิมคือ `/camunda`)
ในทุกชุดแจกจ่าย: `http://localhost:8080/fasti/` เปิด UI ใหม่
(`/fasti/app/next/default/`) ส่วนแอปพลิเคชันแบบคลาสสิกอยู่ที่ `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` และ `/fasti/app/admin/...` REST API ยังคงอยู่ที่ `/engine-rest`

**แอปพลิเคชันแบบคลาสสิกเลิกแนะนำให้ใช้แล้ว** Cockpit, Tasklist, Admin และ Welcome แบบคลาสสิก
สร้างบน AngularJS 1.8.2 และ Bootstrap 3 ซึ่งทั้งคู่สิ้นสุดอายุการใช้งานแล้ว **อิมเมจคอนเทนเนอร์ไม่
ให้บริการแอปเหล่านี้โดยค่าเริ่มต้น** (การเปลี่ยนแปลงที่กระทบความเข้ากันได้): หน้าเว็บและบุ๊กมาร์กเดิมจะเปลี่ยนเส้นทางไปยังส่วนที่ตรงกันของ UI ใหม่ และคำขอไฟล์ของแอปเหล่านี้จะได้รับ `404`; `FASTI_CLASSIC_WEBAPPS=true` จะเปิดให้ใช้งาน
อีกครั้ง (พร้อมคำเตือน) ดู [distro/docker/README.md](../distro/docker/README.md) ชุด
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
