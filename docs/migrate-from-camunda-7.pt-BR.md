[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | Português (Brasil) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

# Migração do Camunda 7 para o Fasti BPM

O Fasti BPM é baseado no Camunda 7 Community Edition 7.24.0 e é compatível com bancos de dados do Camunda 7.
É um projeto independente, sem afiliação com a Camunda Services GmbH.

<a id="summary"></a>

## Resumo

Se você executa o Camunda 7 **7.24** (Run, a imagem de contêiner, o starter do Spring Boot, Tomcat ou
WildFly), o Fasti funciona com **esse mesmo banco de dados sem alterar seu esquema**: ele usa o esquema 7.24
sem modificações e não grava tabelas, colunas nem versões de esquema próprias. Você pode substituir
os nós antigos, executar o Fasti em paralelo com eles e voltar ao Camunda 7.24 (consulte
[Reversão](#rollback-to-camunda-724) e os bancos de dados testados em [Limitações](#limits)).

Os bancos de dados do Camunda 7.23 e de versões anteriores devem primeiro ser atualizados para a versão 7.24 com os scripts SQL (consulte
[Versões anteriores](#older-versions-717723)). O Fasti nunca atualiza um esquema por conta própria; ele se recusa a
iniciar com um esquema anterior e informa quais scripts devem ser executados.

<a id="steps-container-image"></a>

## Etapas (imagem de contêiner)

1. **Faça backup do banco de dados.** Nenhuma das etapas abaixo altera o esquema, mas um backup é
   obrigatório antes de qualquer alteração em um sistema de produção.
2. **Verifique a versão do esquema:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — o maior valor
   deve ser `7.24.x`.
3. **Anote a configuração atual** do nó antigo: variáveis `DB_*`, nível de histórico
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), autenticação REST
   (`camunda.bpm.run.auth.enabled`), autorizações (`camunda.bpm.authorization.enabled`),
   plugins, LDAP/SSO, fuso horário da JVM, hash de senhas (`passwordEncryptor`, `saltGenerator`),
   prefixo das tabelas/esquema.
4. **Baixe a imagem** `fastidev/fasti-bpm:1.3.1` (ou compile-a a partir do código-fonte conforme
   descrito em [distro/docker/README.md](../distro/docker/README.md)).
5. **Inicie o Fasti com as mesmas variáveis `DB_*`** (e o mesmo `TZ`). Diferentemente do Camunda Run, a
   imagem do Fasti **exige autenticação REST e aplica autorizações por padrão**: as contas do banco de dados
   continuam funcionando, mas todo cliente REST deve enviar credenciais Basic ou um token bearer,
   e os usuários que não pertencem a `camunda-admin` precisam de permissões em `ACT_RU_AUTHORIZATION` (consulte "Seguro por
   padrão" em [distro/docker/README.md](../distro/docker/README.md)).
   `FASTI_INSECURE_DEMO=true` destina-se apenas a demonstrações. O Fasti registra no log
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Verifique:** entre em `/fasti/` com uma conta existente; verifique se os filtros, as tarefas, as instâncias e o histórico estão presentes; chame `GET /engine-rest/task` (com credenciais Basic) e `GET /actuator/health/readiness`.
7. **Direcione o tráfego** (balanceador de carga, DNS) para o Fasti; pare os nós antigos ou mantenha-os em execução (consulte
   [Operação mista](#mixed-operation-with-camunda-7-nodes)).

Starter do Spring Boot / Tomcat / WildFly: substitua os artefatos `org.camunda.bpm:camunda-*` pelos
artefatos `dev.fasti.platform:fasti-*` correspondentes e mantenha `camunda.bpm.*`, `bpm-platform.xml` e
os pacotes `org.camunda.bpm.*`. Não mantenha os dois JARs do mecanismo no classpath; nesse caso, o Fasti se recusa a iniciar (para conhecer as limitações dessa verificação, consulte "Verificação na inicialização" no
[embedding.pt-BR.md](embedding.pt-BR.md#startup-check)).

> **Starter do Spring Boot:** o Fasti funciona com **Spring Boot 4.1** e exige **Java 17 ou posterior**.
> As aplicações que usam o starter do Camunda 7.24 (Spring Boot 3.5) devem migrar para Spring Boot 4.1 e
> Java 17+ ao mesmo tempo que substituem os artefatos. Consulte o
> [embedding.pt-BR.md](embedding.pt-BR.md#what-is-different-from-camunda-7240-ce) e o
> [Registro de alterações](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Distribuições Tomcat e WildFly

> **Obrigatório ao assumir o banco de dados do Camunda Run** (ou de um servidor cujos processos foram
> implantados pela API REST): as distribuições Tomcat e WildFly mantêm
> `jobExecutorDeploymentAware=true`, assim como o Camunda 7. Os jobs (temporizadores, continuações assíncronas, novas tentativas)
> de implantações que não estão registradas no nó **não são executados, e nenhum erro é informado**.
> Antes de iniciar, defina `<property name="jobExecutorDeploymentAware">false</property>` em
> `conf/bpm-platform.xml` (Tomcat) ou nas propriedades do mecanismo em `standalone.xml` (WildFly).
> Mantenha `true` se outros nós executarem aplicações de processo com classes Java próprias (consulte
> [Operação mista](#mixed-operation-with-camunda-7-nodes)).

Em comparação com as distribuições Tomcat/WildFly do Camunda 7.24:

- **A API REST exige autenticação** (alteração incompatível). `/engine-rest` tem o filtro `camunda-auth`
  (HTTP Basic) habilitado: todo cliente REST deve enviar o nome de usuário e a senha de um usuário do mecanismo; caso contrário, recebe 401; apenas a lista de mecanismos (`/engine`) permanece aberta.
  Configure as credenciais nos clientes REST, nos workers de tarefas externas e nos scripts que chamam a API
  REST anonimamente antes de fazer a migração. Tokens bearer do OpenID Connect (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) também funcionam: solicitações com um token válido não precisam de senha. Para desativar a autenticação (**não recomendado**), remova o
  filtro `camunda-auth` e seu `filter-mapping` de `WEB-INF/web.xml` do WAR `engine-rest`.
- **Sem usuários padrão** (alteração incompatível). O exemplo de faturamento não é mais implantado, portanto não
  cria `demo/demo` (administrador), `john`, `mary` e `peter`; seu WAR é
  `examples/camunda-invoice.war`, que deve ser copiado para `webapps/` (Tomcat) ou
  `standalone/deployments/` (WildFly) para experimentá-lo. Em um banco de dados vazio, abra `/fasti/`
  e crie o primeiro administrador na página de configuração; em um banco de dados do Camunda 7, use as
  contas existentes.
- **As aplicações clássicas Cockpit, Tasklist, Admin e Welcome não são disponibilizadas** (alteração incompatível), assim como na
  imagem: suas páginas e seus favoritos levam à seção correspondente da nova interface, e as solicitações de seus arquivos retornam 404. Para reativá-las temporariamente (obsoletas, com remoção prevista), use uma destas configurações:
  `-Dfasti.webapp.classic.enabled=true` em `CATALINA_OPTS` (Tomcat) ou `JAVA_OPTS` (WildFly), a
  variável de ambiente `FASTI_CLASSIC_WEBAPPS=true` ou o parâmetro de contexto
  `fasti.webapp.classic.enabled` em `WEB-INF/web.xml` da aplicação web.

<a id="what-is-identical"></a>

## O que permanece igual

- O esquema e as linhas de `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (o Fasti não adiciona nenhuma linha de versão),
  o ID da instalação, a geração de IDs (`StrongUuidGenerator` no Run/Spring Boot, o
  `DbIdGenerator` padrão do mecanismo).
- Senhas: SHA-512 com um salt de 16 bytes, assim como no Camunda 7; os usuários existentes podem entrar, e
  as senhas alteradas no Fasti funcionam no Camunda 7.
- A API REST em `/engine-rest`: os mesmos endpoints, parâmetros e payloads, além de endpoints
  adicionais (a autenticação e alguns códigos de status diferem, consulte [O que muda](#what-differs)); as tarefas externas
  (`fetchAndLock`, long polling), os clientes Java e JS, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, as propriedades `camunda.bpm.*`
  e o namespace BPMN `camunda:`.
- Os filtros da Tasklist clássica (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) são lidos e gravados no mesmo formato pela nova Tasklist.

<a id="what-differs"></a>

## O que muda

- As aplicações web ficam em `/fasti/app/...`; `/camunda/...` redireciona para esse endereço (301/308). Se o seu proxy expõe apenas `/camunda`, exponha também `/fasti` ou defina `FASTI_WEBAPP_PATH=/camunda`. A raiz
  abre a nova aplicação web (`fasti.webapp.default-app: welcome` restaura Welcome, exceto
  na imagem, em que as aplicações clássicas estão desativadas).
- **Alteração incompatível, imagem de contêiner:** as aplicações clássicas Cockpit, Tasklist, Admin e Welcome não são
  disponibilizadas, pois AngularJS 1.8.2 e Bootstrap 3 chegaram ao fim de vida. Suas páginas e seus favoritos,
  inclusive sob `/camunda/app/`, redirecionam para a seção correspondente da nova interface web (`#/cockpit`,
  `#/tasklist`, `#/admin`); as solicitações de seus arquivos retornam 404. Migre os usuários para a nova interface web;
  `FASTI_CLASSIC_WEBAPPS=true` volta a disponibilizar as aplicações clássicas durante um período de transição, com um
  aviso. Tomcat e WildFly também não as disponibilizam (consulte
  [Distribuições Tomcat e WildFly](#tomcat-and-wildfly-distributions)). O ZIP do Run e o
  starter do Spring Boot ainda as disponibilizam, mas elas estão obsoletas e serão removidas em uma versão
  futura (`fasti.webapp.classic.enabled: false` as desativa antecipadamente).
- Com um esquema anterior, o Fasti se recusa a iniciar com `ENGINE-03111`, em vez de falhar mais tarde
  (`FASTI_SCHEMA_VERSION_CHECK=warn` para iniciar mesmo assim).
- As expressões em consultas e filtros são limitadas a uma lista de permissões (funções de data, usuário
  atual); filtros que usam beans ou funções personalizados precisam que eles sejam declarados nas propriedades de configuração
  do mecanismo `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` e
  `allowedExpressionPropertiesInQueries`.
- Comportamento da API REST: na imagem de contêiner e nas distribuições Tomcat e WildFly, toda
  requisição, exceto a lista de engines (`/engine`), exige credenciais Basic ou um bearer token;
  por isso, clientes REST e workers de tarefas externas precisam ser configurados para enviá-los
  (o código deles não muda). Em todas as distribuições, expressões de consulta fora da lista de
  permissões são rejeitadas com `400`; ler ou alterar anexos e comentários de tarefas sem
  permissão é respondido com `403` (com as verificações de autorização ativas); algumas
  requisições inválidas que eram respondidas com `500` agora são respondidas com `400` (recursos
  de implantação que não podem ser analisados, variáveis no formato proibido de serialização Java,
  parâmetros de consulta do tipo errado, senhas que violam a política de senhas). Consulte
  [Differences from Camunda 7](api/README.md#differences-from-camunda-7) (em inglês).
- Padrões da imagem: atualização do esquema `false` (`FASTI_DB_SCHEMA_UPDATE=true` para um banco de dados novo); sem
  usuário de demonstração, exemplo de faturamento ou filtro "Todas as tarefas"; CORS desativado (`FASTI_CORS_ALLOWED_ORIGINS=*`
  o reativa); `/actuator/health` adicionado; arquivos sob `/fasti` (arquivos montados em
  `/camunda/configuration` também são lidos); sem `--swaggerui`.
- OAuth2/OIDC: `camunda-admin` em uma declaração de grupos não concede mais direitos de administrador por si só
  (`fasti.security.oidc.groups.admin`); `group-name-delimiter` é texto literal, não uma expressão regular;
  o logout exige um POST de mesma origem.
- Nova Tasklist: um filtro sem `refresh` é atualizado automaticamente.
- O JAR do Run é `fasti-bpm-run-core.jar`.
- As correções de segurança em relação à versão 7.24.0 (autorização de anexos/comentários, configuração inicial, ...) podem fazer com que algumas chamadas que antes eram bem-sucedidas retornem 403; consulte as seções *Security* do [CHANGELOG](../CHANGELOG.md) e <https://fasti.dev/security>.

<a id="rollback-to-camunda-724"></a>

## Reversão para o Camunda 7.24

Pare o Fasti e inicie novamente os nós do Camunda 7.24 no mesmo banco de dados. Tudo o que foi feito no
Fasti fica visível lá. O que permanece: linhas `fasti.prefs.*` em `ACT_ID_INFO` (preferências da
nova interface web, ignoradas pelo Camunda 7) e `refresh: true` nos filtros salvos pela nova Tasklist em que
essa propriedade estava ausente. Os processos implantados que usam recursos exclusivos do Fasti não são executados no Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Operação mista com nós do Camunda 7

O Fasti e o Camunda 7.24 podem funcionar com um único banco de dados ao mesmo tempo (testado: ambos veem os dados um do
outro, e os jobs são executados em qualquer um dos nós). Requisitos:

- o **mesmo nível de histórico** (a imagem usa o nível armazenado), o **mesmo fuso horário da JVM**, as
  mesmas configurações de senha e o mesmo prefixo de tabelas;
- **executor de jobs:** por padrão, o Fasti (imagem, Run, Spring Boot) captura os jobs de todas as implantações, assim como o Run.
  As distribuições Tomcat e WildFly usam `jobExecutorDeploymentAware=true` por padrão (assim como o Camunda
  7): os jobs (temporizadores, continuações assíncronas, novas tentativas) de implantações não registradas nesse
  nó, incluindo implantações feitas pelo Run ou via REST antes da última reinicialização, **não são
  executados**; ao assumir o banco de dados do Run, defina essa propriedade como `false` em `conf/bpm-platform.xml`
  (Tomcat) ou `standalone.xml` (WildFly). Se os nós antigos executam aplicações de processo com classes que o Fasti não possui (`JavaDelegate`, listeners, beans),
  o Fasti capturaria seus jobs, que então falhariam e gerariam incidentes. Nesse caso, defina
  `FASTI_JOB_EXECUTOR_ENABLED=false` no Fasti ou configure **todos** os nós para considerar apenas as implantações registradas neles (o
  executor de jobs de um nó executa apenas jobs de implantações registradas nesse nó; uma implantação feita via
  REST é registrada apenas no nó que a recebeu, até uma reinicialização). As chamadas REST síncronas (concluir uma tarefa,
  iniciar uma instância) continuam executando delegates no nó que as recebe — não direcione esses
  processos para o Fasti;
- **balanceador de carga:** sessões persistentes para as aplicações web; chamadas REST com autenticação Basic não precisam delas;
- as aplicações web clássicas (nos nós do Camunda 7 ou no Fasti com
  `FASTI_CLASSIC_WEBAPPS=true`) e a nova aplicação compartilham filtros e usuários.

<a id="older-versions-717723"></a>

## Versões anteriores (7.17–7.23)

Faça backup; execute `sql/upgrade/<db>_engine_<from>_to_<to>.sql` em ordem até a versão 7.24, incluindo
os scripts de correção pendentes da sua versão secundária atual (fornecidos em `configuration/sql/upgrade`, na
imagem em `/fasti/configuration/sql/upgrade`); verifique se `ACT_GE_SCHEMA_LOG` contém `7.24.0`;
inicie o Fasti. Exemplo 7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, depois
`postgres_engine_7.23_to_7.24.sql`. Os nós do Camunda anteriores à versão 7.24 não devem continuar em execução no
esquema atualizado, exceto dentro das regras de atualização gradual do Camunda (apenas uma versão secundária, e os
nós antigos não devem usar recursos novos).

<a id="performance"></a>

## Desempenho

A imagem não usa mais os padrões do Camunda Run para o executor de jobs e o pool de conexões (até o
Fasti 1.1 usava): ela roda 10 threads de jobs, uma fila de 10 e 10 jobs por aquisição (Camunda Run:
3 threads principais, máximo de 10, fila de 3, 3 por aquisição) e um pool de até 20 conexões, das
quais 10 ficam abertas quando ocioso (Camunda Run: 10). Dimensione o limite de conexões do banco de
dados para os pools de todos os nós juntos, incluindo os nós do Camunda 7 ([ajuste em tempo de
execução](tuning-runtime.md), em inglês). Se você ajustou o pool de conexões ou o executor de jobs
da sua instalação anterior do Camunda 7, transfira esses valores (`FASTI_DB_CONN_MAXACTIVE`,
`FASTI_DB_CONN_MINIDLE`, `CAMUNDA_BPM_JOB_EXECUTION_*`); mais orientações estão em
`distro/docker/README.md`, "Ajuste de desempenho". O Fasti nunca altera sozinho os índices do
esquema; para bancos de dados grandes, ele fornece índices opcionais como scripts que um
administrador executa (`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`,
veja [performance.md](performance.md)), seguros ao lado de nós do Camunda 7.24 e removidos pelo
`_drop.sql` correspondente.

<a id="limits"></a>

## Limitações

- Apenas o esquema da Community Edition 7.24 é compatível; os esquemas de correções da Enterprise não foram testados.
- Testado com a imagem de contêiner (o Camunda Run 7.24.0 cria e preenche inicialmente o banco de dados → o Fasti
  assume → retorno ao Run → ambos ao mesmo tempo):

  | Banco de dados | Driver | Transição para o Fasti | Reversão | Operação mista | Atualização 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | incluído | aprovado | aprovado | aprovado | não testado |
  | MariaDB 11.8 | MariaDB Connector/J (incluído) ou MySQL Connector/J | aprovado | aprovado | aprovado | aprovado |
  | MySQL 8.4 | incluído | aprovado | — | aprovado | aprovado |
  | SQL Server 2022 (Developer) | incluído (`encrypt=false`) | aprovado | aprovado | aprovado | não testado |
  | Oracle Database 23ai Free | `ojdbc11` via `FASTI_EXTRA_LIB` | aprovado | aprovado | aprovado | não testado |
  | DB2 | — | não testado | | | |

  Além disso: dois nós do Fasti no MariaDB (MariaDB Connector/J), concluindo as mesmas tarefas e buscando tarefas externas em paralelo, não perderam nem duplicaram nada; a transação perdedora recebeu um erro de bloqueio otimista. MySQL/MariaDB exigem `transaction-isolation=READ-COMMITTED`. No MySQL,
  forneça a cada mecanismo seu próprio banco de dados: o mecanismo (tanto o Camunda Run quanto o Fasti) procura tabelas
  em todos os bancos de dados que a conta consegue acessar.
- Os plugins e as aplicações de processo compilados para o Camunda 7 devem ser recompilados com os artefatos do Fasti
  para serem executados dentro do Fasti. Os clientes REST e os clientes de tarefas externas não precisam de alterações
  no código; com a imagem de contêiner, Tomcat ou WildFly, eles precisam ser configurados para enviar credenciais
  Basic ou um bearer token.
- A verificação do esquema lê apenas `ACT_GE_SCHEMA_LOG`; ela não compara colunas.
