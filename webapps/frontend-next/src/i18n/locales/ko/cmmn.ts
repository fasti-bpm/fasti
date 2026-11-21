/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Korean messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': '케이스 (CMMN)',
  'cmmn.nav.history': '케이스 이력',

  'cmmn.definitions.title': '케이스 정의',
  'cmmn.definitions.count': '케이스 정의 {count}개',
  'cmmn.definitions.search': '케이스 정의 검색',
  'cmmn.definitions.noMatch': '검색과 일치하는 케이스 정의가 없습니다',
  'cmmn.definitions.empty': '배포된 케이스 정의가 없습니다',
  'cmmn.definitions.emptyHint': '케이스는 CMMN으로 모델링하고 .cmmn 파일로 배포합니다. 예를 들어 다음 페이지에서:',
  'cmmn.definitions.open': '닫히지 않은 인스턴스',

  'cmmn.definition.title': '케이스 정의',
  'cmmn.definition.notFound': '이 케이스 정의는 존재하지 않습니다',
  'cmmn.definition.instances': '이 버전의 닫히지 않은 인스턴스',
  'cmmn.definition.instancesEmpty': '이 버전에는 닫히지 않은 인스턴스가 없습니다',
  'cmmn.definition.businessKeySearch': '비즈니스 키 (정확히 일치)',
  'cmmn.definition.noMatch': '이 비즈니스 키를 가진 닫히지 않은 인스턴스가 없습니다',
  'cmmn.definition.diagramLabel': '열린 계획 항목 수를 표시한 케이스 다이어그램',
  'cmmn.definition.diagramHint': '숫자는 닫히지 않은 인스턴스에서 활성 상태이거나 시작을 기다리는 계획 항목 수입니다.',
  'cmmn.definition.truncated': '다이어그램의 숫자는 처음 {count}개의 계획 항목만 셉니다.',

  'cmmn.create.action': '케이스 인스턴스 생성',
  'cmmn.create.title': '케이스 인스턴스 생성',
  'cmmn.create.text': '“{name}” 버전 {version}의 인스턴스를 생성합니다. 계획 항목은 바로 시작됩니다.',
  'cmmn.create.done': '케이스 인스턴스를 생성했습니다',
  'cmmn.create.failed': '케이스 인스턴스를 생성할 수 없습니다',

  'cmmn.instance': '케이스 인스턴스',
  'cmmn.instance.gone': '이 케이스 인스턴스는 존재하지 않습니다',
  'cmmn.instance.goneHint': 'ID가 잘못되었거나, 인스턴스가 닫힌 뒤 이력이 삭제되었을 수 있습니다.',
  'cmmn.instance.closedNote': '이 케이스 인스턴스는 닫혔습니다. 이력에 저장된 대로 표시되며 변경할 수 없습니다.',
  'cmmn.instance.created': '생성됨',
  'cmmn.instance.closedAt': '닫힘',
  'cmmn.instance.backToDefinition': '케이스 정의로 돌아가기',
  'cmmn.instance.parentProcess': '프로세스 인스턴스에서 시작됨',
  'cmmn.instance.parentCase': '케이스 인스턴스에서 시작됨',
  'cmmn.instance.diagramLabel': '각 계획 항목의 상태를 표시한 케이스 다이어그램',
  'cmmn.instance.diagramHint': '다이어그램에서 계획 항목을 선택하면 계획 항목 목록에 그 항목만 표시됩니다.',
  'cmmn.instance.tabs': '케이스 인스턴스 데이터',
  'cmmn.instance.tab.items': '계획 항목',
  'cmmn.instance.tab.history': '이력',
  'cmmn.instance.complete': '케이스 완료',
  'cmmn.instance.terminate': '케이스 종료',
  'cmmn.instance.close': '케이스 닫기',
  'cmmn.instance.completeTitle': '케이스 인스턴스 완료',
  'cmmn.instance.completeText':
    '“{name}”의 인스턴스를 완료합니다. 활성 계획 항목이 더 이상 없을 때만 가능하며 되돌릴 수 없습니다.',
  'cmmn.instance.terminateTitle': '케이스 인스턴스 종료',
  'cmmn.instance.terminateText':
    '“{name}”의 인스턴스와 끝나지 않은 모든 계획 항목을 종료하고, 열린 태스크는 삭제됩니다. 되돌릴 수 없습니다.',
  'cmmn.instance.closeTitle': '케이스 인스턴스 닫기',
  'cmmn.instance.closeText':
    '“{name}”의 인스턴스를 닫습니다. 실행 중인 인스턴스에서 빠지고 이력에만 남습니다. 되돌릴 수 없습니다.',
  'cmmn.instance.done.complete': '케이스 인스턴스를 완료했습니다',
  'cmmn.instance.done.terminate': '케이스 인스턴스를 종료했습니다',
  'cmmn.instance.done.close': '케이스 인스턴스를 닫았습니다',
  'cmmn.instance.failed': '케이스 인스턴스를 변경할 수 없습니다',

  'cmmn.state.active': '활성',
  'cmmn.state.enabled': '시작 대기',
  'cmmn.state.disabled': '비활성화됨',
  'cmmn.state.available': '조건 대기',
  'cmmn.state.completed': '완료됨',
  'cmmn.state.terminated': '종료됨',
  'cmmn.state.closed': '닫힘',

  'cmmn.type.casePlanModel': '케이스 계획 모델',
  'cmmn.type.stage': '단계',
  'cmmn.type.humanTask': '휴먼 태스크',
  'cmmn.type.processTask': '프로세스 태스크',
  'cmmn.type.caseTask': '케이스 태스크',
  'cmmn.type.decisionTask': '결정 태스크',
  'cmmn.type.task': '태스크',
  'cmmn.type.milestone': '마일스톤',

  'cmmn.action.manualStart': '시작',
  'cmmn.action.complete': '완료',
  'cmmn.action.disable': '비활성화',
  'cmmn.action.reenable': '다시 활성화',
  'cmmn.action.terminate': '종료',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': '계획 항목',
  'cmmn.item.type': '유형',
  'cmmn.item.required': '필수',
  'cmmn.item.failed': '“{name}”을(를) 변경할 수 없습니다',
  'cmmn.item.done.manualStart': '“{name}” 시작됨',
  'cmmn.item.done.complete': '“{name}” 완료됨',
  'cmmn.item.done.disable': '“{name}” 비활성화됨',
  'cmmn.item.done.reenable': '“{name}” 다시 활성화됨',
  'cmmn.item.done.terminate': '“{name}” 종료됨',
  'cmmn.item.completeTitle': '계획 항목 완료',
  'cmmn.item.completeText':
    '“{name}”을(를) 폼 없이 완료합니다. 열린 태스크도 함께 완료됩니다. 되돌릴 수 없습니다.',
  'cmmn.item.terminateTitle': '계획 항목 종료',
  'cmmn.item.terminateText':
    '“{name}”과(와) 그 안의 모든 것을 종료하고, 열린 태스크는 삭제됩니다. 되돌릴 수 없습니다.',
  'cmmn.items.empty': '열린 계획 항목이 없습니다',
  'cmmn.items.closed': '닫힌 케이스에는 열린 계획 항목이 없습니다. 이력에서 진행 내용을 볼 수 있습니다.',
  'cmmn.tasks.empty': '이 케이스 인스턴스에 열린 태스크가 없습니다',

  'cmmn.history.title': '케이스 이력',
  'cmmn.history.count': '케이스 인스턴스 {count}개',
  'cmmn.history.search': '비즈니스 키로 검색',
  'cmmn.history.case': '케이스',
  'cmmn.history.filter.all': '전체',
  'cmmn.history.filter.open': '닫히지 않음',
  'cmmn.history.filter.closed': '닫힘',
  'cmmn.history.empty': '이력에 아직 케이스 인스턴스가 없습니다',
  'cmmn.history.noMatch': '일치하는 케이스 인스턴스가 없습니다',
  'cmmn.history.itemsEmpty': '이력에 이 케이스 인스턴스의 계획 항목이 없습니다',
  'cmmn.variables.fromHistory': '이력에 저장된 값입니다. 닫힌 케이스는 변경할 수 없습니다.',

  'cmmn.legend': '다이어그램의 상태',
  'cmmn.diagram.label': '케이스 다이어그램',
  'cmmn.diagram.noLayout': '이 모델에는 다이어그램 레이아웃이 없어 계획 항목 목록만 표시합니다.',
  'cmmn.task.diagramLabel': '케이스 다이어그램, 태스크의 계획 항목이 강조 표시됨',
  'cmmn.task.openCase': 'Cockpit에서 케이스 열기',

  'palette.kind.case': '케이스 인스턴스',
};

export default messages;
