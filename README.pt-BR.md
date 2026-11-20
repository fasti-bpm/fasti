[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | Português (Brasil) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

# Fasti BPM Platform

**Make Camunda 7 Great Again.** O Camunda 7 Community Edition encerrou seu ciclo com a versão 7.24.0, sua última
versão. O Fasti BPM dá continuidade a partir daí: o mesmo motor no mesmo banco de dados, com
correções e atualizações de bibliotecas contínuas, além de uma nova interface web.

> **O Fasti BPM não é afiliado à Camunda Services GmbH; Camunda é uma marca registrada da Camunda Services GmbH.**
> O Fasti BPM é um projeto independente de código aberto (Apache-2.0). Não faz parte da Camunda Services GmbH, nem é patrocinado
> ou endossado por ela. O nome "Camunda" é usado apenas para descrever a origem do
> código-fonte e a compatibilidade do Fasti.

<a id="quick-start"></a>

## Início rápido

A imagem está disponível no Docker Hub (`fastidev/fasti-bpm`) e no GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), para amd64 e arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.1
```

Abra <http://localhost:8080/fasti/> e entre. Sem `DB_URL`, a imagem usa um banco de dados H2
dentro do contêiner, apenas para avaliação. A API REST exige credenciais Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Compile a imagem por conta própria** a partir do código-fonte. Isso exige Docker, JDK 17+ e acesso de
rede ao Maven Central, nodejs.org e ao registro npm (a compilação baixa Node/npm para a
interface web); a primeira compilação leva cerca de 10–20 minutos:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.1
```

A imagem **exige autenticação REST e aplica autorizações por padrão** (diferentemente do Camunda
Run); somente `FASTI_INSECURE_DEMO=true` as desativa, para demonstrações.

<a id="highlights"></a>

## Destaques

- **Nova interface web** em `/fasti/` (React):
  - pesquisa de instâncias de processo por várias variáveis ao mesmo tempo, combinadas com AND/OR;
  - linha do tempo do caso dentro da tarefa (comentários e ações em cada etapa);
  - um botão "Obter próximo trabalho";
  - prazos e urgência (SLA), trabalhos em atraso;
  - carga de trabalho da equipe por usuário e grupo;
  - procedimentos operacionais anexados a incidentes;
  - uma página de integridade do sistema (incidentes, jobs com falha, jobs atrasados, tarefas externas, lotes);
  - layout adaptado a dispositivos móveis;
  - 13 idiomas.
- **Autenticação única (OIDC)**: Keycloak, Microsoft Entra ID e outros provedores OpenID Connect;
  declarações mapeadas para grupos e tenants; tokens bearer JWT para a API REST. Disponível para Run,
  Spring Boot, Tomcat e WildFly.
- **Reforço de segurança**: lista de permissões para expressões em consultas e filtros, verificações de anexos e
  comentários dentro do motor, configuração inicial, permissões do Cockpit, redirecionamento aberto, cabeçalhos de
  aplicações web, cookies e CSP. Detalhes no [CHANGELOG](CHANGELOG.md).
- **Autenticação ativada por padrão**: a imagem exige credenciais para REST e aplica
  autorizações em todos os modos.
- **Proteção da versão do esquema**: o motor se recusa a iniciar com um esquema anterior ao 7.24 e nunca
  atualiza um esquema por conta própria.
- **Mitigação do Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): a imagem amd64
  inclui um Temurin JRE corrigido e desativa a função intrínseca Base64; o motor emite um aviso quando
  é executado em uma JVM afetada.
- **Desempenho**: o primeiro carregamento da nova interface web cai de 960 KB para 330 KB (gzip ativado por
  padrão); a vazão do executor de jobs aumenta cerca de 2,5 vezes com ajustes (números e configuração dos testes em
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Plataforma atual**: Java 17 ou posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## Instalação

<a id="one-command-on-your-existing-camunda-724-database"></a>

### Um comando, no seu banco de dados Camunda 7.24 existente

A imagem aceita as variáveis `DB_*` da antiga imagem do Camunda 7 Run. Faça um backup do banco de dados
e execute:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.1
```

Usuários, senhas, grupos, tenants, autorizações, filtros, implantações, instâncias em execução,
histórico, jobs e incidentes permanecem como estão. A porta fica vinculada apenas a esta máquina; para acesso
remoto, use um proxy reverso com HTTPS. Entre em `/fasti/` com uma conta existente.
Como a autenticação está ativada, todo cliente REST (workers de tarefas externas, integrações) deve enviar
credenciais Basic ou um token bearer.

- **Lado a lado** com nós do Camunda 7.24 CE no mesmo banco de dados: mesmo nível de histórico, mesmo
  `TZ`, mesmas configurações de senha. Se os nós antigos forem aplicações de processo com classes Java que
  o Fasti não possui, defina `FASTI_JOB_EXECUTOR_ENABLED=false` no Fasti (ou configure todos os nós
  para considerar as implantações) e não inicie esses processos nem conclua suas tarefas pelo Fasti: uma
  chamada síncrona executa os delegates no nó que a recebe.
- **Reversão**: o Fasti não altera o esquema, portanto pare o Fasti e inicie os nós do Camunda 7.24
  novamente no mesmo banco de dados. Processos implantados que usam recursos exclusivos do Fasti não serão executados no
  Camunda 7; consulte [Reversão para o Camunda 7.24](docs/migrate-from-camunda-7.pt-BR.md#rollback-to-camunda-724).
- **Esquemas anteriores (7.17–7.23)**: primeiro execute os scripts `sql/upgrade` incluídos na imagem
  (`/fasti/configuration/sql/upgrade`) até chegar ao 7.24; por exemplo, 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Testados com a imagem (assumindo um banco de dados criado pelo Camunda Run 7.24.0, revertendo
e executando lado a lado): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (o driver Oracle não está incluído; adicione `ojdbc11` por conta própria; a reversão não foi testada
no MySQL). **DB2 não foi
verificado.** O H2 é apenas para avaliação. Resultados por teste e limitações:
[guia de migração](docs/migrate-from-camunda-7.pt-BR.md#limits).

Guias detalhados:

- [Instalação com Docker](docs/install-docker.pt-BR.md): H2, PostgreSQL com `docker run` e
  `docker compose`, bancos de dados existentes, modo lado a lado, verificações de integridade, atualização, reversão,
  solução de problemas.
- [Migração do Camunda 7](docs/migrate-from-camunda-7.pt-BR.md): etapas, o que é idêntico e o que
  difere, reversão, limitações.
- [Referência de variáveis de ambiente da imagem](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Sem Docker: pacote Tomcat (download em fasti.dev)

Pacote Tomcat 1.0.0 pronto para uso (página de download: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Verifique a soma de verificação antes de extrair, por exemplo, `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

A distribuição Tomcat também pode ser compilada a partir do código-fonte (`distro/tomcat`). Leia `README.txt` no
pacote antes de iniciá-lo: a API REST (`/engine-rest`) exige autenticação HTTP Basic
por padrão, nenhum usuário é criado (crie o primeiro administrador na página de configuração de `/fasti/`),
e, ao assumir um banco de dados do Camunda Run, defina `jobExecutorDeploymentAware=false` em
`conf/bpm-platform.xml`; caso contrário, os temporizadores e jobs assíncronos das implantações existentes não serão executados.

<a id="a-ui-built-from-real-user-complaints"></a>

## Uma interface criada a partir de reclamações reais de usuários

Os recursos da nova interface web foram escolhidos com base no que usuários do Camunda 7 escreveram publicamente no
fórum do Camunda e no GitHub, por exemplo:

| O que os usuários pediram | Fonte | No Fasti |
|---|---|---|
| Pesquisar instâncias por várias variáveis, com OR | fórum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Construtor de condições com múltiplas variáveis, AND/OR |
| Ver os comentários em cada etapa de um caso | fórum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Linha do tempo do caso na tarefa; comentários na própria instância de processo |
| Ordenar tarefas por variáveis, com as mais antigas primeiro | fórum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Ordenação por variáveis, prazos e urgência |
| Delegação, substitutos durante ausências | fórum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Já existe delegação por tarefa; substitutos durante ausências estão no [plano de evolução](#roadmap-plan) |
| Chave de negócio e variáveis ao iniciar um processo sem formulário | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Sim |
| Baixar o XML BPMN implantado | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Sim |
| Rótulos consistentes, vários idiomas | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 idiomas |

<a id="comparison"></a>

## Comparação

Uma comparação compacta baseada na documentação pública de cada projeto (consultada em novembro de 2026);
"não verificado" significa que não encontramos uma fonte confiável. Cada projeto tem sua própria direção e é uma
boa escolha para necessidades diferentes; consulte a documentação antes de decidir.

| | Licença | Executa em um BD C7 existente | Migração necessária | Patches de segurança | Java / Spring Boot | Interface | SSO | Imagem Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Sim, esquema 7.24 inalterado | Imagem: nenhuma. Aplicações com motor embarcado: novas coordenadas Maven, Spring Boot 4.1 e Java 17+; pacotes `org.camunda.*` mantidos | Sim, consulte a [política abaixo](#commitments-support-and-security-patches) | 17+ / 4.1 | Nova (React) + clássica (descontinuada) | OIDC integrado | Sim, a partir de 1.0.0 |
| Camunda 7 CE 7.24 | Código-fonte Apache-2.0; alguns componentes web incluídos sob MIT ([13]) | (o original) | — | Nenhuma versão CE após 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 pelo starter de segurança | Antiga imagem Run |
| Camunda 7 Enterprise | Comercial | Sim (mesma linha de produto) | Não | Até 04/2030, extensão paga até 04/2032 ([3]) | não verificado | Cockpit Enterprise | não verificado | não verificado |
| Camunda 8 | Camunda License 1.0; produção autogerenciada exige licença desde 8.6 ([4]) | Não (motor diferente) | Sim (modelos, instâncias, dados) | Sim (fornecedor) | motor diferente, não comparável | Operate, Tasklist | não verificado | não verificado |
| Operaton | Apache-2.0 | Mesma estrutura de tabelas do 7.24; a versão 2.2 registra a versão de esquema 7.25 por meio de um script de atualização ([5]) | Pacotes renomeados para `org.operaton.*` (receita OpenRewrite disponível); script de versão do esquema (2.2) | Sim, linha 2.2.x ([6]) | 17+ / 4.1 ([5]) | Clássica + webapps-neo (prévia) | não verificado | Sim ([7]) |
| CIB seven | Apache-2.0; EE exige uma chave de licença | Sim (alterações do 7.24 incorporadas) | Pacotes renomeados para `org.cibseven.*`, prefixo `cibseven.*` | Sim, por versão ([8]) | 17, 21 / 3.5 e 4.0 ([9]) | Cliente web Vue 3 | OAuth2/JWT para REST ([8]) | Sim ([8]) |
| OrqueIO | Apache-2.0 | Compatível com as tabelas do C7 segundo sua documentação ([10]) | Grupo Maven `io.orqueio`; renomeação de pacotes não informada | Sim, segundo sua documentação ([10]) | 21 / 3.5, 4.0, 4.1 | Reescrita em Angular | OIDC com múltiplos provedores | não verificado |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | O projeto afirma que seu esquema permanece compatível com o Camunda 7 (não verificado pelo Fasti) ([14]) | Pacotes renomeados para `org.eximeebpms.*`, Model API renomeada | Sim, as duas versões menores mais recentes ([11]) | 21 / versão do Spring Boot não informada | Monitor separado | não verificado | Sim (repositório `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | não verificado | não verificado | não verificado | 21 (imagem Corretto) / não verificado | Control Center | não verificado | Sim ([12]) |

Os pontos fracos do próprio Fasti, apresentados com clareza: um projeto jovem com uma equipe pequena, ainda sem suporte
comercial e sem verificação do DB2.

Fontes: [1] [Anúncio do fim de vida da CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [Ambientes compatíveis com 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Extensão do Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Licenciamento do Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [SECURITY.md do Operaton](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [Ambientes compatíveis com CIB seven](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [Documentação do OrqueIO](https://docs.orqueio.io/);
[11] [SECURITY.md do EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Licenças do Camunda 7](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
visão geral independente: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## Linha do tempo

<a id="camunda-7-milestones"></a>

### Marcos do Camunda 7

| Data | Marco | Fonte |
|---|---|---|
| 2024-03-06 | A Camunda anuncia o fim de vida do Camunda 7 CE para outubro de 2025, com 7.24 como a última versão CE | [Fórum do Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | A Camunda estende o suporte do Camunda 7 Enterprise até 2030-04-09, com suporte estendido pago até abril de 2032 | [Blog da Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Tag `7.24.0` criada e publicada no Maven Central: a última versão CE | [tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Data de lançamento da 7.24 conforme o cronograma da Camunda; após essa data, não há novas versões CE, incluindo patches de segurança. Desde então, o repositório CE foi arquivado | [anúncio](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [página de suporte](https://docs.camunda.org/enterprise/announcement) |
| Abril de 2030 | Fim do suporte do Camunda 7 Enterprise (7.24 LTS: 2030-04-13) | [página de suporte](https://docs.camunda.org/enterprise/announcement) |
| Abril de 2032 | Fim do suporte estendido pago do Camunda 7 Enterprise | [página de suporte](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

O Fasti dá continuidade diretamente a partir da tag `7.24.0`; todas as correções e alterações são contabilizadas a partir desse ponto
(`git log 7.24.0..`). As fases abaixo estão ordenadas pelo trabalho realizado, sem datas:

1. **Fundação**: Java 17, Spring Boot 4.1, atualizações de bibliotecas; coordenadas Maven
   `dev.fasti.platform`.
2. **Segurança**: correções para os problemas divulgados publicamente da linha 7.x, lista de permissões para expressões,
   verificações de anexos/comentários dentro do motor, reforço de segurança das aplicações web.
3. **Nova interface web** em `/fasti/`, substituindo gradualmente os clássicos Cockpit, Tasklist e Admin.
4. **Autenticação única** (OIDC) para Run, Spring Boot, Tomcat e WildFly.
5. **Substituição direta em bancos de dados existentes**: a imagem aceita as antigas variáveis `DB_*` e verifica a versão do esquema; testada em cinco sistemas de banco de dados.
6. **Revisão de segurança antes do lançamento** (revisão de código, teste de penetração da imagem, varredura básica com
   ZAP; os problemas encontrados foram corrigidos) e **mitigação do Java4Shell**.
7. **1.0.0**: a primeira versão (2026-11-20).

<a id="roadmap-plan"></a>

## Plano de evolução (planejamento)

> Este é um **plano**, não um compromisso com datas. O escopo e a ordem podem mudar.

### Novembro e dezembro de 2026 (em andamento)

Primeiro, tudo o que os usuários já pediram.

**1.x: Mais fácil de operar**

- Artefatos Maven no Maven Central.
- Chart Helm e manifestos de exemplo para Kubernetes.
- Métricas do Prometheus e rastreamento com OpenTelemetry.
- Logs estruturados em JSON.
- Atualização do MyBatis, que também atualiza o OGNL.
- Verificação do DB2 e revisão das traduções por falantes nativos.

**2.0: O que pedem as equipes de setores regulados**

- Um substituto durante ausências, para que o trabalho não fique esperando alguém de licença
  ([fórum 2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199)).
- Controle de quatro olhos para aprovações.
- Uma trilha de auditoria imutável, assinada ou com hash, com relatórios de auditoria.
- CMMN na nova interface e descontinuação das aplicações AngularJS.

**Novos territórios**

- Linhas de suporte de longo prazo.
- Um servidor MCP para o motor.
- Etapas de IA nos processos.

### Metas para 2027

- **1º trimestre de 2027: Rodar em escala**: um operador Kubernetes para atualizações graduais e
  escalonamento; histórico transmitido para Kafka e OpenSearch, para que o banco de dados de runtime
  continue pequeno; benchmarks de desempenho publicados, repetidos a cada versão.
- **2º trimestre de 2027: Um kit de ferramentas para quem desenvolve**: um SDK de conectores, com
  conectores para REST, Kafka, e-mail e armazenamento de objetos; um kit de testes de processos com
  testes de cenários e relatórios de cobertura; um construtor de formulários com componentes
  reutilizáveis.
- **3º trimestre de 2027: Ver como os processos se saem**: análise de processos (mapas de calor,
  gargalos e relatórios de duração); metas de nível de serviço com alertas; um assistente que
  rascunha BPMN a partir de texto e explica incidentes.
- **4º trimestre de 2027: Confiança em escala corporativa**: administração multi-tenant com cotas
  para cada tenant; uma auditoria de segurança independente, com o relatório publicado; builds
  reproduzíveis com proveniência assinada para cada versão.

<a id="commitments-support-and-security-patches"></a>

## Compromissos: suporte e patches de segurança

> Esta política é mantida pela equipe do projeto Fasti BPM e pode ser atualizada; a versão neste
> repositório é a que está em vigor.

- **Linha com suporte:** a versão menor mais recente da linha `1.x` recebe correções de bugs e patches de
  segurança (como versões de correção, por exemplo, `1.0.1`).
- **Patches de segurança:** vulnerabilidades no Fasti e nas bibliotecas que ele inclui têm prioridade sobre
  outros trabalhos; a imagem é recompilada quando a imagem base ou o JRE recebe uma correção. O projeto acompanha
  avisos públicos de segurança sobre o Camunda 7 e seus forks e verifica se o Fasti é afetado.
- **Compatibilidade:** a linha `1.x` mantém o esquema de banco de dados 7.24 e os pacotes `org.camunda.bpm.*`.
  Alterações incompatíveis são marcadas como **BREAKING** no [CHANGELOG](CHANGELOG.md).
- **Nível de suporte:** este é um projeto da comunidade; o suporte é prestado conforme os recursos disponíveis, **sem SLA**,
  e ainda não há suporte comercial.
- **Relato de vulnerabilidades:** **não** abra uma issue pública; consulte
  [SECURITY.md](SECURITY.md#security-policy) (relato privado pelo GitHub).
- **Defeitos corrigidos:** as seções *Corrigido* e *Segurança* do [CHANGELOG](CHANGELOG.md) listam
  o que foi corrigido em relação ao Camunda 7 CE 7.24.0; os avisos estão listados em
  [SECURITY.md](SECURITY.md#advisories).

<a id="documentation"></a>

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/install-docker.md](docs/install-docker.pt-BR.md) | Instalação com Docker, passo a passo |
| [docs/api/README.md](docs/api/README.md) | Integração com a API REST, clientes de tarefas externas em Java e JavaScript |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.pt-BR.md) | Migração do Camunda 7, reversão, limitações |
| [distro/docker/README.md](distro/docker/README.md) | Imagem: variáveis de ambiente, drivers, operações, ajustes |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | A nova interface web |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell e Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Alterações, segurança, contribuições |

<a id="what-this-is"></a>

## O que é este projeto

Este repositório contém o motor de processos BPMN 2.0 / DMN, a API REST, as aplicações
web (Cockpit, Tasklist, Admin) e as distribuições (Run, Tomcat, WildFly). Ele
parte da tag `7.24.0` do Camunda 7 CE e dá continuidade com correções de bugs e atualizações de
bibliotecas.

O objetivo é a **compatibilidade retroativa** com aplicações executadas no Camunda 7:

- os pacotes `org.camunda.bpm.*`, o namespace BPMN
  `http://camunda.org/schema/1.0/bpmn` e o prefixo de configuração `camunda.bpm.*` são mantidos;
- o esquema de banco de dados do 7.24 é mantido;
- todas as modalidades de implantação são mantidas: Spring Boot, Run, Tomcat, WildFly; `javax` e `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## O que é diferente em relação ao Camunda 7.24.0 CE

- **Segurança:** correções para os problemas divulgados publicamente da linha 7.x (expressões em
  consultas, configuração inicial, acesso a anexos e comentários, permissões do Cockpit, redirecionamento
  aberto), além de proteções adicionais: uma lista de permissões para expressões em consultas e
  filtros, verificações de anexos e comentários dentro do motor, filtros de segurança reforçados,
  cabeçalhos e cookies nas aplicações web.
- **Estabilidade:** eliminação do vazamento de memória quando scripts e expressões DMN são avaliados com
  JavaScript (GraalJS), além de várias outras correções no motor.
- **Plataforma:** Java 17 ou posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21 e muitas outras
  bibliotecas atualizadas para versões com correções de segurança.

Algumas correções alteram os padrões para um comportamento mais seguro; leia "O que difere" no
[guia de migração](docs/migrate-from-camunda-7.pt-BR.md#what-differs) antes de atualizar.

<a id="versioning"></a>

## Versionamento

O Fasti BPM Platform segue o [Versionamento Semântico](https://semver.org/), começando em `1.0.0`.
A linha `1.x` é compatível com aplicações escritas para o Camunda 7.24.

<a id="maven-coordinates"></a>

## Coordenadas Maven

Os artefatos do Fasti BPM Platform têm suas próprias coordenadas Maven. **Os pacotes Java
permanecem `org.camunda.bpm.*`**, portanto o código das aplicações mantém suas importações; o prefixo de configuração `camunda.bpm.*`,
os namespaces BPMN/DMN, o esquema do banco de dados e os nomes dos módulos no
WildFly também permanecem inalterados. No que diz respeito ao Maven, apenas as declarações de dependências
mudam (o caminho da aplicação web muda separadamente, consulte
[Caminho da aplicação web](#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; os grupos `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` e `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** o prefixo `camunda-` passa a ser `fasti-`, o restante é mantido.
- **version:** a versão do Fasti BPM Platform (consulte [Versionamento](#versioning)) em vez de
  `7.24.0`.

Nenhuma versão desses artefatos foi publicada no Maven Central ainda. Até lá, compile
a partir do código-fonte e disponibilize os artefatos no repositório Maven usado pelo seu projeto.

Gerencie as versões com o BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Artefatos usados com frequência:

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

A biblioteca FEEL `org.camunda.feel:feel-engine` é uma biblioteca externa que não faz parte
deste repositório; suas coordenadas permanecem como estão.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Bibliotecas de terceiros que ainda trazem o motor original como dependência

Como os pacotes permanecem inalterados, o motor original (`org.camunda.bpm:camunda-engine`)
e `fasti-engine` contêm classes com os mesmos nomes. O Maven os trata como duas bibliotecas
sem relação entre si e não remove nenhuma delas. Se uma biblioteca de terceiros (uma extensão da comunidade,
por exemplo) depender dos artefatos originais, exclua-os e declare os artefatos
Fasti correspondentes em seu lugar:

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` lista os artefatos originais que ainda estão
presentes (`org.camunda.feel:feel-engine` é legítimo, não o exclua).

<a id="startup-check"></a>

### Verificação na inicialização

Quando é criado, o motor inspeciona o classpath e **se recusa a iniciar** se encontrar
as classes do motor de processos em mais de uma biblioteca; o erro `ENGINE-12021` lista
a localização de cada uma delas. O starter do Spring Boot realiza a mesma verificação assim que a
aplicação inicia. Uma biblioteca visível apenas pelo carregador de classes pai (um motor
compartilhado do servidor ao lado de um motor embarcado em uma aplicação web, por exemplo) não é
contabilizada. Se sua configuração for intencional, a verificação pode ser desativada com uma propriedade de sistema:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limitação: se a biblioteca do motor original preceder `fasti-engine` no classpath, o
motor será construído pelas classes da biblioteca original, que não contêm essa verificação.
Nesse caso, apenas as aplicações que usam o starter do Fasti para Spring Boot são interrompidas; com
outras integrações, revise as dependências conforme descrito acima.

<a id="web-application-path-fasti"></a>

## Caminho da aplicação web: `/fasti`

Cockpit, Tasklist, Admin e a nova interface web são servidos sob `/fasti` (anteriormente `/camunda`)
por todas as distribuições: `http://localhost:8080/fasti/` abre a nova interface
(`/fasti/app/next/default/`), e as aplicações clássicas ficam em `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` e `/fasti/app/admin/...`. A API REST permanece em `/engine-rest`.

**As aplicações clássicas estão descontinuadas.** Os clássicos Cockpit, Tasklist, Admin e Welcome
são construídos com AngularJS 1.8.2 e Bootstrap 3, ambos em fim de vida. **A imagem do contêiner não
os disponibiliza por padrão** (alteração incompatível): suas páginas e favoritos antigos redirecionam para a seção correspondente da nova interface, e as solicitações de seus arquivos retornam `404`; `FASTI_CLASSIC_WEBAPPS=true` os
reativa (com um aviso), consulte [distro/docker/README.md](distro/docker/README.md). Os
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

<a id="single-sign-on-openid-connect"></a>

## Autenticação única (OpenID Connect)

Disponível no **Run** (`./start.sh --oauth2`, módulo `internal/oauth2`) e em aplicações **Spring Boot**
com `fasti-bpm-spring-boot-starter-security`. É ativada ao declarar um
registro de cliente do Spring Security (`spring.security.oauth2.client.*`). Para **Tomcat** e
**WildFly**, consulte o final desta seção.

- **Login:** a página de login da nova aplicação web mostra "Entrar com &lt;provedor&gt;"
  em vez do formulário de senha (rótulo: `fasti.security.oidc.login-label`, usa por padrão o
  `client-name`; com vários registros, escolha um com
  `fasti.security.oidc.registration-id`). O fluxo de código de autorização usa `state`, `nonce`
  e PKCE (S256, também para clientes confidenciais); o ID da sessão é substituído no login. Os usuários
  retornam à página da nova aplicação web de onde partiram (somente caminhos sob
  `/fasti/app/next/` são aceitos, portanto não há redirecionamento aberto).
- **Logout:** `POST /logout` (somente solicitações da mesma origem) encerra a sessão; com
  `camunda.bpm.oauth2.sso-logout.enabled=true`, o usuário segue para o endpoint de encerramento de
  sessão do provedor (logout iniciado pelo RP, `post-logout-redirect-uri`). O logout por
  canal de retaguarda do Spring Security permanece ativado. `GET /logout` não encerra mais a sessão do usuário.
- **Expiração da sessão:** quando o token de acesso não pode ser renovado ou a sessão expira,
  as solicitações para `/fasti/api/**` retornam `401` (sem redirecionamento), e a nova aplicação web mostra sua página de login.
- **CSRF:** `/fasti/api` e `/fasti/app` mantêm o filtro CSRF próprio das aplicações web
  (`X-XSRF-TOKEN`); a proteção CSRF do Spring Security permanece desativada, como no projeto de origem.
- **Grupos, administradores, tenants:** a declaração de grupos é
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nome da propriedade no projeto de origem; declarações
  aninhadas, como `realm_access.roles`, funcionam). `fasti.security.oidc.groups.admin` lista os
  valores de declaração que concedem `camunda-admin`; `groups.mapping` mapeia valores de declaração para IDs de
  grupos do motor; `groups.include-unmapped=false` descarta valores não mapeados. **Alteração em relação ao projeto de origem:** um
  valor de declaração `camunda-admin` não concede mais direitos de administrador por si só, e
  autoridades como `SCOPE_openid` ou `OIDC_USER` não se tornam mais grupos.
  `fasti.security.oidc.tenants.claim` mapeia tenants. Nome e e-mail:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Provedor de identidade:** por padrão, o motor lê usuários, grupos e tenants do
  token da sessão atual (somente leitura). Defina
  `camunda.bpm.oauth2.identity-provider.enabled=false` para usar o banco de dados em seu lugar (os grupos
  passam a vir das tabelas de associação). O provisionamento automático não está implementado.
  Enquanto os usuários vêm do token (o padrão), a página de configuração inicial (primeiro administrador)
  fica desativada, pois os administradores vêm das declarações (`groups.admin`); anteriormente, usuários fora do grupo de administradores eram enviados à página de configuração.
- **API REST com tokens bearer:** `fasti.security.oidc.rest.bearer-enabled=true` aceita
  `Authorization: Bearer <JWT>` em `/engine-rest`, além da autenticação básica.
  `rest.issuer-uri` e `rest.audiences` são obrigatórios; as assinaturas são verificadas com o
  JWKS do emissor usando apenas algoritmos assimétricos (`rest.jws-algorithms`, padrão `RS256`), e
  `iss`, `aud`, `exp`, `nbf` são validados. O Run também exige `camunda.bpm.run.auth.enabled=true`.
  Um cookie de sessão do navegador nunca autentica `/engine-rest`.

Exemplo com Keycloak (`configuration/default.yml` do Run):

```yaml
spring.security.oauth2.client:
  registration.keycloak:
    client-id: fasti
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    client-name: Keycloak
    scope: openid, profile, email
  provider.keycloak:
    issuer-uri: https://sso.example.com/realms/fasti
    user-name-attribute: preferred_username
camunda.bpm:
  oauth2:
    # the client's "Group Membership" mapper, with "Full group path" off
    identity-provider.group-name-attribute: groups
    sso-logout:
      enabled: true
      post-logout-redirect-uri: "{baseUrl}/fasti/app/next/default/"
  run.auth.enabled: true
fasti.security.oidc:
  groups.admin: [fasti-admins]
  rest:
    bearer-enabled: true
    issuer-uri: https://sso.example.com/realms/fasti
    audiences: [fasti-api]          # "Audience" mapper in Keycloak
    user-id-claim: preferred_username
```

Exemplo com Microsoft Entra ID:

```yaml
spring.security.oauth2.client:
  registration.entra:
    client-id: <application-id>
    client-secret: ${ENTRA_CLIENT_SECRET}
    client-name: Microsoft
    scope: openid, profile, email
  provider.entra:
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    user-name-attribute: preferred_username
camunda.bpm.oauth2:
  # app registration manifest: "groupMembershipClaims": "SecurityGroup"
  identity-provider.group-name-attribute: groups
  sso-logout.enabled: true
fasti.security.oidc:
  groups:
    admin: ["<object id of the administrators group>"]
    mapping:
      "<object id of the accounting group>": accounting
    include-unmapped: false          # Entra sends object ids, not group names
  rest:
    bearer-enabled: true
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    audiences: ["api://<application-id>"]   # v2 access tokens: accessTokenAcceptedVersion = 2
    user-id-claim: preferred_username
```

**Tomcat, WildFly (WAR):** os mesmos recursos, configurados em `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; um `fasti-oidc.properties.example` com anotações
fica ao lado dele). Sem o arquivo, nada muda. As chaves correspondem às propriedades `fasti.security.oidc.*` do Run, mais `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. O
provedor deve permitir a URI de redirecionamento `<base>/fasti/sso/callback`; a URL de logout por canal de retaguarda é
`<base>/fasti/sso/backchannel-logout`. Os tokens bearer em `/engine-rest` são ativados com
`fasti.security.oidc.rest.bearer-enabled=true` (a autenticação básica continua funcionando, a menos que
`rest.basic-enabled=false`; a lista de motores `/engine` também exige credenciais). Os usuários não precisam
existir no banco de dados. Para que o motor responda a consultas sobre os grupos e tenants do
usuário conectado a partir das declarações, ative o `OidcIdentityProviderPlugin` (comentado em
`bpm-platform.xml` / `standalone.xml`). Limitações: as sessões rastreadas para logout por canal de retaguarda são
mantidas na memória de cada servidor (não são compartilhadas entre nós do cluster); as aplicações clássicas apenas redirecionam para o provedor; um logout completo exige a nova interface web.

<a id="build"></a>

## Compilação

Exige JDK 17 ou posterior (21 recomendado).

```bash
./mvnw clean verify
```

Uma compilação completa executa todos os testes Java e os testes unitários da interface web (quase uma hora; os testes
de ponta a ponta com Playwright são executados separadamente, consulte [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) e exige acesso de rede ao Maven Central,
nodejs.org e ao registro npm. Consulte [TESTING.md](TESTING.md).

<a id="acknowledgements"></a>

## Agradecimentos

- O Fasti BPM Platform se apoia em mais de uma década de trabalho da Camunda e da comunidade
  Camunda 7.
- Durante a revisão de defeitos, o projeto consultou informações públicas (issues, avisos,
  notas de versão) de outros projetos da comunidade derivados do Camunda 7: Operaton, CIB
  seven, OrqueIO e EximeeBPMS. O código-fonte deste repositório foi escrito
  de forma independente sobre o Camunda 7 CE.

<a id="license"></a>

## Licença

[Licença Apache 2.0](LICENSE). Consulte [NOTICE](NOTICE) para os direitos autorais do código original.
