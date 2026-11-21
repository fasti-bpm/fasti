[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | Русский | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> Этот перевод поддерживается сообществом и может отставать от английской версии; основной является английская версия. Исправления приветствуются.

# Установка Fasti BPM с Docker

Пошаговое руководство: быстрое ознакомление с H2, промышленная эксплуатация с PostgreSQL (`docker run` или
`docker compose`), подключение к существующей базе данных Camunda 7, совместная работа узлов, проверки
работоспособности, обновление, откат и устранение неполадок. Полная справка по образу:
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM построен на Camunda 7 CE
7.24.0 и является независимым проектом, не связанным с Camunda Services GmbH.

<a id="1-requirements"></a>

## 1. Требования

- Docker Engine с Docker Compose v2 (`docker compose`).
- Для реальных рабочих нагрузок: не менее 2 процессорных ядер и 2 ГБ памяти для контейнера Fasti (при меньших ресурсах
  JVM выбирает Serial GC).
- База данных для промышленной эксплуатации: PostgreSQL, MariaDB, MySQL, SQL Server или Oracle (см.
  [матрицу протестированных конфигураций](migrate-from-camunda-7.ru.md#limits)). DB2 не проверена. H2 предназначена только для ознакомления.

<a id="2-pull-the-image"></a>

## 2. Загрузка образа

<!-- update with the Docker Hub image name at release -->
Образ опубликован для amd64 и arm64 в двух реестрах:

```bash
docker pull fastidev/fasti-bpm:1.1.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.1.0     # GitHub Container Registry
```

Теги: `1.1.0` (именно этот выпуск), `1.1` (последний корректирующий выпуск ветки 1.1) и `latest` (последний
опубликованный выпуск). Образ подписан с помощью cosign (без ключа, GitHub OIDC) и содержит SBOM и аттестацию
происхождения. Начиная с 1.0.1 подпись имеет формат Sigstore bundle из cosign 3; образ 1.0.0 подписан в более
старом формате cosign 2.

Также можно собрать образ из исходного кода (требуются JDK 17+ и сетевой доступ к Maven
Central, nodejs.org и реестру npm) и использовать имя `fasti-bpm:local` вместо
`fastidev/fasti-bpm:1.1.0` во всех приведённых ниже командах:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Быстрое ознакомление с H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.1.0
```

- Откройте <http://localhost:8080/fasti/> и войдите под именем `admin`.
- Без `DB_URL` образ использует H2 в `/fasti/data`; том `fasti-data` сохраняет данные
  при пересоздании контейнера. Только для ознакомления, никогда не используйте для реальных данных.
- Добавьте `--webapps --rest --example` в конец команды, чтобы также получить пример процесса (обработка счёта).
  Сам по себе `--example` оставляет веб-интерфейс и REST API выключенными.
- REST API доступен по адресу `http://localhost:8080/engine-rest` и требует учётных данных HTTP Basic; см.
  [руководство по REST API](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Промышленная эксплуатация с PostgreSQL: `docker run`

Создайте два файла с секретами (пароль базы данных и пароль администратора). При использовании `--production` движок
применяет политику паролей: не менее 10 символов, включая прописные и строчные буквы, цифру
и специальный символ.

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
  fastidev/fasti-bpm:1.1.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` только создаёт отсутствующие таблицы в новой базе данных; Fasti никогда
  не обновляет существующую схему. Этот параметр можно оставлять включённым при последующих запусках.
- `--production` включает cookie с атрибутом `Secure` и запрещает использование неявно выбранной базы H2 и `DEBUG=true`.
  Настройте завершение TLS на обратном прокси перед Fasti; браузеры отправляют cookie с атрибутом `Secure` только по HTTPS (или на
  `localhost`).
- `WAIT_FOR` откладывает запуск до тех пор, пока база данных не начнёт принимать подключения.
- Пароль администратора задаётся только в том случае, если администратор ещё не существует; впоследствии изменяйте его в Admin.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Промышленная эксплуатация с PostgreSQL: `docker compose`

Пример файла: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, секреты, проверки работоспособности, `--production`, ограничение памяти 2 ГБ).

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

Переменные, которые можно задать при запуске: `FASTI_IMAGE` (по умолчанию `fastidev/fasti-bpm:1.1.0`,
например `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (по умолчанию `8080`), `TZ` (по умолчанию `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Подключение к существующей базе данных Camunda 7

Fasti работает со схемой 7.24, не изменяя её. Перед запуском:

1. **Создайте резервную копию** базы данных.
2. Проверьте версию схемы: `select VERSION_ from ACT_GE_SCHEMA_LOG` — наибольшее значение должно быть
   `7.24.x`. Для схем 7.17–7.23 сначала выполните скрипты `sql/upgrade` (поставляются в образе по пути
   `/fasti/configuration/sql/upgrade`), см.
   [руководство по миграции](migrate-from-camunda-7.ru.md#older-versions-717723).
3. Проверьте права доступа, поскольку Fasti обеспечивает их соблюдение: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` и `select count(*) from ACT_RU_AUTHORIZATION`. Если в старой системе
   проверка прав была отключена, предоставьте обычным пользователям разрешения до переключения трафика.
4. Запишите `TZ`, уровень истории, префикс таблиц/схему и настройки паролей
   старых узлов.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.1.0 --production
```

- Порт доступен только на этой машине; для удалённого доступа используйте обратный прокси с HTTPS.
- **Не** задавайте `FASTI_DB_SCHEMA_UPDATE` или `FASTI_ADMIN_*`: таблицы и учётные записи уже существуют.
  Журнал запуска должен содержать `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- Уровень истории по умолчанию — `auto`: используется уровень, сохранённый в базе данных.
- Теперь каждый REST-клиент должен отправлять учётные данные Basic или токен Bearer.
- Драйверы PostgreSQL, MySQL, MariaDB, SQL Server и H2 включены в образ. Добавьте драйверы **Oracle** и **DB2** самостоятельно (`ojdbc11.jar`, `jcc.jar`) с помощью `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: драйвер 13.x по умолчанию шифрует соединение и проверяет сертификат;
  серверу нужен действительный сертификат, соответствующий имени хоста в URL. `trustServerCertificate=true`
  (отключает проверку сертификата) или `encrypt=false` (отключает шифрование) допустимы только для локального ознакомления. MySQL/MariaDB требуют
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Совместная работа с узлами Camunda 7

Fasti и Camunda 7.24 могут одновременно работать с одной базой данных (проверено: обе системы видят данные
друг друга). Условия:

- одинаковый уровень истории, одинаковые `TZ`, настройки паролей и префикс таблиц;
- если старые узлы являются приложениями процессов с классами Java (`JavaDelegate`, слушатели), которых нет в Fasti, задайте `-e FASTI_JOB_EXECUTOR_ENABLED=false` для Fasti или настройте **каждый** узел
  на учёт развёртываний (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` для Fasti). Это защищает только
  асинхронные задания: запуск экземпляров или завершение задач через Fasti по-прежнему выполняет делегаты
  на Fasti, поэтому направляйте такие запросы на узлы, где есть соответствующие классы Java;
- балансировщик нагрузки: привязка сеансов для веб-приложений; REST с учётными данными Basic в ней не нуждается.

Подробности: [Смешанная эксплуатация с узлами Camunda 7](migrate-from-camunda-7.ru.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Основные переменные окружения

| Переменная | Назначение |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | подключение к базе данных (имена переменных прежнего образа Camunda 7 Run; псевдонимы: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | ожидание доступности `host:port` перед запуском |
| `TZ` | часовой пояс JVM; должен совпадать на всех узлах |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: создать отсутствующие таблицы в новой базе данных |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | создать администратора, если он отсутствует |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | исполнитель заданий этого узла |
| `FASTI_EXTRA_LIB` | каталог дополнительных JAR-файлов (драйверы Oracle, DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | максимальный размер пула соединений (по умолчанию 20; в простое открыто 10, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | параметры JVM, доля памяти для кучи в процентах (75) |

Полная таблица: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Проверки работоспособности

| Конечная точка | Назначение |
|---|---|
| `/actuator/health/liveness` | проверка жизнеспособности Kubernetes |
| `/actuator/health/readiness` | проверка готовности, балансировщики нагрузки; включает подключение к базе данных и движок |
| `/actuator/health` | общее состояние |

Вход не требуется; открыты только конечные точки проверки работоспособности. `HEALTHCHECK` образа использует
проверку готовности, поэтому `docker ps` показывает `healthy`, когда Fasti готов к работе:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. Обновление и откат

**Обновление Fasti** (например, `1.0.1` → `1.1.0`):

```bash
docker pull fastidev/fasti-bpm:1.1.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.1.0 --production   # same options as before
```

С Docker Compose: измените `FASTI_IMAGE` (или строку `image:`), затем выполните
`docker compose -f docker-compose.postgres.yml up -d`. Сначала прочитайте [журнал изменений](../CHANGELOG.md),
особенно записи **BREAKING**. Ветка `1.x` сохраняет схему 7.24, поэтому обновления
в её пределах не затрагивают схему. `docker stop -t 60` позволяет выполняющимся запросам завершиться до
остановки контейнера.

**Откат:**

- к предыдущему выпуску Fasti: запустите снова со старым тегом;
- к Camunda 7.24: остановите Fasti и снова запустите узлы Camunda 7.24 с той же базой данных. Настройки нового интерфейса сохранятся (`fasti.prefs.*` в `ACT_ID_INFO`, Camunda 7 их игнорирует). Развёрнутые процессы, использующие возможности, доступные только в Fasti, не будут работать в Camunda 7. См.
  [Откат к Camunda 7.24](migrate-from-camunda-7.ru.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Устранение неполадок

Просмотрите журнал с помощью `docker logs fasti` (или `docker compose logs fasti`).

| Симптом | Причина и решение |
|---|---|
| REST API возвращает `401` | Fasti требует аутентификации: передайте `-u user:password` или токен Bearer |
| Пустые списки или `403` | у пользователя недостаточно прав: предоставьте необходимые разрешения на соответствующие ресурсы в Admin → Авторизации. Добавляйте в `camunda-admin` только настоящих администраторов |
| Остановка с сообщением `ENGINE-03111 The database schema has version …` | схема старше 7.24: создайте резервную копию, затем выполните скрипты `sql/upgrade` (раздел 6). Не обходите проверку с помощью `FASTI_SCHEMA_VERSION_CHECK=warn`: работа со старой схемой не поддерживается и может привести к сбоям во время эксплуатации |
| Остановка с сообщением `Insecure configuration refused` | один из параметров отключает аутентификацию REST или проверку прав; удалите его (только `FASTI_INSECURE_DEMO=true` открывает доступ к экземпляру, для демонстраций) |
| Остановка с сообщением `Password does not match policy` | `--production` применяет политику паролей к `FASTI_ADMIN_PASSWORD`: не менее 10 символов, включая прописные и строчные буквы, цифру и специальный символ |
| `password authentication failed` | пароль в файле секрета отличается от пароля базы данных. Файлы, созданные в Windows, могут оканчиваться на `\r\n`: пересоздайте их с помощью `tr -d '\r\n'`. PostgreSQL читает `POSTGRES_PASSWORD_FILE` только при первой инициализации тома |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | задайте `FASTI_ADMIN_PASSWORD` или `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | база данных не настроена; `--production` не переключается на демонстрационную базу H2: укажите в `DB_URL` базу данных для промышленной эксплуатации |
| `… cannot be read` для переменной `_FILE` | файл не смонтирован в контейнер или uid 1000 не может его прочитать (для файла нужны права `0644`; для его каталога можно оставить `0700`) |
| При входе через веб-интерфейс по HTTP сеанс не сохраняется | cookie с атрибутом `Secure` (`--production`) отправляется только по HTTPS (или на `localhost`): настройте завершение TLS на прокси |
| Не удаётся подключиться к SQL Server (ошибка сертификата) | установите для SQL Server действительный сертификат, соответствующий имени хоста в URL; `trustServerCertificate=true` допустим только для локального ознакомления |
| Контейнер постоянно перезапускается, а в журнале появляется `OutOfMemoryError` | JVM завершает работу при нехватке памяти: увеличьте ограничение памяти контейнера (2 ГБ) |

Проблема не решена? Создайте обращение в GitHub и приложите журнал (удалив пароли). Проблемы безопасности: см.
[SECURITY.md](../SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Без Docker

Готовая сборка Tomcat 1.1.0 (страница загрузки: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
- [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

Проверьте контрольную сумму перед распаковкой, например с помощью `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`.
