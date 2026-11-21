/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Vietnamese messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Hồ sơ (CMMN)',
  'cmmn.nav.history': 'Lịch sử hồ sơ',

  'cmmn.definitions.title': 'Định nghĩa hồ sơ',
  'cmmn.definitions.count': '{count} định nghĩa hồ sơ',
  'cmmn.definitions.search': 'Tìm định nghĩa hồ sơ',
  'cmmn.definitions.noMatch': 'Không có định nghĩa hồ sơ nào khớp với từ khóa',
  'cmmn.definitions.empty': 'Chưa triển khai định nghĩa hồ sơ nào',
  'cmmn.definitions.emptyHint': 'Hồ sơ được mô hình hóa bằng CMMN và triển khai dưới dạng tệp .cmmn, ví dụ ở trang',
  'cmmn.definitions.open': 'Lượt chạy chưa đóng',

  'cmmn.definition.title': 'Định nghĩa hồ sơ',
  'cmmn.definition.notFound': 'Định nghĩa hồ sơ này không tồn tại',
  'cmmn.definition.instances': 'Lượt chạy chưa đóng của phiên bản này',
  'cmmn.definition.instancesEmpty': 'Phiên bản này không có lượt chạy nào chưa đóng',
  'cmmn.definition.businessKeySearch': 'Mã nghiệp vụ (chính xác)',
  'cmmn.definition.noMatch': 'Không có lượt chạy chưa đóng nào mang mã nghiệp vụ này',
  'cmmn.definition.diagramLabel': 'Sơ đồ hồ sơ với số mục kế hoạch đang mở',
  'cmmn.definition.diagramHint': 'Các con số cho biết bao nhiêu mục kế hoạch của các lượt chạy chưa đóng đang hoạt động hoặc chờ được bắt đầu.',
  'cmmn.definition.truncated': 'Các con số trên sơ đồ chỉ tính {count} mục kế hoạch đầu tiên.',

  'cmmn.create.action': 'Tạo lượt chạy hồ sơ',
  'cmmn.create.title': 'Tạo một lượt chạy hồ sơ',
  'cmmn.create.text': 'Tạo một lượt chạy của “{name}”, phiên bản {version}. Các mục kế hoạch bắt đầu ngay.',
  'cmmn.create.done': 'Đã tạo lượt chạy hồ sơ',
  'cmmn.create.failed': 'Không tạo được lượt chạy hồ sơ',

  'cmmn.instance': 'Lượt chạy hồ sơ',
  'cmmn.instance.gone': 'Lượt chạy hồ sơ này không tồn tại',
  'cmmn.instance.goneHint': 'ID có thể sai, hoặc lượt chạy đã đóng và lịch sử của nó đã bị xóa.',
  'cmmn.instance.closedNote': 'Lượt chạy hồ sơ này đã đóng. Nội dung hiển thị theo lịch sử đã lưu và không thể thay đổi.',
  'cmmn.instance.created': 'Tạo lúc',
  'cmmn.instance.closedAt': 'Đóng lúc',
  'cmmn.instance.backToDefinition': 'Quay lại định nghĩa hồ sơ',
  'cmmn.instance.parentProcess': 'Được khởi tạo bởi một lượt chạy tiến trình',
  'cmmn.instance.parentCase': 'Được khởi tạo bởi một lượt chạy hồ sơ',
  'cmmn.instance.diagramLabel': 'Sơ đồ hồ sơ với trạng thái của từng mục kế hoạch',
  'cmmn.instance.diagramHint': 'Chọn một mục kế hoạch trên sơ đồ để chỉ xem mục đó trong danh sách mục kế hoạch.',
  'cmmn.instance.tabs': 'Dữ liệu của lượt chạy hồ sơ',
  'cmmn.instance.tab.items': 'Mục kế hoạch',
  'cmmn.instance.tab.history': 'Lịch sử',
  'cmmn.instance.complete': 'Hoàn tất hồ sơ',
  'cmmn.instance.terminate': 'Chấm dứt hồ sơ',
  'cmmn.instance.close': 'Đóng hồ sơ',
  'cmmn.instance.completeTitle': 'Hoàn tất lượt chạy hồ sơ',
  'cmmn.instance.completeText':
    'Lượt chạy của “{name}” sẽ được hoàn tất. Chỉ làm được khi không còn mục kế hoạch nào đang hoạt động, và không thể hoàn tác.',
  'cmmn.instance.terminateTitle': 'Chấm dứt lượt chạy hồ sơ',
  'cmmn.instance.terminateText':
    'Lượt chạy của “{name}” và mọi mục kế hoạch chưa kết thúc sẽ bị chấm dứt; công việc đang mở bị xóa. Không thể hoàn tác.',
  'cmmn.instance.closeTitle': 'Đóng lượt chạy hồ sơ',
  'cmmn.instance.closeText':
    'Lượt chạy của “{name}” sẽ được đóng: nó rời khỏi danh sách đang chạy và chỉ còn trong lịch sử. Không thể hoàn tác.',
  'cmmn.instance.done.complete': 'Đã hoàn tất lượt chạy hồ sơ',
  'cmmn.instance.done.terminate': 'Đã chấm dứt lượt chạy hồ sơ',
  'cmmn.instance.done.close': 'Đã đóng lượt chạy hồ sơ',
  'cmmn.instance.failed': 'Không thay đổi được lượt chạy hồ sơ',

  'cmmn.state.active': 'Đang hoạt động',
  'cmmn.state.enabled': 'Sẵn sàng bắt đầu',
  'cmmn.state.disabled': 'Đã vô hiệu hóa',
  'cmmn.state.available': 'Đang chờ điều kiện',
  'cmmn.state.completed': 'Đã hoàn tất',
  'cmmn.state.terminated': 'Đã chấm dứt',
  'cmmn.state.closed': 'Đã đóng',

  'cmmn.type.casePlanModel': 'Mô hình kế hoạch hồ sơ',
  'cmmn.type.stage': 'Giai đoạn',
  'cmmn.type.humanTask': 'Công việc của người',
  'cmmn.type.processTask': 'Công việc tiến trình',
  'cmmn.type.caseTask': 'Công việc hồ sơ',
  'cmmn.type.decisionTask': 'Công việc quyết định',
  'cmmn.type.task': 'Công việc',
  'cmmn.type.milestone': 'Cột mốc',

  'cmmn.action.manualStart': 'Bắt đầu',
  'cmmn.action.complete': 'Hoàn tất',
  'cmmn.action.disable': 'Vô hiệu hóa',
  'cmmn.action.reenable': 'Bật lại',
  'cmmn.action.terminate': 'Chấm dứt',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'Mục kế hoạch',
  'cmmn.item.type': 'Loại',
  'cmmn.item.required': 'Bắt buộc',
  'cmmn.item.failed': 'Không thay đổi được “{name}”',
  'cmmn.item.done.manualStart': 'Đã bắt đầu “{name}”',
  'cmmn.item.done.complete': 'Đã hoàn tất “{name}”',
  'cmmn.item.done.disable': 'Đã vô hiệu hóa “{name}”',
  'cmmn.item.done.reenable': 'Đã bật lại “{name}”',
  'cmmn.item.done.terminate': 'Đã chấm dứt “{name}”',
  'cmmn.item.completeTitle': 'Hoàn tất mục kế hoạch',
  'cmmn.item.completeText':
    '“{name}” sẽ được hoàn tất mà không qua biểu mẫu; công việc đang mở của nó cũng được hoàn tất. Không thể hoàn tác.',
  'cmmn.item.terminateTitle': 'Chấm dứt mục kế hoạch',
  'cmmn.item.terminateText':
    '“{name}” và mọi thứ bên trong sẽ bị chấm dứt; công việc đang mở của nó bị xóa. Không thể hoàn tác.',
  'cmmn.items.empty': 'Không có mục kế hoạch nào đang mở',
  'cmmn.items.closed': 'Hồ sơ đã đóng không còn mục kế hoạch đang mở. Xem lịch sử để biết những gì đã diễn ra.',
  'cmmn.tasks.empty': 'Lượt chạy hồ sơ này không có công việc đang mở',

  'cmmn.history.title': 'Lịch sử hồ sơ',
  'cmmn.history.count': '{count} lượt chạy hồ sơ',
  'cmmn.history.search': 'Tìm theo mã nghiệp vụ',
  'cmmn.history.case': 'Hồ sơ',
  'cmmn.history.filter.all': 'Tất cả',
  'cmmn.history.filter.open': 'Chưa đóng',
  'cmmn.history.filter.closed': 'Đã đóng',
  'cmmn.history.empty': 'Lịch sử chưa có lượt chạy hồ sơ nào',
  'cmmn.history.noMatch': 'Không có lượt chạy hồ sơ nào khớp',
  'cmmn.history.itemsEmpty': 'Lịch sử không có mục kế hoạch nào của lượt chạy hồ sơ này',
  'cmmn.variables.fromHistory': 'Giá trị theo lịch sử đã lưu; hồ sơ đã đóng không thể thay đổi.',

  'cmmn.legend': 'Trạng thái trên sơ đồ',
  'cmmn.diagram.label': 'Sơ đồ hồ sơ',
  'cmmn.diagram.noLayout': 'Mô hình này không có bố cục sơ đồ, nên chỉ liệt kê các mục kế hoạch.',
  'cmmn.task.diagramLabel': 'Sơ đồ hồ sơ, mục kế hoạch của công việc được tô sáng',
  'cmmn.task.openCase': 'Mở hồ sơ trong trang giám sát',

  'cmmn.deployments.openCase': 'Mở hồ sơ “{name}” (phiên bản {version})',
  'palette.kind.case': 'Lượt chạy hồ sơ',
};

export default messages;
