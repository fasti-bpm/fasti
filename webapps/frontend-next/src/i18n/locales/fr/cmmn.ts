/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** French messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Dossiers (CMMN)',
  'cmmn.nav.history': 'Historique des dossiers',

  'cmmn.definitions.title': 'Définitions de dossier',
  'cmmn.definitions.count': '{count} définitions de dossier',
  'cmmn.definitions.count.one': '{count} définition de dossier',
  'cmmn.definitions.search': 'Rechercher des définitions de dossier',
  'cmmn.definitions.noMatch': 'Aucune définition de dossier ne correspond à la recherche',
  'cmmn.definitions.empty': 'Aucune définition de dossier déployée',
  'cmmn.definitions.emptyHint': 'Les dossiers sont modélisés en CMMN et déployés sous forme de fichiers .cmmn, par exemple sur la page',
  'cmmn.definitions.open': 'Instances ouvertes',

  'cmmn.definition.title': 'Définition de dossier',
  'cmmn.definition.notFound': 'Cette définition de dossier n’existe pas',
  'cmmn.definition.instances': 'Instances ouvertes de cette version',
  'cmmn.definition.instancesEmpty': 'Cette version n’a aucune instance ouverte',
  'cmmn.definition.businessKeySearch': 'Clé métier (exacte)',
  'cmmn.definition.noMatch': 'Aucune instance ouverte n’a cette clé métier',
  'cmmn.definition.diagramLabel': 'Diagramme du dossier avec le nombre d’éléments de plan ouverts',
  'cmmn.definition.diagramHint': 'Les nombres indiquent combien d’éléments de plan des instances ouvertes sont actifs ou attendent d’être démarrés.',
  'cmmn.definition.truncated': 'Les nombres du diagramme ne comptent que les {count} premiers éléments de plan.',

  'cmmn.create.action': 'Créer une instance de dossier',
  'cmmn.create.title': 'Créer une instance de dossier',
  'cmmn.create.text': 'Crée une instance de « {name} », version {version}. Ses éléments de plan démarrent aussitôt.',
  'cmmn.create.done': 'Instance de dossier créée',
  'cmmn.create.failed': 'L’instance de dossier n’a pas pu être créée',

  'cmmn.instance': 'Instance de dossier',
  'cmmn.instance.gone': 'Cette instance de dossier n’existe pas',
  'cmmn.instance.goneHint': 'L’ID est peut-être erroné, ou l’instance a été clôturée et son historique supprimé.',
  'cmmn.instance.closedNote': 'Cette instance de dossier est clôturée. Elle est affichée telle que l’historique l’a conservée et ne peut pas être modifiée.',
  'cmmn.instance.created': 'Créée',
  'cmmn.instance.closedAt': 'Clôturée',
  'cmmn.instance.backToDefinition': 'Retour à la définition de dossier',
  'cmmn.instance.parentProcess': 'Démarrée par une instance de processus',
  'cmmn.instance.parentCase': 'Démarrée par une instance de dossier',
  'cmmn.instance.diagramLabel': 'Diagramme du dossier avec l’état de chaque élément de plan',
  'cmmn.instance.diagramHint': 'Choisissez un élément de plan dans le diagramme pour ne voir que lui dans la liste.',
  'cmmn.instance.tabs': 'Données de l’instance de dossier',
  'cmmn.instance.tab.items': 'Éléments de plan',
  'cmmn.instance.tab.history': 'Historique',
  'cmmn.instance.complete': 'Achever le dossier',
  'cmmn.instance.terminate': 'Interrompre le dossier',
  'cmmn.instance.close': 'Clôturer le dossier',
  'cmmn.instance.completeTitle': 'Achever l’instance de dossier',
  'cmmn.instance.completeText':
    'L’instance de « {name} » est achevée. Ce n’est possible que si plus aucun élément de plan n’est actif, et c’est irréversible.',
  'cmmn.instance.terminateTitle': 'Interrompre l’instance de dossier',
  'cmmn.instance.terminateText':
    'L’instance de « {name} » et tous ses éléments de plan non terminés sont interrompus ; les tâches ouvertes sont supprimées. C’est irréversible.',
  'cmmn.instance.closeTitle': 'Clôturer l’instance de dossier',
  'cmmn.instance.closeText':
    'L’instance de « {name} » est clôturée : elle quitte les instances en cours et seul l’historique la conserve. C’est irréversible.',
  'cmmn.instance.done.complete': 'Instance de dossier achevée',
  'cmmn.instance.done.terminate': 'Instance de dossier interrompue',
  'cmmn.instance.done.close': 'Instance de dossier clôturée',
  'cmmn.instance.failed': 'L’instance de dossier n’a pas pu être modifiée',

  'cmmn.state.active': 'Active',
  'cmmn.state.enabled': 'Prête à démarrer',
  'cmmn.state.disabled': 'Désactivée',
  'cmmn.state.available': 'En attente de condition',
  'cmmn.state.completed': 'Achevée',
  'cmmn.state.terminated': 'Interrompue',
  'cmmn.state.closed': 'Clôturée',

  'cmmn.type.casePlanModel': 'Modèle de plan du dossier',
  'cmmn.type.stage': 'Étape',
  'cmmn.type.humanTask': 'Tâche humaine',
  'cmmn.type.processTask': 'Tâche de processus',
  'cmmn.type.caseTask': 'Tâche de dossier',
  'cmmn.type.decisionTask': 'Tâche de décision',
  'cmmn.type.task': 'Tâche',
  'cmmn.type.milestone': 'Jalon',

  'cmmn.action.manualStart': 'Démarrer',
  'cmmn.action.complete': 'Achever',
  'cmmn.action.disable': 'Désactiver',
  'cmmn.action.reenable': 'Réactiver',
  'cmmn.action.terminate': 'Interrompre',
  'cmmn.action.of': '{action} : {name}',

  'cmmn.item.name': 'Élément de plan',
  'cmmn.item.type': 'Type',
  'cmmn.item.required': 'Obligatoire',
  'cmmn.item.failed': '« {name} » n’a pas pu être modifié',
  'cmmn.item.done.manualStart': '« {name} » démarré',
  'cmmn.item.done.complete': '« {name} » achevé',
  'cmmn.item.done.disable': '« {name} » désactivé',
  'cmmn.item.done.reenable': '« {name} » réactivé',
  'cmmn.item.done.terminate': '« {name} » interrompu',
  'cmmn.item.completeTitle': 'Achever l’élément de plan',
  'cmmn.item.completeText':
    '« {name} » est achevé sans formulaire ; une tâche ouverte qui en dépend est également achevée. C’est irréversible.',
  'cmmn.item.terminateTitle': 'Interrompre l’élément de plan',
  'cmmn.item.terminateText':
    '« {name} » et tout ce qu’il contient sont interrompus ; ses tâches ouvertes sont supprimées. C’est irréversible.',
  'cmmn.items.empty': 'Aucun élément de plan n’est ouvert',
  'cmmn.items.closed': 'Un dossier clôturé n’a plus d’éléments de plan ouverts. L’historique montre ce qui s’est passé.',
  'cmmn.tasks.empty': 'Cette instance de dossier n’a aucune tâche ouverte',

  'cmmn.history.title': 'Historique des dossiers',
  'cmmn.history.count': '{count} instances de dossier',
  'cmmn.history.count.one': '{count} instance de dossier',
  'cmmn.history.search': 'Rechercher par clé métier',
  'cmmn.history.case': 'Dossier',
  'cmmn.history.filter.all': 'Toutes',
  'cmmn.history.filter.open': 'Non clôturées',
  'cmmn.history.filter.closed': 'Clôturées',
  'cmmn.history.empty': 'L’historique ne contient encore aucune instance de dossier',
  'cmmn.history.noMatch': 'Aucune instance de dossier ne correspond',
  'cmmn.history.itemsEmpty': 'L’historique ne contient aucun élément de plan de cette instance de dossier',
  'cmmn.variables.fromHistory': 'Les valeurs telles que l’historique les a conservées ; un dossier clôturé ne peut pas être modifié.',

  'cmmn.legend': 'États sur le diagramme',
  'cmmn.diagram.label': 'Diagramme du dossier',
  'cmmn.diagram.noLayout': 'Ce modèle n’a pas de mise en page de diagramme ; seuls ses éléments de plan sont listés.',
  'cmmn.task.diagramLabel': 'Diagramme du dossier, l’élément de plan de la tâche est mis en évidence',
  'cmmn.task.openCase': 'Ouvrir le dossier dans Cockpit',

  'cmmn.deployments.openCase': 'Ouvrir le dossier « {name} » (version {version})',
  'palette.kind.case': 'Instance de dossier',
};

export default messages;
