[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | Português (Brasil) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

# Fasti BPM Platform

O Fasti BPM é um motor independente e de código aberto, construído sobre o Camunda 7 Community
Edition 7.24.0. Ele roda no seu banco de dados Camunda 7 atual, com bibliotecas atualizadas,
correções de segurança e uma nova interface web. Site: <https://fasti.dev>

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
  fastidev/fasti-bpm:1.2.1
```

Abra <http://localhost:8080/fasti/> e entre. Sem `DB_URL`, a imagem usa um banco de dados H2
dentro do contêiner, apenas para avaliação. A API REST exige credenciais Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Guia completo, incluindo o seu próprio banco de dados: <https://fasti.dev/install>

<a id="highlights"></a>

## Destaques

- **Troque a conexão. Essa é a migração.** O Fasti usa o schema do Camunda 7.24 como ele é e
  mantém os pacotes `org.camunda.bpm.*` ([migração](https://fasti.dev/install#migrate)).
- **Um contêiner, um comando**, com as mesmas variáveis `DB_*` do Camunda Run ([instalação](https://fasti.dev/install)).
- **Feita para quem trabalha a fila**: uma nova interface web em 13 idiomas, com tema claro e
  escuro, utilizável no celular ([recursos](https://fasti.dev/#features)).
- **Modele e faça o deploy no navegador**: BPMN, DMN e formulários ([modelador](https://fasti.dev/#modeler)).
- **As ferramentas para operar já vêm incluídas**: histórico, reparo de instâncias, retry em
  massa, log de operações ([operação](https://fasti.dev/#operate)).
- **Fechado por padrão na imagem de contêiner**: login obrigatório desde a primeira inicialização,
  sem usuário padrão, configurações que desligam a autenticação REST ou as autorizações são
  recusadas, e o Java4Shell é mitigado. Tomcat e WildFly exigem credenciais para a API REST e não
  criam usuários (o primeiro administrador é criado na página de configuração inicial); o zip do
  Run e o starter Spring Boot mantêm os padrões do Camunda 7. Todas as distribuições têm as
  correções de segurança e emitem um aviso na inicialização em uma JVM exposta ao Java4Shell
  ([segurança](https://fasti.dev/security)).
- **Single sign-on**: OpenID Connect e bearer tokens JWT ([guia](docs/single-sign-on.pt-BR.md)).
- **Plataforma atual**: Java 17 ou posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([desempenho](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Obter o Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Imagem Docker** (`fastidev/fasti-bpm`, amd64 e arm64): <https://fasti.dev/install>; passo a
  passo em [docs/install-docker.pt-BR.md](docs/install-docker.pt-BR.md), todas as variáveis em
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Pacote Tomcat**, sem Docker: <https://fasti.dev/download>. Pacote Tomcat 1.2.0 pronto para uso:
  - [fasti-bpm-tomcat-1.2.0.zip](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.zip.sha256))
  - [fasti-bpm-tomcat-1.2.0.tar.gz](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.tar.gz.sha256))

  Verifique a soma de verificação antes de extrair, por exemplo, `sha256sum -c fasti-bpm-tomcat-1.2.0.zip.sha256`,
  e leia `README.txt` no pacote antes de iniciá-lo.
- **Embutido** no Spring Boot ou em outra aplicação Java: novas coordenadas Maven
  (`dev.fasti.platform:fasti-*`), os mesmos pacotes `org.camunda.bpm.*`; consulte
  [docs/embedding.pt-BR.md](docs/embedding.pt-BR.md).

<a id="moving-from-camunda-7"></a>

## Migrar do Camunda 7

Se você roda o Camunda 7.24, o Fasti roda nesse mesmo banco de dados sem alterar o schema. Você pode
substituir os nós antigos, rodar o Fasti ao lado deles e voltar para o Camunda 7.24 a qualquer
momento. A imagem do Fasti foi testada assumindo bancos de dados criados pelo Camunda Run 7.24 em
PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022 e Oracle Database 23ai; a volta para o
Camunda Run foi testada nos mesmos bancos de dados, exceto MySQL. O DB2 ainda não foi testado. Todo cliente REST, inclusive os workers de external tasks, agora precisa enviar credenciais
Basic ou um bearer token.

Aplicações Spring Boot embarcadas também passam para Spring Boot 4.1 e Java 17; schemas mais antigos
são atualizados antes com os scripts padrão. Plugins e process applications feitos para o Camunda 7
precisam ser recompilados com os artefatos do Fasti para rodar dentro do Fasti. Rodar lado a lado e
voltar atrás têm limites (a mesma configuração em todos os nós, qual nó executa os jobs, processos
que usam recursos exclusivos do Fasti): veja
[operação mista](docs/migrate-from-camunda-7.pt-BR.md#mixed-operation-with-camunda-7-nodes) e
[reversão](docs/migrate-from-camunda-7.pt-BR.md#rollback-to-camunda-724).

Etapas e limites: <https://fasti.dev/install#migrate> e o
[guia de migração](docs/migrate-from-camunda-7.pt-BR.md) (reversão, schemas mais antigos, resultados
dos testes).

<a id="comparison"></a><a id="how-it-compares"></a>

## Como ele se compara

Vários bons projetos levam o Camunda 7 adiante. O Fasti mantém os pacotes `org.camunda.bpm.*` e
o schema da 7.24, de modo que a mudança é uma troca de conexão, e não um projeto de migração, e
ele adiciona uma nova interface. Os outros projetos também são boas escolhas. A tabela completa,
com fontes: <https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Segurança e suporte

A versão minor mais recente da linha `1.x` recebe correções de bugs e patches de segurança, como
versões de patch do tipo `1.0.1`. Reporte problemas de segurança de forma privada, não em uma
issue pública: consulte [SECURITY.md](SECURITY.md#security-policy), que também contém a
[política de suporte](SECURITY.md#support-policy). Lista de correções, Java4Shell e a revisão
anterior ao lançamento: <https://fasti.dev/security>. Para ajuda com instalação, migração ou
produção, e para condições de suporte, escreva para <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Plano de evolução

Isto é um plano, não um compromisso com datas. Lançado na `1.2.0`, com foco em desempenho: um
endpoint de carga de trabalho, índices opcionais para o banco de dados, avisos de desempenho na
inicialização, ajustes na trilha de auditoria e uma interface web mais rápida. Lançado na `1.1.0`:
um Helm chart e manifestos Kubernetes, métricas Prometheus, tracing com OpenTelemetry, logs
estruturados em JSON, um substituto durante ausências, controle de quatro olhos para aprovações, uma
trilha de auditoria imutável, um servidor MCP para assistentes de IA e casos (CMMN) na nova
interface. A seguir na linha `1.x`: a publicação dos artefatos Maven no Maven Central, a verificação
com DB2, a revisão das traduções por falantes nativos e a retirada das aplicações AngularJS. A linha
do tempo, do fim do Camunda 7 CE às metas para 2027: <https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Documentação

Em [fasti.dev](https://fasti.dev): [Instalar com Docker](https://fasti.dev/install) ·
[Download para Tomcat](https://fasti.dev/download) · [Guia de integração com a API](https://fasti.dev/docs) ·
[Segurança e suporte](https://fasti.dev/security) · [Comparativo](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Documento | Conteúdo |
|---|---|
| [docs/install-docker.pt-BR.md](docs/install-docker.pt-BR.md) | Instalação com Docker, passo a passo |
| [docs/install-kubernetes.md](docs/install-kubernetes.md) | Instalação no Kubernetes: Helm chart e manifestos simples (a partir da 1.1.0) |
| [docs/migrate-from-camunda-7.pt-BR.md](docs/migrate-from-camunda-7.pt-BR.md) | Migração do Camunda 7, reversão, limitações |
| [docs/embedding.pt-BR.md](docs/embedding.pt-BR.md) | Compatibilidade, o que é diferente em relação à 7.24.0, versionamento, coordenadas Maven, verificação na inicialização |
| [docs/web-ui.pt-BR.md](docs/web-ui.pt-BR.md) | A interface web: recursos pedidos pelos usuários, o caminho `/fasti`, as aplicações clássicas |
| [docs/absence.md](docs/absence.md) | Substitutos durante ausências (a partir da 1.1.0) |
| [docs/four-eyes.md](docs/four-eyes.md) | Controle de quatro olhos para aprovações (a partir da 1.1.0) |
| [docs/audit-trail.md](docs/audit-trail.md) | Trilha de auditoria imutável (a partir da 1.1.0) |
| [docs/single-sign-on.pt-BR.md](docs/single-sign-on.pt-BR.md) | Single sign-on (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | Integração com a API REST, clientes de tarefas externas em Java e JavaScript |
| [distro/docker/README.md](distro/docker/README.md) | Imagem: variáveis de ambiente, drivers, operações, ajustes |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | A nova interface web |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell e Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | Alterações, segurança, política de suporte |

<a id="build"></a>

## Compilar a partir do código-fonte

Exige JDK 17 ou posterior (21 recomendado) e acesso de rede ao Maven Central, nodejs.org e ao
registro npm (a compilação baixa Node/npm para a interface web).

```bash
./mvnw clean verify
```

Uma compilação completa executa todos os testes Java e os testes unitários da interface web
(quase uma hora; os testes de ponta a ponta com Playwright são executados separadamente, consulte [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
Consulte [TESTING.md](TESTING.md). Para compilar a imagem do contêiner (a primeira compilação
leva cerca de 10–20 minutos):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Contribuir

Perguntas, relatos de bugs e pull requests são bem-vindos no GitHub; consulte
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) e o
[Código de Conduta](CODE_OF_CONDUCT.md). Revisões das traduções por falantes nativos são
especialmente bem-vindas.

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
