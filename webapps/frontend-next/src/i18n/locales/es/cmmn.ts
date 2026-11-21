/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Spanish messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Casos (CMMN)',
  'cmmn.nav.history': 'Historial de casos',

  'cmmn.definitions.title': 'Definiciones de caso',
  'cmmn.definitions.count': '{count} definiciones de caso',
  'cmmn.definitions.count.one': '1 definición de caso',
  'cmmn.definitions.search': 'Buscar definiciones de caso',
  'cmmn.definitions.noMatch': 'Ninguna definición de caso coincide con la búsqueda',
  'cmmn.definitions.empty': 'No hay definiciones de caso desplegadas',
  'cmmn.definitions.emptyHint': 'Los casos se modelan en CMMN y se despliegan como archivos .cmmn, por ejemplo en la página',
  'cmmn.definitions.open': 'Instancias abiertas',

  'cmmn.definition.title': 'Definición de caso',
  'cmmn.definition.notFound': 'Esta definición de caso no existe',
  'cmmn.definition.instances': 'Instancias abiertas de esta versión',
  'cmmn.definition.instancesEmpty': 'Esta versión no tiene instancias abiertas',
  'cmmn.definition.businessKeySearch': 'Clave de negocio (exacta)',
  'cmmn.definition.noMatch': 'Ninguna instancia abierta tiene esta clave de negocio',
  'cmmn.definition.diagramLabel': 'Diagrama del caso con el número de elementos del plan abiertos',
  'cmmn.definition.diagramHint': 'Los números indican cuántos elementos del plan de instancias abiertas están activos o esperan a ser iniciados.',
  'cmmn.definition.truncated': 'Los números del diagrama solo cuentan los primeros {count} elementos del plan.',

  'cmmn.create.action': 'Crear instancia de caso',
  'cmmn.create.title': 'Crear una instancia de caso',
  'cmmn.create.text': 'Crea una instancia de «{name}», versión {version}. Sus elementos del plan se inician de inmediato.',
  'cmmn.create.done': 'Instancia de caso creada',
  'cmmn.create.failed': 'No se pudo crear la instancia de caso',

  'cmmn.instance': 'Instancia de caso',
  'cmmn.instance.gone': 'Esta instancia de caso no existe',
  'cmmn.instance.goneHint': 'Puede que el ID sea incorrecto, o que la instancia se cerrara y se borrara su historial.',
  'cmmn.instance.closedNote': 'Esta instancia de caso está cerrada. Se muestra tal como la guardó el historial y no se puede cambiar.',
  'cmmn.instance.created': 'Creada',
  'cmmn.instance.closedAt': 'Cerrada',
  'cmmn.instance.backToDefinition': 'Volver a la definición de caso',
  'cmmn.instance.parentProcess': 'Iniciada por una instancia de proceso',
  'cmmn.instance.parentCase': 'Iniciada por una instancia de caso',
  'cmmn.instance.diagramLabel': 'Diagrama del caso con el estado de cada elemento del plan',
  'cmmn.instance.diagramHint': 'Elija un elemento del plan en el diagrama para ver solo ese elemento en la lista.',
  'cmmn.instance.tabs': 'Datos de la instancia de caso',
  'cmmn.instance.tab.items': 'Elementos del plan',
  'cmmn.instance.tab.history': 'Historial',
  'cmmn.instance.complete': 'Completar caso',
  'cmmn.instance.terminate': 'Terminar caso',
  'cmmn.instance.close': 'Cerrar caso',
  'cmmn.instance.completeTitle': 'Completar la instancia de caso',
  'cmmn.instance.completeText':
    'La instancia de «{name}» se completa. Solo es posible si ya no hay elementos del plan activos, y no se puede deshacer.',
  'cmmn.instance.terminateTitle': 'Terminar la instancia de caso',
  'cmmn.instance.terminateText':
    'La instancia de «{name}» y todos sus elementos del plan sin terminar se terminan; las tareas abiertas se eliminan. No se puede deshacer.',
  'cmmn.instance.closeTitle': 'Cerrar la instancia de caso',
  'cmmn.instance.closeText':
    'La instancia de «{name}» se cierra: deja de estar entre las instancias en curso y solo el historial la conserva. No se puede deshacer.',
  'cmmn.instance.done.complete': 'Instancia de caso completada',
  'cmmn.instance.done.terminate': 'Instancia de caso terminada',
  'cmmn.instance.done.close': 'Instancia de caso cerrada',
  'cmmn.instance.failed': 'No se pudo cambiar la instancia de caso',

  'cmmn.state.active': 'Activo',
  'cmmn.state.enabled': 'Listo para iniciar',
  'cmmn.state.disabled': 'Deshabilitado',
  'cmmn.state.available': 'Esperando condición',
  'cmmn.state.completed': 'Completado',
  'cmmn.state.terminated': 'Terminado',
  'cmmn.state.closed': 'Cerrado',

  'cmmn.type.casePlanModel': 'Modelo de plan del caso',
  'cmmn.type.stage': 'Etapa',
  'cmmn.type.humanTask': 'Tarea humana',
  'cmmn.type.processTask': 'Tarea de proceso',
  'cmmn.type.caseTask': 'Tarea de caso',
  'cmmn.type.decisionTask': 'Tarea de decisión',
  'cmmn.type.task': 'Tarea',
  'cmmn.type.milestone': 'Hito',

  'cmmn.action.manualStart': 'Iniciar',
  'cmmn.action.complete': 'Completar',
  'cmmn.action.disable': 'Deshabilitar',
  'cmmn.action.reenable': 'Volver a habilitar',
  'cmmn.action.terminate': 'Terminar',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'Elemento del plan',
  'cmmn.item.type': 'Tipo',
  'cmmn.item.required': 'Obligatorio',
  'cmmn.item.failed': 'No se pudo cambiar «{name}»',
  'cmmn.item.done.manualStart': '«{name}» iniciado',
  'cmmn.item.done.complete': '«{name}» completado',
  'cmmn.item.done.disable': '«{name}» deshabilitado',
  'cmmn.item.done.reenable': '«{name}» habilitado de nuevo',
  'cmmn.item.done.terminate': '«{name}» terminado',
  'cmmn.item.completeTitle': 'Completar el elemento del plan',
  'cmmn.item.completeText':
    '«{name}» se completa sin formulario; una tarea abierta suya también se completa. No se puede deshacer.',
  'cmmn.item.terminateTitle': 'Terminar el elemento del plan',
  'cmmn.item.terminateText':
    '«{name}» y todo lo que contiene se terminan; sus tareas abiertas se eliminan. No se puede deshacer.',
  'cmmn.items.empty': 'No hay elementos del plan abiertos',
  'cmmn.items.closed': 'Un caso cerrado no tiene elementos del plan abiertos. El historial muestra lo ocurrido.',
  'cmmn.tasks.empty': 'Esta instancia de caso no tiene tareas abiertas',

  'cmmn.history.title': 'Historial de casos',
  'cmmn.history.count': '{count} instancias de caso',
  'cmmn.history.count.one': '1 instancia de caso',
  'cmmn.history.search': 'Buscar por clave de negocio',
  'cmmn.history.case': 'Caso',
  'cmmn.history.filter.all': 'Todas',
  'cmmn.history.filter.open': 'Sin cerrar',
  'cmmn.history.filter.closed': 'Cerradas',
  'cmmn.history.empty': 'El historial aún no tiene instancias de caso',
  'cmmn.history.noMatch': 'Ninguna instancia de caso coincide',
  'cmmn.history.itemsEmpty': 'El historial no tiene elementos del plan de esta instancia de caso',
  'cmmn.variables.fromHistory': 'Los valores tal como los guardó el historial; un caso cerrado no se puede cambiar.',

  'cmmn.legend': 'Estados en el diagrama',
  'cmmn.diagram.label': 'Diagrama del caso',
  'cmmn.diagram.noLayout': 'Este modelo no tiene disposición de diagrama, así que solo se listan sus elementos del plan.',
  'cmmn.task.diagramLabel': 'Diagrama del caso, el elemento del plan de la tarea está resaltado',
  'cmmn.task.openCase': 'Abrir el caso en Cockpit',

  'cmmn.deployments.openCase': 'Abrir el caso «{name}» (versión {version})',
  'palette.kind.case': 'Instancia de caso',
};

export default messages;
