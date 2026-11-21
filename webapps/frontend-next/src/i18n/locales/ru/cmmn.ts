/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Russian messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Кейсы (CMMN)',
  'cmmn.nav.history': 'История кейсов',

  'cmmn.definitions.title': 'Определения кейсов',
  'cmmn.definitions.count': '{count} определений кейсов',
  'cmmn.definitions.count.one': '{count} определение кейса',
  'cmmn.definitions.count.few': '{count} определения кейсов',
  'cmmn.definitions.count.many': '{count} определений кейсов',
  'cmmn.definitions.search': 'Поиск определений кейсов',
  'cmmn.definitions.noMatch': 'Нет определений кейсов, подходящих под поиск',
  'cmmn.definitions.empty': 'Определения кейсов не развёрнуты',
  'cmmn.definitions.emptyHint': 'Кейсы моделируются в CMMN и развёртываются как файлы .cmmn, например на странице',
  'cmmn.definitions.open': 'Открытые экземпляры',

  'cmmn.definition.title': 'Определение кейса',
  'cmmn.definition.notFound': 'Такого определения кейса нет',
  'cmmn.definition.instances': 'Открытые экземпляры этой версии',
  'cmmn.definition.instancesEmpty': 'У этой версии нет открытых экземпляров',
  'cmmn.definition.businessKeySearch': 'Бизнес-ключ (точно)',
  'cmmn.definition.noMatch': 'Нет открытого экземпляра с таким бизнес-ключом',
  'cmmn.definition.diagramLabel': 'Диаграмма кейса с числом открытых элементов плана',
  'cmmn.definition.diagramHint': 'Числа показывают, сколько элементов плана открытых экземпляров активны или ждут запуска.',
  'cmmn.definition.truncated': 'Числа на диаграмме учитывают только первые {count} элементов плана.',

  'cmmn.create.action': 'Создать экземпляр кейса',
  'cmmn.create.title': 'Создание экземпляра кейса',
  'cmmn.create.text': 'Создаёт экземпляр «{name}», версия {version}. Его элементы плана запускаются сразу.',
  'cmmn.create.done': 'Экземпляр кейса создан',
  'cmmn.create.failed': 'Не удалось создать экземпляр кейса',

  'cmmn.instance': 'Экземпляр кейса',
  'cmmn.instance.gone': 'Такого экземпляра кейса нет',
  'cmmn.instance.goneHint': 'Возможно, ID неверен, или экземпляр закрыт и его история удалена.',
  'cmmn.instance.closedNote': 'Этот экземпляр кейса закрыт. Он показан так, как его сохранила история, и не может быть изменён.',
  'cmmn.instance.created': 'Создан',
  'cmmn.instance.closedAt': 'Закрыт',
  'cmmn.instance.backToDefinition': 'Назад к определению кейса',
  'cmmn.instance.parentProcess': 'Запущен экземпляром процесса',
  'cmmn.instance.parentCase': 'Запущен экземпляром кейса',
  'cmmn.instance.diagramLabel': 'Диаграмма кейса с состоянием каждого элемента плана',
  'cmmn.instance.diagramHint': 'Выберите элемент плана на диаграмме, чтобы увидеть в списке только его.',
  'cmmn.instance.tabs': 'Данные экземпляра кейса',
  'cmmn.instance.tab.items': 'Элементы плана',
  'cmmn.instance.tab.history': 'История',
  'cmmn.instance.complete': 'Завершить кейс',
  'cmmn.instance.terminate': 'Прервать кейс',
  'cmmn.instance.close': 'Закрыть кейс',
  'cmmn.instance.completeTitle': 'Завершение экземпляра кейса',
  'cmmn.instance.completeText':
    'Экземпляр «{name}» будет завершён. Это возможно, только если ни один элемент плана больше не активен, и не может быть отменено.',
  'cmmn.instance.terminateTitle': 'Прерывание экземпляра кейса',
  'cmmn.instance.terminateText':
    'Экземпляр «{name}» и все его незавершённые элементы плана будут прерваны; открытые задачи удаляются. Это нельзя отменить.',
  'cmmn.instance.closeTitle': 'Закрытие экземпляра кейса',
  'cmmn.instance.closeText':
    'Экземпляр «{name}» будет закрыт: он покинет выполняющиеся экземпляры, и его сохранит только история. Это нельзя отменить.',
  'cmmn.instance.done.complete': 'Экземпляр кейса завершён',
  'cmmn.instance.done.terminate': 'Экземпляр кейса прерван',
  'cmmn.instance.done.close': 'Экземпляр кейса закрыт',
  'cmmn.instance.failed': 'Не удалось изменить экземпляр кейса',

  'cmmn.state.active': 'Активен',
  'cmmn.state.enabled': 'Готов к запуску',
  'cmmn.state.disabled': 'Отключён',
  'cmmn.state.available': 'Ждёт условия',
  'cmmn.state.completed': 'Завершён',
  'cmmn.state.terminated': 'Прерван',
  'cmmn.state.closed': 'Закрыт',

  'cmmn.type.casePlanModel': 'Модель плана кейса',
  'cmmn.type.stage': 'Этап',
  'cmmn.type.humanTask': 'Задача человека',
  'cmmn.type.processTask': 'Задача процесса',
  'cmmn.type.caseTask': 'Задача кейса',
  'cmmn.type.decisionTask': 'Задача решения',
  'cmmn.type.task': 'Задача',
  'cmmn.type.milestone': 'Веха',

  'cmmn.action.manualStart': 'Запустить',
  'cmmn.action.complete': 'Завершить',
  'cmmn.action.disable': 'Отключить',
  'cmmn.action.reenable': 'Включить снова',
  'cmmn.action.terminate': 'Прервать',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'Элемент плана',
  'cmmn.item.type': 'Тип',
  'cmmn.item.required': 'Обязательный',
  'cmmn.item.failed': 'Не удалось изменить «{name}»',
  'cmmn.item.done.manualStart': '«{name}» запущен',
  'cmmn.item.done.complete': '«{name}» завершён',
  'cmmn.item.done.disable': '«{name}» отключён',
  'cmmn.item.done.reenable': '«{name}» снова включён',
  'cmmn.item.done.terminate': '«{name}» прерван',
  'cmmn.item.completeTitle': 'Завершение элемента плана',
  'cmmn.item.completeText':
    '«{name}» будет завершён без формы; его открытая задача тоже будет завершена. Это нельзя отменить.',
  'cmmn.item.terminateTitle': 'Прерывание элемента плана',
  'cmmn.item.terminateText':
    '«{name}» и всё, что в нём содержится, будет прервано; его открытые задачи удаляются. Это нельзя отменить.',
  'cmmn.items.empty': 'Нет открытых элементов плана',
  'cmmn.items.closed': 'У закрытого кейса нет открытых элементов плана. История показывает, что произошло.',
  'cmmn.tasks.empty': 'У этого экземпляра кейса нет открытых задач',

  'cmmn.history.title': 'История кейсов',
  'cmmn.history.count': '{count} экземпляров кейсов',
  'cmmn.history.count.one': '{count} экземпляр кейса',
  'cmmn.history.count.few': '{count} экземпляра кейсов',
  'cmmn.history.count.many': '{count} экземпляров кейсов',
  'cmmn.history.search': 'Поиск по бизнес-ключу',
  'cmmn.history.case': 'Кейс',
  'cmmn.history.filter.all': 'Все',
  'cmmn.history.filter.open': 'Не закрытые',
  'cmmn.history.filter.closed': 'Закрытые',
  'cmmn.history.empty': 'В истории пока нет экземпляров кейсов',
  'cmmn.history.noMatch': 'Нет подходящих экземпляров кейсов',
  'cmmn.history.itemsEmpty': 'В истории нет элементов плана этого экземпляра кейса',
  'cmmn.variables.fromHistory': 'Значения в том виде, в каком их сохранила история; закрытый кейс нельзя изменить.',

  'cmmn.legend': 'Состояния на диаграмме',
  'cmmn.diagram.label': 'Диаграмма кейса',
  'cmmn.diagram.noLayout': 'У этой модели нет разметки диаграммы, поэтому показан только список элементов плана.',
  'cmmn.task.diagramLabel': 'Диаграмма кейса, элемент плана задачи выделен',
  'cmmn.task.openCase': 'Открыть кейс в Cockpit',

  'palette.kind.case': 'Экземпляр кейса',
};

export default messages;
