/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Thai messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'เคส (CMMN)',
  'cmmn.nav.history': 'ประวัติเคส',

  'cmmn.definitions.title': 'นิยามเคส',
  'cmmn.definitions.count': 'นิยามเคส {count} รายการ',
  'cmmn.definitions.search': 'ค้นหานิยามเคส',
  'cmmn.definitions.noMatch': 'ไม่มีนิยามเคสที่ตรงกับการค้นหา',
  'cmmn.definitions.empty': 'ยังไม่มีนิยามเคสที่ดีพลอย',
  'cmmn.definitions.emptyHint': 'เคสสร้างแบบจำลองด้วย CMMN และดีพลอยเป็นไฟล์ .cmmn เช่น ที่หน้า',
  'cmmn.definitions.open': 'อินสแตนซ์ที่ยังไม่ปิด',

  'cmmn.definition.title': 'นิยามเคส',
  'cmmn.definition.notFound': 'ไม่มีนิยามเคสนี้',
  'cmmn.definition.instances': 'อินสแตนซ์ที่ยังไม่ปิดของเวอร์ชันนี้',
  'cmmn.definition.instancesEmpty': 'เวอร์ชันนี้ไม่มีอินสแตนซ์ที่ยังไม่ปิด',
  'cmmn.definition.businessKeySearch': 'คีย์ทางธุรกิจ (ตรงทั้งหมด)',
  'cmmn.definition.noMatch': 'ไม่มีอินสแตนซ์ที่ยังไม่ปิดที่มีคีย์ทางธุรกิจนี้',
  'cmmn.definition.diagramLabel': 'แผนภาพเคสพร้อมจำนวนรายการแผนที่ยังเปิดอยู่',
  'cmmn.definition.diagramHint': 'ตัวเลขแสดงจำนวนรายการแผนของอินสแตนซ์ที่ยังไม่ปิดซึ่งกำลังทำงานหรือรอเริ่ม',
  'cmmn.definition.truncated': 'ตัวเลขบนแผนภาพนับเฉพาะรายการแผน {count} รายการแรก',

  'cmmn.create.action': 'สร้างอินสแตนซ์เคส',
  'cmmn.create.title': 'สร้างอินสแตนซ์เคส',
  'cmmn.create.text': 'สร้างอินสแตนซ์ของ “{name}” เวอร์ชัน {version} รายการแผนจะเริ่มทันที',
  'cmmn.create.done': 'สร้างอินสแตนซ์เคสแล้ว',
  'cmmn.create.failed': 'สร้างอินสแตนซ์เคสไม่สำเร็จ',

  'cmmn.instance': 'อินสแตนซ์เคส',
  'cmmn.instance.gone': 'ไม่มีอินสแตนซ์เคสนี้',
  'cmmn.instance.goneHint': 'ID อาจไม่ถูกต้อง หรืออินสแตนซ์ถูกปิดและประวัติถูกลบไปแล้ว',
  'cmmn.instance.closedNote': 'อินสแตนซ์เคสนี้ปิดแล้ว แสดงตามที่ประวัติบันทึกไว้และแก้ไขไม่ได้',
  'cmmn.instance.created': 'สร้างเมื่อ',
  'cmmn.instance.closedAt': 'ปิดเมื่อ',
  'cmmn.instance.backToDefinition': 'กลับไปที่นิยามเคส',
  'cmmn.instance.parentProcess': 'เริ่มโดยอินสแตนซ์กระบวนการ',
  'cmmn.instance.parentCase': 'เริ่มโดยอินสแตนซ์เคส',
  'cmmn.instance.diagramLabel': 'แผนภาพเคสพร้อมสถานะของแต่ละรายการแผน',
  'cmmn.instance.diagramHint': 'เลือกรายการแผนบนแผนภาพเพื่อดูเฉพาะรายการนั้นในรายการแผน',
  'cmmn.instance.tabs': 'ข้อมูลของอินสแตนซ์เคส',
  'cmmn.instance.tab.items': 'รายการแผน',
  'cmmn.instance.tab.history': 'ประวัติ',
  'cmmn.instance.complete': 'ทำเคสให้เสร็จ',
  'cmmn.instance.terminate': 'ยุติเคส',
  'cmmn.instance.close': 'ปิดเคส',
  'cmmn.instance.completeTitle': 'ทำอินสแตนซ์เคสให้เสร็จ',
  'cmmn.instance.completeText':
    'อินสแตนซ์ของ “{name}” จะเสร็จสิ้น ทำได้เมื่อไม่มีรายการแผนที่ทำงานอยู่แล้วเท่านั้น และย้อนกลับไม่ได้',
  'cmmn.instance.terminateTitle': 'ยุติอินสแตนซ์เคส',
  'cmmn.instance.terminateText':
    'อินสแตนซ์ของ “{name}” และรายการแผนทั้งหมดที่ยังไม่จบจะถูกยุติ งานที่เปิดอยู่จะถูกลบ ย้อนกลับไม่ได้',
  'cmmn.instance.closeTitle': 'ปิดอินสแตนซ์เคส',
  'cmmn.instance.closeText':
    'อินสแตนซ์ของ “{name}” จะถูกปิด: ออกจากอินสแตนซ์ที่กำลังทำงานและเก็บไว้ในประวัติเท่านั้น ย้อนกลับไม่ได้',
  'cmmn.instance.done.complete': 'อินสแตนซ์เคสเสร็จสิ้นแล้ว',
  'cmmn.instance.done.terminate': 'ยุติอินสแตนซ์เคสแล้ว',
  'cmmn.instance.done.close': 'ปิดอินสแตนซ์เคสแล้ว',
  'cmmn.instance.failed': 'เปลี่ยนแปลงอินสแตนซ์เคสไม่สำเร็จ',

  'cmmn.state.active': 'ใช้งานอยู่',
  'cmmn.state.enabled': 'พร้อมเริ่ม',
  'cmmn.state.disabled': 'ปิดใช้งาน',
  'cmmn.state.available': 'รอเงื่อนไข',
  'cmmn.state.completed': 'เสร็จสิ้น',
  'cmmn.state.terminated': 'ยุติแล้ว',
  'cmmn.state.closed': 'ปิดแล้ว',

  'cmmn.type.casePlanModel': 'แบบจำลองแผนเคส',
  'cmmn.type.stage': 'ขั้นตอน',
  'cmmn.type.humanTask': 'งานของคน',
  'cmmn.type.processTask': 'งานกระบวนการ',
  'cmmn.type.caseTask': 'งานเคส',
  'cmmn.type.decisionTask': 'งานการตัดสินใจ',
  'cmmn.type.task': 'งาน',
  'cmmn.type.milestone': 'หมุดหมาย',

  'cmmn.action.manualStart': 'เริ่ม',
  'cmmn.action.complete': 'ทำให้เสร็จ',
  'cmmn.action.disable': 'ปิดใช้งาน',
  'cmmn.action.reenable': 'เปิดใช้งานอีกครั้ง',
  'cmmn.action.terminate': 'ยุติ',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'รายการแผน',
  'cmmn.item.type': 'ประเภท',
  'cmmn.item.required': 'จำเป็น',
  'cmmn.item.failed': 'เปลี่ยนแปลง “{name}” ไม่สำเร็จ',
  'cmmn.item.done.manualStart': 'เริ่ม “{name}” แล้ว',
  'cmmn.item.done.complete': '“{name}” เสร็จสิ้นแล้ว',
  'cmmn.item.done.disable': 'ปิดใช้งาน “{name}” แล้ว',
  'cmmn.item.done.reenable': 'เปิดใช้งาน “{name}” อีกครั้งแล้ว',
  'cmmn.item.done.terminate': 'ยุติ “{name}” แล้ว',
  'cmmn.item.completeTitle': 'ทำรายการแผนให้เสร็จ',
  'cmmn.item.completeText':
    '“{name}” จะเสร็จสิ้นโดยไม่ผ่านฟอร์ม งานที่เปิดอยู่ของรายการนี้ก็จะเสร็จสิ้นด้วย ย้อนกลับไม่ได้',
  'cmmn.item.terminateTitle': 'ยุติรายการแผน',
  'cmmn.item.terminateText':
    '“{name}” และทุกอย่างที่อยู่ภายในจะถูกยุติ งานที่เปิดอยู่จะถูกลบ ย้อนกลับไม่ได้',
  'cmmn.items.empty': 'ไม่มีรายการแผนที่เปิดอยู่',
  'cmmn.items.closed': 'เคสที่ปิดแล้วไม่มีรายการแผนที่เปิดอยู่ ดูสิ่งที่เกิดขึ้นได้ในประวัติ',
  'cmmn.tasks.empty': 'อินสแตนซ์เคสนี้ไม่มีงานที่เปิดอยู่',

  'cmmn.history.title': 'ประวัติเคส',
  'cmmn.history.count': 'อินสแตนซ์เคส {count} รายการ',
  'cmmn.history.search': 'ค้นหาตามคีย์ทางธุรกิจ',
  'cmmn.history.case': 'เคส',
  'cmmn.history.filter.all': 'ทั้งหมด',
  'cmmn.history.filter.open': 'ยังไม่ปิด',
  'cmmn.history.filter.closed': 'ปิดแล้ว',
  'cmmn.history.empty': 'ประวัติยังไม่มีอินสแตนซ์เคส',
  'cmmn.history.noMatch': 'ไม่มีอินสแตนซ์เคสที่ตรงกัน',
  'cmmn.history.itemsEmpty': 'ประวัติไม่มีรายการแผนของอินสแตนซ์เคสนี้',
  'cmmn.variables.fromHistory': 'ค่าตามที่ประวัติบันทึกไว้ เคสที่ปิดแล้วแก้ไขไม่ได้',

  'cmmn.legend': 'สถานะบนแผนภาพ',
  'cmmn.diagram.label': 'แผนภาพเคส',
  'cmmn.diagram.noLayout': 'แบบจำลองนี้ไม่มีเลย์เอาต์แผนภาพ จึงแสดงเฉพาะรายการแผน',
  'cmmn.task.diagramLabel': 'แผนภาพเคส รายการแผนของงานถูกเน้นไว้',
  'cmmn.task.openCase': 'เปิดเคสใน Cockpit',

  'palette.kind.case': 'อินสแตนซ์เคส',
};

export default messages;
