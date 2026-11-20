[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | Português (Brasil) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

# A interface web do Fasti

Por que a nova interface web tem esses recursos e em qual caminho as aplicações web são servidas. Um tour pela interface: <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## Uma interface criada a partir de reclamações reais de usuários

Os recursos da nova interface web foram escolhidos com base no que usuários do Camunda 7 escreveram publicamente no
fórum do Camunda e no GitHub, por exemplo:

| O que os usuários pediram | Fonte | No Fasti |
|---|---|---|
| Pesquisar instâncias por várias variáveis, com OR | fórum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Construtor de condições com múltiplas variáveis, AND/OR |
| Ver os comentários em cada etapa de um caso | fórum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Linha do tempo do caso na tarefa; comentários na própria instância de processo |
| Ordenar tarefas por variáveis, com as mais antigas primeiro | fórum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Ordenação por variáveis, prazos e urgência |
| Delegação, substitutos durante ausências | fórum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Já existe delegação por tarefa; substitutos durante ausências estão no [plano de evolução](https://fasti.dev/#timeline) |
| Chave de negócio e variáveis ao iniciar um processo sem formulário | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Sim |
| Baixar o XML BPMN implantado | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Sim |
| Rótulos consistentes, vários idiomas | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 idiomas |

<a id="web-application-path-fasti"></a>

## Caminho da aplicação web: `/fasti`

Cockpit, Tasklist, Admin e a nova interface web são servidos sob `/fasti` (anteriormente `/camunda`)
por todas as distribuições: `http://localhost:8080/fasti/` abre a nova interface
(`/fasti/app/next/default/`), e as aplicações clássicas ficam em `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` e `/fasti/app/admin/...`. A API REST permanece em `/engine-rest`.

**As aplicações clássicas estão descontinuadas.** Os clássicos Cockpit, Tasklist, Admin e Welcome
são construídos com AngularJS 1.8.2 e Bootstrap 3, ambos em fim de vida. **A imagem do contêiner não
os disponibiliza por padrão** (alteração incompatível): suas páginas e favoritos antigos redirecionam para a seção correspondente da nova interface, e as solicitações de seus arquivos retornam `404`; `FASTI_CLASSIC_WEBAPPS=true` os
reativa (com um aviso), consulte [distro/docker/README.md](../distro/docker/README.md). Os
pacotes Tomcat e WildFly também não os disponibilizam por padrão (reative-os com
`-Dfasti.webapp.classic.enabled=true` ou `FASTI_CLASSIC_WEBAPPS=true`). O zip do Run e o starter do Spring
Boot continuam disponibilizando-os como antes, para que as instalações em execução não deixem de funcionar; desative-os
com `fasti.webapp.classic.enabled: false`. As aplicações clássicas serão removidas em uma
versão futura.

O caminho antigo continua funcionando: toda solicitação para `/camunda/...` é redirecionada para o mesmo caminho
sob `/fasti/...`, mantendo o restante do caminho e a string de consulta. GET e HEAD recebem
`301`; todos os outros métodos (POST, PUT, DELETE, por exemplo, clientes antigos chamando `/camunda/api/...`)
recebem `308`, para que os clientes repitam a solicitação com o mesmo método e corpo. Os redirecionamentos
incluem `Cache-Control: no-store`, para que a volta a `/camunda` tenha efeito imediato.

Para restaurar `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (nesse caso, sem redirecionamento).
  Para desativar apenas o redirecionamento: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** exclua `webapps/camunda` (a aplicação de redirecionamento) e renomeie
  `webapps/fasti` para `webapps/camunda`.
- **WildFly:** exclua `standalone/deployments/camunda.war` (e `camunda.war.dodeploy`) e
  defina `<context-root>/camunda</context-root>` em `WEB-INF/jboss-web.xml` do WAR da
  aplicação web.

**Proxies reversos:** se um proxy encaminhar apenas `/camunda/` (ou reescrever caminhos, definir caminhos de
cookies, CSP ou verificações de integridade para `/camunda`), adicione `/fasti/` ou migre inteiramente para `/fasti/`.
O cookie `XSRF-TOKEN` (e `JSESSIONID` no Tomcat e no WildFly) agora tem o caminho `/fasti`,
portanto os usuários precisam entrar novamente. Favoritos e links antigos continuam funcionando pelo redirecionamento.
