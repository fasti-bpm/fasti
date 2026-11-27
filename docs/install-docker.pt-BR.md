[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | Português (Brasil) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

# Instalação do Fasti BPM com Docker

Um guia passo a passo: teste rápido com H2, produção com PostgreSQL (`docker run` ou
`docker compose`), conexão com um banco de dados Camunda 7 existente, operação lado a lado, verificações de
integridade, atualização, reversão e solução de problemas. Referência completa da imagem:
[distro/docker/README.md](../distro/docker/README.md). O Fasti BPM é baseado no Camunda 7 CE
7.24.0 e é um projeto independente, sem afiliação com a Camunda Services GmbH.

<a id="1-requirements"></a>

## 1. Requisitos

- Docker Engine com Docker Compose v2 (`docker compose`).
- Para cargas de trabalho reais: pelo menos 2 CPUs e 2 GB de memória para o contêiner Fasti (abaixo disso, a
  JVM seleciona o Serial GC).
- Banco de dados de produção: PostgreSQL, MariaDB, MySQL, SQL Server ou Oracle (consulte a
  [matriz de testes](migrate-from-camunda-7.pt-BR.md#limits)). O DB2 não foi verificado. O H2 é apenas para avaliação.

<a id="2-pull-the-image"></a>

## 2. Baixe a imagem

<!-- update with the Docker Hub image name at release -->
A imagem é publicada para amd64 e arm64 em dois locais:

```bash
docker pull fastidev/fasti-bpm:1.3.2              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.2     # GitHub Container Registry
```

Tags: `1.3.2` (exatamente essa versão), `1.3` (versão de correção mais recente da série 1.3) e `latest` (a versão
publicada mais recentemente). A imagem é assinada com cosign (sem chave, GitHub OIDC) e inclui uma SBOM e uma
atestação de proveniência. A partir da 1.0.1, a assinatura está no formato Sigstore bundle do cosign 3; a imagem
1.0.0 tem uma assinatura no formato mais antigo do cosign 2.

Você também pode compilar a imagem a partir do código-fonte (requer JDK 17+ e acesso pela rede ao Maven
Central, nodejs.org e ao registro npm) e usar o nome `fasti-bpm:local` no lugar de
`fastidev/fasti-bpm:1.3.2` em todos os comandos abaixo:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Teste rápido com H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.2
```

- Abra <http://localhost:8080/fasti/> e entre como `admin`.
- Sem `DB_URL`, a imagem usa H2 em `/fasti/data`; o volume `fasti-data` mantém os dados
  quando o contêiner é recriado. Apenas para avaliação, nunca para dados reais.
- Acrescente `--webapps --rest --example` ao comando para obter também um processo de exemplo
  (fatura). Usar apenas `--example` mantém a interface web e a API REST desativadas.
- A API REST está em `http://localhost:8080/engine-rest` e exige credenciais HTTP Basic; consulte o
  [guia da API REST](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Produção com PostgreSQL: `docker run`

Crie dois arquivos de segredos (senha do banco de dados e senha do administrador). Com `--production`, o mecanismo
aplica sua política de senhas: pelo menos 10 caracteres com letras maiúsculas e minúsculas, um dígito
e um caractere especial.

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # other users on the host cannot enter the directory
chmod 0644 secrets/*.txt     # the containers read them as uid 999 (PostgreSQL) and 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.3.2 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` apenas cria as tabelas ausentes em um banco de dados novo; o Fasti nunca
  atualiza um esquema existente. Essa configuração pode permanecer definida nas inicializações posteriores.
- `--production` ativa cookies `Secure` e recusa um banco de dados H2 implícito e `DEBUG=true`.
  Termine o TLS em um proxy reverso à frente do Fasti; os navegadores enviam cookies `Secure` apenas por HTTPS (ou para
  `localhost`).
- `WAIT_FOR` adia a inicialização até que o banco de dados aceite conexões.
- A senha do administrador só é definida quando ele ainda não existe; altere-a posteriormente em Administração.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Produção com PostgreSQL: `docker compose`

Arquivo de exemplo: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, segredos, verificações de integridade, `--production`, limite de memória de 2 GB).

```bash
cd distro/docker/examples          # or copy the file to a directory of its own
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # both services "healthy"
```

Variáveis que você pode definir na inicialização: `FASTI_IMAGE` (padrão `fastidev/fasti-bpm:1.3.2`, por
exemplo `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (padrão `8080`), `TZ` (padrão `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Conexão com um banco de dados Camunda 7 existente

O Fasti funciona com o esquema 7.24 sem alterá-lo. Antes de começar:

1. **Faça um backup** do banco de dados.
2. Verifique a versão do esquema: `select VERSION_ from ACT_GE_SCHEMA_LOG` — o maior valor deve ser
   `7.24.x`. Esquemas 7.17–7.23: execute primeiro os scripts de `sql/upgrade` (incluídos na imagem em
   `/fasti/configuration/sql/upgrade`), consulte o
   [guia de migração](migrate-from-camunda-7.pt-BR.md#older-versions-717723).
3. Verifique as autorizações, pois o Fasti as aplica: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` e `select count(*) from ACT_RU_AUTHORIZATION`. Se o sistema antigo
   operava com as autorizações desativadas, conceda permissões aos usuários comuns antes de redirecionar o tráfego.
4. Anote `TZ`, o nível de histórico, o prefixo das tabelas/esquema e as configurações de senha dos
   nós antigos.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.2 --production
```

- A porta fica vinculada apenas a esta máquina; para acesso remoto, use um proxy reverso com HTTPS.
- **Não** defina `FASTI_DB_SCHEMA_UPDATE` nem `FASTI_ADMIN_*`: as tabelas e as contas já existem. O
  log de inicialização deve conter `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- O nível de histórico padrão é `auto`: utiliza-se o nível armazenado no banco de dados.
- Todos os clientes REST devem agora enviar credenciais Basic ou um token bearer.
- Drivers: PostgreSQL, MySQL, MariaDB, SQL Server e H2 estão incluídos. Adicione os drivers **Oracle** e **DB2** por conta própria (`ojdbc11.jar`, `jcc.jar`) com `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: o driver 13.x criptografa a conexão e verifica o certificado por padrão; o
  servidor precisa de um certificado válido que corresponda ao nome do host na URL. `trustServerCertificate=true`
  (ignora as verificações de certificado) ou `encrypt=false` (sem criptografia) são apenas para testes locais. MySQL/MariaDB precisam de
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Operação lado a lado com nós Camunda 7

Fasti e Camunda 7.24 podem funcionar simultaneamente no mesmo banco de dados (testado: ambos veem os dados um
do outro). Condições:

- mesmo nível de histórico, mesmo `TZ`, mesmas configurações de senha, mesmo prefixo de tabelas;
- se os nós antigos forem aplicações de processos com classes Java (`JavaDelegate`, listeners) que o Fasti não possui, defina `-e FASTI_JOB_EXECUTOR_ENABLED=false` no Fasti ou configure **todos** os nós
  para considerar as implantações disponíveis (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` para o Fasti). Isso protege apenas
  os jobs assíncronos: iniciar instâncias ou concluir tarefas pelo Fasti ainda executa os delegates
  no Fasti, portanto envie essas solicitações aos nós que possuem as classes Java;
- balanceador de carga: afinidade de sessão para as aplicações web; REST com credenciais Basic não precisa disso.

Detalhes: [Operação mista com nós Camunda 7](migrate-from-camunda-7.pt-BR.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Variáveis de ambiente comuns

| Variável | Significado |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | conexão com o banco de dados (nomes de variáveis da antiga imagem Camunda 7 Run; aliases: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | aguarda `host:port` antes de iniciar |
| `TZ` | fuso horário da JVM; deve ser o mesmo em todos os nós |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: cria as tabelas ausentes em um banco de dados novo |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | cria o administrador caso ele não exista |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | executor de jobs deste nó |
| `FASTI_EXTRA_LIB` | diretório de jars adicionais (drivers Oracle, DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | tamanho máximo do pool de conexões (padrão 20; ocioso, mantém 10 abertas, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | opções da JVM, porcentagem de memória para o heap (75; < 1.5 GiB: 60) |

Tabela completa: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Verificações de integridade

| Endpoint | Uso |
|---|---|
| `/actuator/health/liveness` | sondagem de atividade do Kubernetes |
| `/actuator/health/readiness` | sondagem de prontidão, balanceadores de carga; inclui a conexão com o banco de dados e o mecanismo |
| `/actuator/health` | geral |

Não é necessário fazer login; apenas os endpoints de integridade são expostos. O `HEALTHCHECK` da imagem usa a
prontidão, portanto `docker ps` exibe `healthy` assim que o Fasti está pronto:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. Atualização e reversão

**Atualização do Fasti** (por exemplo `1.1.0` → `1.3.2`):

```bash
docker pull fastidev/fasti-bpm:1.3.2
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.2 --production   # same options as before
```

Com Docker Compose: altere `FASTI_IMAGE` (ou a linha `image:`), depois execute
`docker compose -f docker-compose.postgres.yml up -d`. Leia o [histórico de alterações](../CHANGELOG.md)
primeiro, especialmente as entradas de **ALTERAÇÕES INCOMPATÍVEIS**. A série `1.x` mantém o esquema 7.24, portanto as atualizações
dentro dela não alteram o esquema. `docker stop -t 60` permite que as solicitações em andamento terminem antes que o
contêiner seja interrompido.

**Reversão:**

- para a versão anterior do Fasti: inicie novamente com a tag antiga;
- para Camunda 7.24: pare o Fasti e inicie novamente os nós Camunda 7.24 no mesmo banco de dados. As preferências da nova interface permanecem (`fasti.prefs.*` em `ACT_ID_INFO`, ignoradas pelo Camunda 7). Os processos implantados que usam recursos exclusivos do Fasti não serão executados no Camunda 7. Consulte
  [Reversão para Camunda 7.24](migrate-from-camunda-7.pt-BR.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Solução de problemas

Leia o log com `docker logs fasti` (ou `docker compose logs fasti`).

| Sintoma | Causa e solução |
|---|---|
| A API REST retorna `401` | O Fasti exige autenticação: envie `-u user:password` ou um token bearer |
| Listas vazias ou `403` | O usuário não possui autorizações: conceda as permissões necessárias para os recursos envolvidos em Administração → Autorizações. Adicione apenas administradores de fato a `camunda-admin` |
| Para com `ENGINE-03111 The database schema has version …` | Esquema anterior a 7.24: faça um backup e execute os scripts de `sql/upgrade` (seção 6). Não contorne a verificação com `FASTI_SCHEMA_VERSION_CHECK=warn`: a execução com um esquema mais antigo não é suportada e pode falhar durante a operação |
| Para com `Insecure configuration refused` | Uma configuração desativa a autenticação REST ou as autorizações; remova-a (apenas `FASTI_INSECURE_DEMO=true` libera o acesso à instância, para demonstrações) |
| Para com `Password does not match policy` | `--production` aplica a política de senhas a `FASTI_ADMIN_PASSWORD`: pelo menos 10 caracteres com letras maiúsculas e minúsculas, um dígito e um caractere especial |
| `password authentication failed` | A senha no arquivo de segredo difere da senha do banco de dados. Arquivos criados no Windows podem terminar em `\r\n`: recrie-os com `tr -d '\r\n'`. O PostgreSQL lê `POSTGRES_PASSWORD_FILE` apenas quando o volume é inicializado pela primeira vez |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | Defina `FASTI_ADMIN_PASSWORD` ou `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | Nenhum banco de dados está configurado; `--production` não usa o banco de dados H2 de demonstração como alternativa: defina `DB_URL` para um banco de dados de produção |
| `… cannot be read` para uma variável `_FILE` | O arquivo não está montado no contêiner ou o uid 1000 não consegue lê-lo (o arquivo precisa do modo `0644`; seu diretório pode permanecer com `0700`) |
| O login web por HTTP não mantém a sessão | O cookie `Secure` (`--production`) é enviado apenas por HTTPS (ou para `localhost`): termine o TLS no proxy |
| Não é possível conectar ao SQL Server (erro de certificado) | Forneça ao SQL Server um certificado válido que corresponda ao nome do host na URL; `trustServerCertificate=true` é apenas para testes locais |
| O contêiner reinicia continuamente e o log mostra `OutOfMemoryError` | A JVM encerra quando a memória se esgota: aumente o limite de memória do contêiner (2 GB) |

Ainda não conseguiu resolver? Abra uma issue no GitHub com o log (sem as senhas). Problemas de segurança: consulte
[SECURITY.md](../.github/SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Sem Docker

Pacote Tomcat 1.3.1 pronto para uso (página de download: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.3.1.zip](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip.sha256))
- [fasti-bpm-tomcat-1.3.1.tar.gz](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz.sha256))

Verifique a soma de verificação antes de extrair, por exemplo `sha256sum -c fasti-bpm-tomcat-1.3.1.zip.sha256`.
