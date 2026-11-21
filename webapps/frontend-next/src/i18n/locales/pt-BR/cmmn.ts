/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Brazilian Portuguese messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Casos (CMMN)',
  'cmmn.nav.history': 'Histórico de casos',

  'cmmn.definitions.title': 'Definições de caso',
  'cmmn.definitions.count': '{count} definições de caso',
  'cmmn.definitions.count.one': '{count} definição de caso',
  'cmmn.definitions.search': 'Pesquisar definições de caso',
  'cmmn.definitions.noMatch': 'Nenhuma definição de caso corresponde à pesquisa',
  'cmmn.definitions.empty': 'Nenhuma definição de caso implantada',
  'cmmn.definitions.emptyHint': 'Casos são modelados em CMMN e implantados como arquivos .cmmn, por exemplo na página',
  'cmmn.definitions.open': 'Instâncias abertas',

  'cmmn.definition.title': 'Definição de caso',
  'cmmn.definition.notFound': 'Esta definição de caso não existe',
  'cmmn.definition.instances': 'Instâncias abertas desta versão',
  'cmmn.definition.instancesEmpty': 'Esta versão não tem instâncias abertas',
  'cmmn.definition.businessKeySearch': 'Chave de negócio (exata)',
  'cmmn.definition.noMatch': 'Nenhuma instância aberta tem esta chave de negócio',
  'cmmn.definition.diagramLabel': 'Diagrama do caso com o número de itens do plano abertos',
  'cmmn.definition.diagramHint': 'Os números mostram quantos itens do plano de instâncias abertas estão ativos ou aguardam ser iniciados.',
  'cmmn.definition.truncated': 'Os números no diagrama contam apenas os primeiros {count} itens do plano.',

  'cmmn.create.action': 'Criar instância de caso',
  'cmmn.create.title': 'Criar uma instância de caso',
  'cmmn.create.text': 'Cria uma instância de “{name}”, versão {version}. Seus itens do plano começam imediatamente.',
  'cmmn.create.done': 'Instância de caso criada',
  'cmmn.create.failed': 'Não foi possível criar a instância de caso',

  'cmmn.instance': 'Instância de caso',
  'cmmn.instance.gone': 'Esta instância de caso não existe',
  'cmmn.instance.goneHint': 'O ID pode estar errado, ou a instância foi fechada e seu histórico removido.',
  'cmmn.instance.closedNote': 'Esta instância de caso está fechada. Ela é mostrada como o histórico a guardou e não pode ser alterada.',
  'cmmn.instance.created': 'Criada',
  'cmmn.instance.closedAt': 'Fechada',
  'cmmn.instance.backToDefinition': 'Voltar à definição de caso',
  'cmmn.instance.parentProcess': 'Iniciada por uma instância de processo',
  'cmmn.instance.parentCase': 'Iniciada por uma instância de caso',
  'cmmn.instance.diagramLabel': 'Diagrama do caso com o estado de cada item do plano',
  'cmmn.instance.diagramHint': 'Escolha um item do plano no diagrama para ver só ele na lista de itens do plano.',
  'cmmn.instance.tabs': 'Dados da instância de caso',
  'cmmn.instance.tab.items': 'Itens do plano',
  'cmmn.instance.tab.history': 'Histórico',
  'cmmn.instance.complete': 'Concluir caso',
  'cmmn.instance.terminate': 'Encerrar caso',
  'cmmn.instance.close': 'Fechar caso',
  'cmmn.instance.completeTitle': 'Concluir a instância de caso',
  'cmmn.instance.completeText':
    'A instância de “{name}” é concluída. Isso só é possível quando nenhum item do plano está ativo, e não pode ser desfeito.',
  'cmmn.instance.terminateTitle': 'Encerrar a instância de caso',
  'cmmn.instance.terminateText':
    'A instância de “{name}” e todos os seus itens do plano não terminados são encerrados; tarefas abertas são removidas. Não pode ser desfeito.',
  'cmmn.instance.closeTitle': 'Fechar a instância de caso',
  'cmmn.instance.closeText':
    'A instância de “{name}” é fechada: ela deixa as instâncias em execução e só o histórico a guarda. Não pode ser desfeito.',
  'cmmn.instance.done.complete': 'Instância de caso concluída',
  'cmmn.instance.done.terminate': 'Instância de caso encerrada',
  'cmmn.instance.done.close': 'Instância de caso fechada',
  'cmmn.instance.failed': 'Não foi possível alterar a instância de caso',

  'cmmn.state.active': 'Ativo',
  'cmmn.state.enabled': 'Pronto para iniciar',
  'cmmn.state.disabled': 'Desabilitado',
  'cmmn.state.available': 'Aguardando condição',
  'cmmn.state.completed': 'Concluído',
  'cmmn.state.terminated': 'Encerrado',
  'cmmn.state.closed': 'Fechado',

  'cmmn.type.casePlanModel': 'Modelo de plano do caso',
  'cmmn.type.stage': 'Estágio',
  'cmmn.type.humanTask': 'Tarefa humana',
  'cmmn.type.processTask': 'Tarefa de processo',
  'cmmn.type.caseTask': 'Tarefa de caso',
  'cmmn.type.decisionTask': 'Tarefa de decisão',
  'cmmn.type.task': 'Tarefa',
  'cmmn.type.milestone': 'Marco',

  'cmmn.action.manualStart': 'Iniciar',
  'cmmn.action.complete': 'Concluir',
  'cmmn.action.disable': 'Desabilitar',
  'cmmn.action.reenable': 'Habilitar novamente',
  'cmmn.action.terminate': 'Encerrar',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'Item do plano',
  'cmmn.item.type': 'Tipo',
  'cmmn.item.required': 'Obrigatório',
  'cmmn.item.failed': 'Não foi possível alterar “{name}”',
  'cmmn.item.done.manualStart': '“{name}” iniciado',
  'cmmn.item.done.complete': '“{name}” concluído',
  'cmmn.item.done.disable': '“{name}” desabilitado',
  'cmmn.item.done.reenable': '“{name}” habilitado novamente',
  'cmmn.item.done.terminate': '“{name}” encerrado',
  'cmmn.item.completeTitle': 'Concluir o item do plano',
  'cmmn.item.completeText':
    '“{name}” é concluído sem formulário; uma tarefa aberta dele também é concluída. Não pode ser desfeito.',
  'cmmn.item.terminateTitle': 'Encerrar o item do plano',
  'cmmn.item.terminateText':
    '“{name}” e tudo o que ele contém são encerrados; suas tarefas abertas são removidas. Não pode ser desfeito.',
  'cmmn.items.empty': 'Nenhum item do plano está aberto',
  'cmmn.items.closed': 'Um caso fechado não tem itens do plano abertos. O histórico mostra o que aconteceu.',
  'cmmn.tasks.empty': 'Esta instância de caso não tem tarefas abertas',

  'cmmn.history.title': 'Histórico de casos',
  'cmmn.history.count': '{count} instâncias de caso',
  'cmmn.history.count.one': '{count} instância de caso',
  'cmmn.history.search': 'Pesquisar por chave de negócio',
  'cmmn.history.case': 'Caso',
  'cmmn.history.filter.all': 'Todas',
  'cmmn.history.filter.open': 'Não fechadas',
  'cmmn.history.filter.closed': 'Fechadas',
  'cmmn.history.empty': 'O histórico ainda não tem instâncias de caso',
  'cmmn.history.noMatch': 'Nenhuma instância de caso corresponde',
  'cmmn.history.itemsEmpty': 'O histórico não tem itens do plano desta instância de caso',
  'cmmn.variables.fromHistory': 'Os valores como o histórico os guardou; um caso fechado não pode ser alterado.',

  'cmmn.legend': 'Estados no diagrama',
  'cmmn.diagram.label': 'Diagrama do caso',
  'cmmn.diagram.noLayout': 'Este modelo não tem layout de diagrama, então só seus itens do plano são listados.',
  'cmmn.task.diagramLabel': 'Diagrama do caso, o item do plano da tarefa está destacado',
  'cmmn.task.openCase': 'Abrir o caso no Cockpit',

  'palette.kind.case': 'Instância de caso',
};

export default messages;
