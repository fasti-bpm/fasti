/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Japanese messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'ケース (CMMN)',
  'cmmn.nav.history': 'ケース履歴',

  'cmmn.definitions.title': 'ケース定義',
  'cmmn.definitions.count': 'ケース定義 {count} 件',
  'cmmn.definitions.search': 'ケース定義を検索',
  'cmmn.definitions.noMatch': '検索に一致するケース定義はありません',
  'cmmn.definitions.empty': 'デプロイされたケース定義はありません',
  'cmmn.definitions.emptyHint': 'ケースは CMMN でモデル化し、.cmmn ファイルとしてデプロイします。たとえば次のページから:',
  'cmmn.definitions.open': '未クローズのインスタンス',

  'cmmn.definition.title': 'ケース定義',
  'cmmn.definition.notFound': 'このケース定義は存在しません',
  'cmmn.definition.instances': 'このバージョンの未クローズのインスタンス',
  'cmmn.definition.instancesEmpty': 'このバージョンに未クローズのインスタンスはありません',
  'cmmn.definition.businessKeySearch': 'ビジネスキー (完全一致)',
  'cmmn.definition.noMatch': 'このビジネスキーを持つ未クローズのインスタンスはありません',
  'cmmn.definition.diagramLabel': '未完了のプランアイテム数を表示したケース図',
  'cmmn.definition.diagramHint': '数字は、未クローズのインスタンスでアクティブまたは開始待ちのプランアイテムの数です。',
  'cmmn.definition.truncated': '図の数字は最初の {count} 件のプランアイテムのみを数えています。',

  'cmmn.create.action': 'ケースインスタンスを作成',
  'cmmn.create.title': 'ケースインスタンスの作成',
  'cmmn.create.text': '「{name}」バージョン {version} のインスタンスを作成します。プランアイテムはすぐに開始されます。',
  'cmmn.create.done': 'ケースインスタンスを作成しました',
  'cmmn.create.failed': 'ケースインスタンスを作成できませんでした',

  'cmmn.instance': 'ケースインスタンス',
  'cmmn.instance.gone': 'このケースインスタンスは存在しません',
  'cmmn.instance.goneHint': 'ID が誤っているか、インスタンスがクローズされて履歴が削除された可能性があります。',
  'cmmn.instance.closedNote': 'このケースインスタンスはクローズ済みです。履歴に保存された内容を表示しており、変更できません。',
  'cmmn.instance.created': '作成日時',
  'cmmn.instance.closedAt': 'クローズ日時',
  'cmmn.instance.backToDefinition': 'ケース定義に戻る',
  'cmmn.instance.parentProcess': 'プロセスインスタンスから開始',
  'cmmn.instance.parentCase': 'ケースインスタンスから開始',
  'cmmn.instance.diagramLabel': '各プランアイテムの状態を表示したケース図',
  'cmmn.instance.diagramHint': '図でプランアイテムを選ぶと、プランアイテムの一覧にそれだけが表示されます。',
  'cmmn.instance.tabs': 'ケースインスタンスのデータ',
  'cmmn.instance.tab.items': 'プランアイテム',
  'cmmn.instance.tab.history': '履歴',
  'cmmn.instance.complete': 'ケースを完了',
  'cmmn.instance.terminate': 'ケースを終了',
  'cmmn.instance.close': 'ケースをクローズ',
  'cmmn.instance.completeTitle': 'ケースインスタンスの完了',
  'cmmn.instance.completeText':
    '「{name}」のインスタンスを完了します。アクティブなプランアイテムが残っていない場合のみ可能で、元に戻せません。',
  'cmmn.instance.terminateTitle': 'ケースインスタンスの終了',
  'cmmn.instance.terminateText':
    '「{name}」のインスタンスと未完了のすべてのプランアイテムを終了し、未完了のタスクは削除されます。元に戻せません。',
  'cmmn.instance.closeTitle': 'ケースインスタンスのクローズ',
  'cmmn.instance.closeText':
    '「{name}」のインスタンスをクローズします。実行中のインスタンスから外れ、履歴にのみ残ります。元に戻せません。',
  'cmmn.instance.done.complete': 'ケースインスタンスを完了しました',
  'cmmn.instance.done.terminate': 'ケースインスタンスを終了しました',
  'cmmn.instance.done.close': 'ケースインスタンスをクローズしました',
  'cmmn.instance.failed': 'ケースインスタンスを変更できませんでした',

  'cmmn.state.active': 'アクティブ',
  'cmmn.state.enabled': '開始待ち',
  'cmmn.state.disabled': '無効',
  'cmmn.state.available': '条件待ち',
  'cmmn.state.completed': '完了',
  'cmmn.state.terminated': '終了',
  'cmmn.state.closed': 'クローズ済み',

  'cmmn.type.casePlanModel': 'ケースプランモデル',
  'cmmn.type.stage': 'ステージ',
  'cmmn.type.humanTask': 'ヒューマンタスク',
  'cmmn.type.processTask': 'プロセスタスク',
  'cmmn.type.caseTask': 'ケースタスク',
  'cmmn.type.decisionTask': 'デシジョンタスク',
  'cmmn.type.task': 'タスク',
  'cmmn.type.milestone': 'マイルストーン',

  'cmmn.action.manualStart': '開始',
  'cmmn.action.complete': '完了',
  'cmmn.action.disable': '無効にする',
  'cmmn.action.reenable': '再度有効にする',
  'cmmn.action.terminate': '終了',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'プランアイテム',
  'cmmn.item.type': '種類',
  'cmmn.item.required': '必須',
  'cmmn.item.failed': '「{name}」を変更できませんでした',
  'cmmn.item.done.manualStart': '「{name}」を開始しました',
  'cmmn.item.done.complete': '「{name}」を完了しました',
  'cmmn.item.done.disable': '「{name}」を無効にしました',
  'cmmn.item.done.reenable': '「{name}」を再度有効にしました',
  'cmmn.item.done.terminate': '「{name}」を終了しました',
  'cmmn.item.completeTitle': 'プランアイテムの完了',
  'cmmn.item.completeText':
    '「{name}」をフォームなしで完了します。未完了のタスクも完了されます。元に戻せません。',
  'cmmn.item.terminateTitle': 'プランアイテムの終了',
  'cmmn.item.terminateText':
    '「{name}」とその中のすべてを終了し、未完了のタスクは削除されます。元に戻せません。',
  'cmmn.items.empty': '未完了のプランアイテムはありません',
  'cmmn.items.closed': 'クローズ済みのケースに未完了のプランアイテムはありません。経緯は履歴で確認できます。',
  'cmmn.tasks.empty': 'このケースインスタンスに未完了のタスクはありません',

  'cmmn.history.title': 'ケース履歴',
  'cmmn.history.count': 'ケースインスタンス {count} 件',
  'cmmn.history.search': 'ビジネスキーで検索',
  'cmmn.history.case': 'ケース',
  'cmmn.history.filter.all': 'すべて',
  'cmmn.history.filter.open': '未クローズ',
  'cmmn.history.filter.closed': 'クローズ済み',
  'cmmn.history.empty': '履歴にはまだケースインスタンスがありません',
  'cmmn.history.noMatch': '一致するケースインスタンスはありません',
  'cmmn.history.itemsEmpty': '履歴にこのケースインスタンスのプランアイテムはありません',
  'cmmn.variables.fromHistory': '履歴に保存された値です。クローズ済みのケースは変更できません。',

  'cmmn.legend': '図の状態',
  'cmmn.diagram.label': 'ケース図',
  'cmmn.diagram.noLayout': 'このモデルには図のレイアウトがないため、プランアイテムの一覧のみ表示します。',
  'cmmn.task.diagramLabel': 'ケース図。タスクのプランアイテムが強調表示されています',
  'cmmn.task.openCase': 'Cockpit でケースを開く',

  'cmmn.deployments.openCase': 'ケース「{name}」(バージョン {version}) を開く',
  'palette.kind.case': 'ケースインスタンス',
};

export default messages;
