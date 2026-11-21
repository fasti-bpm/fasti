/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** German messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Fälle (CMMN)',
  'cmmn.nav.history': 'Fallhistorie',

  'cmmn.definitions.title': 'Falldefinitionen',
  'cmmn.definitions.count': '{count} Falldefinitionen',
  'cmmn.definitions.count.one': '1 Falldefinition',
  'cmmn.definitions.search': 'Falldefinitionen suchen',
  'cmmn.definitions.noMatch': 'Keine Falldefinition passt zur Suche',
  'cmmn.definitions.empty': 'Keine Falldefinitionen deployt',
  'cmmn.definitions.emptyHint': 'Fälle werden in CMMN modelliert und als .cmmn-Dateien deployt, zum Beispiel auf der Seite',
  'cmmn.definitions.open': 'Offene Instanzen',

  'cmmn.definition.title': 'Falldefinition',
  'cmmn.definition.notFound': 'Diese Falldefinition gibt es nicht',
  'cmmn.definition.instances': 'Offene Instanzen dieser Version',
  'cmmn.definition.instancesEmpty': 'Diese Version hat keine offenen Instanzen',
  'cmmn.definition.businessKeySearch': 'Business Key (genau)',
  'cmmn.definition.noMatch': 'Keine offene Instanz hat diesen Business Key',
  'cmmn.definition.diagramLabel': 'Falldiagramm mit der Zahl offener Planelemente',
  'cmmn.definition.diagramHint': 'Die Zahlen zeigen, wie viele Planelemente offener Instanzen aktiv sind oder auf ihren Start warten.',
  'cmmn.definition.truncated': 'Die Zahlen im Diagramm zählen nur die ersten {count} Planelemente.',

  'cmmn.create.action': 'Fallinstanz anlegen',
  'cmmn.create.title': 'Eine Fallinstanz anlegen',
  'cmmn.create.text': 'Legt eine Instanz von „{name}“, Version {version}, an. Ihre Planelemente starten sofort.',
  'cmmn.create.done': 'Fallinstanz angelegt',
  'cmmn.create.failed': 'Die Fallinstanz konnte nicht angelegt werden',

  'cmmn.instance': 'Fallinstanz',
  'cmmn.instance.gone': 'Diese Fallinstanz gibt es nicht',
  'cmmn.instance.goneHint': 'Die ID ist vielleicht falsch, oder die Instanz wurde geschlossen und ihre Historie entfernt.',
  'cmmn.instance.closedNote': 'Diese Fallinstanz ist geschlossen. Sie wird so gezeigt, wie die Historie sie aufbewahrt hat, und kann nicht geändert werden.',
  'cmmn.instance.created': 'Angelegt',
  'cmmn.instance.closedAt': 'Geschlossen',
  'cmmn.instance.backToDefinition': 'Zurück zur Falldefinition',
  'cmmn.instance.parentProcess': 'Von einer Prozessinstanz gestartet',
  'cmmn.instance.parentCase': 'Von einer Fallinstanz gestartet',
  'cmmn.instance.diagramLabel': 'Falldiagramm mit dem Zustand jedes Planelements',
  'cmmn.instance.diagramHint': 'Wählen Sie ein Planelement im Diagramm, um nur dieses in der Liste der Planelemente zu sehen.',
  'cmmn.instance.tabs': 'Daten der Fallinstanz',
  'cmmn.instance.tab.items': 'Planelemente',
  'cmmn.instance.tab.history': 'Historie',
  'cmmn.instance.complete': 'Fall abschließen',
  'cmmn.instance.terminate': 'Fall beenden',
  'cmmn.instance.close': 'Fall schließen',
  'cmmn.instance.completeTitle': 'Fallinstanz abschließen',
  'cmmn.instance.completeText':
    'Die Instanz von „{name}“ wird abgeschlossen. Das geht nur, wenn kein Planelement mehr aktiv ist, und lässt sich nicht rückgängig machen.',
  'cmmn.instance.terminateTitle': 'Fallinstanz beenden',
  'cmmn.instance.terminateText':
    'Die Instanz von „{name}“ und alle nicht abgeschlossenen Planelemente werden beendet; offene Aufgaben werden entfernt. Das lässt sich nicht rückgängig machen.',
  'cmmn.instance.closeTitle': 'Fallinstanz schließen',
  'cmmn.instance.closeText':
    'Die Instanz von „{name}“ wird geschlossen: Sie verlässt die laufenden Instanzen, nur die Historie bewahrt sie auf. Das lässt sich nicht rückgängig machen.',
  'cmmn.instance.done.complete': 'Fallinstanz abgeschlossen',
  'cmmn.instance.done.terminate': 'Fallinstanz beendet',
  'cmmn.instance.done.close': 'Fallinstanz geschlossen',
  'cmmn.instance.failed': 'Die Fallinstanz konnte nicht geändert werden',

  'cmmn.state.active': 'Aktiv',
  'cmmn.state.enabled': 'Startbereit',
  'cmmn.state.disabled': 'Deaktiviert',
  'cmmn.state.available': 'Wartet auf Bedingung',
  'cmmn.state.completed': 'Abgeschlossen',
  'cmmn.state.terminated': 'Beendet',
  'cmmn.state.closed': 'Geschlossen',

  'cmmn.type.casePlanModel': 'Fallplanmodell',
  'cmmn.type.stage': 'Phase',
  'cmmn.type.humanTask': 'Benutzeraufgabe',
  'cmmn.type.processTask': 'Prozessaufgabe',
  'cmmn.type.caseTask': 'Fallaufgabe',
  'cmmn.type.decisionTask': 'Entscheidungsaufgabe',
  'cmmn.type.task': 'Aufgabe',
  'cmmn.type.milestone': 'Meilenstein',

  'cmmn.action.manualStart': 'Starten',
  'cmmn.action.complete': 'Abschließen',
  'cmmn.action.disable': 'Deaktivieren',
  'cmmn.action.reenable': 'Wieder aktivieren',
  'cmmn.action.terminate': 'Beenden',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'Planelement',
  'cmmn.item.type': 'Typ',
  'cmmn.item.required': 'Erforderlich',
  'cmmn.item.failed': '„{name}“ konnte nicht geändert werden',
  'cmmn.item.done.manualStart': '„{name}“ gestartet',
  'cmmn.item.done.complete': '„{name}“ abgeschlossen',
  'cmmn.item.done.disable': '„{name}“ deaktiviert',
  'cmmn.item.done.reenable': '„{name}“ wieder aktiviert',
  'cmmn.item.done.terminate': '„{name}“ beendet',
  'cmmn.item.completeTitle': 'Planelement abschließen',
  'cmmn.item.completeText':
    '„{name}“ wird ohne Formular abgeschlossen; eine offene Aufgabe dazu wird ebenfalls abgeschlossen. Das lässt sich nicht rückgängig machen.',
  'cmmn.item.terminateTitle': 'Planelement beenden',
  'cmmn.item.terminateText':
    '„{name}“ und alles darin wird beendet; offene Aufgaben dazu werden entfernt. Das lässt sich nicht rückgängig machen.',
  'cmmn.items.empty': 'Kein Planelement ist offen',
  'cmmn.items.closed': 'Ein geschlossener Fall hat keine offenen Planelemente. Die Historie zeigt, was geschehen ist.',
  'cmmn.tasks.empty': 'Diese Fallinstanz hat keine offene Aufgabe',

  'cmmn.history.title': 'Fallhistorie',
  'cmmn.history.count': '{count} Fallinstanzen',
  'cmmn.history.count.one': '1 Fallinstanz',
  'cmmn.history.search': 'Nach Business Key suchen',
  'cmmn.history.case': 'Fall',
  'cmmn.history.filter.all': 'Alle',
  'cmmn.history.filter.open': 'Nicht geschlossen',
  'cmmn.history.filter.closed': 'Geschlossen',
  'cmmn.history.empty': 'Die Historie enthält noch keine Fallinstanzen',
  'cmmn.history.noMatch': 'Keine Fallinstanz passt',
  'cmmn.history.itemsEmpty': 'Die Historie enthält keine Planelemente dieser Fallinstanz',
  'cmmn.variables.fromHistory': 'Die Werte, wie die Historie sie aufbewahrt hat; ein geschlossener Fall kann nicht geändert werden.',

  'cmmn.legend': 'Zustände im Diagramm',
  'cmmn.diagram.label': 'Falldiagramm',
  'cmmn.diagram.noLayout': 'Dieses Modell hat kein Diagramm-Layout, daher werden nur seine Planelemente aufgelistet.',
  'cmmn.task.diagramLabel': 'Falldiagramm, das Planelement der Aufgabe ist hervorgehoben',
  'cmmn.task.openCase': 'Fall im Cockpit öffnen',

  'palette.kind.case': 'Fallinstanz',
};

export default messages;
