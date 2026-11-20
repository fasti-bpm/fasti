[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | Русский | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Этот перевод поддерживается сообществом и может отставать от английской версии; основной является английская версия. Исправления приветствуются.

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Версия 7.24.0 стала последним
выпуском Camunda 7 Community Edition. Fasti BPM продолжает её развитие: тот же движок и та же база данных,
дальнейшие исправления и обновления библиотек, а также новый веб-интерфейс.

> **Fasti BPM не аффилирован с Camunda Services GmbH; Camunda является товарным знаком Camunda Services GmbH.**
> Fasti BPM — независимый проект с открытым исходным кодом (Apache-2.0). Он не является частью Camunda Services GmbH,
> не спонсируется и не одобряется этой компанией. Название «Camunda» используется только для указания происхождения
> исходного кода и совместимости Fasti.

<a id="quick-start"></a>

## Быстрый старт

Образ доступен в Docker Hub (`fastidev/fasti-bpm`) и GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`) для amd64 и arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.1
```

Откройте <http://localhost:8080/fasti/> и войдите в систему. Без `DB_URL` образ использует базу данных H2
внутри контейнера, предназначенную только для ознакомления. Для REST API требуются учётные данные Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Соберите образ самостоятельно** из исходного кода. Для этого нужны Docker, JDK 17+ и сетевой
доступ к Maven Central, nodejs.org и реестру npm (при сборке загружаются Node/npm для
веб-интерфейса); первая сборка занимает примерно 10–20 минут:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.1
```

Образ **по умолчанию требует аутентификацию REST и проверяет права доступа** (в отличие от Camunda
Run); отключить их для демонстраций можно только с помощью `FASTI_INSECURE_DEMO=true`.

<a id="highlights"></a>

## Основные возможности

- **Новый веб-интерфейс** по адресу `/fasti/` (React):
  - поиск экземпляров процессов сразу по нескольким переменным с объединением условий через AND/OR;
  - хронология дела внутри задачи (комментарии и действия на каждом этапе);
  - кнопка «Получить следующую задачу»;
  - сроки выполнения и срочность (SLA), просроченные задачи;
  - загрузка команды по пользователям и группам;
  - инструкции по устранению проблем, прикреплённые к инцидентам;
  - страница состояния системы (инциденты, задания с ошибками, просроченные задания, внешние задачи, пакетные операции);
  - адаптация для мобильных устройств;
  - 13 языков.
- **Единый вход (OIDC)**: Keycloak, Microsoft Entra ID и другие провайдеры OpenID Connect;
  сопоставление утверждений токена с группами и арендаторами; токены JWT Bearer для REST API. Доступно для Run,
  Spring Boot, Tomcat и WildFly.
- **Усиление безопасности**: список разрешённых выражений в запросах и фильтрах, проверка вложений и
  комментариев внутри движка, первоначальная настройка, права доступа Cockpit, открытая переадресация, заголовки
  веб-приложений, файлы cookie и CSP. Подробности в [журнале изменений](CHANGELOG.md).
- **Аутентификация включена по умолчанию**: образ требует учётные данные для REST и проверяет
  права доступа во всех режимах.
- **Проверка версии схемы**: движок отказывается запускаться со схемой старше 7.24 и никогда
  не обновляет схему самостоятельно.
- **Защита от Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): образ amd64
  содержит исправленную Temurin JRE и отключает встроенную оптимизацию Base64; движок выдаёт предупреждение,
  если работает на уязвимой JVM.
- **Производительность**: объём первой загрузки нового веб-интерфейса сокращён с 960 КБ до 330 КБ (gzip включён
  по умолчанию); настройка увеличивает пропускную способность исполнителя заданий примерно в 2,5 раза (цифры и условия тестирования в
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Современная платформа**: Java 17 или новее, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## Установка

<a id="one-command-on-your-existing-camunda-724-database"></a>

### Одна команда для существующей базы данных Camunda 7.24

Образ поддерживает переменные `DB_*` прежнего образа Camunda 7 Run. Создайте резервную копию базы данных,
затем выполните:

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

Пользователи, пароли, группы, арендаторы, права доступа, фильтры, развёртывания, работающие экземпляры,
история, задания и инциденты остаются без изменений. Порт доступен только на этом компьютере; для удалённого
доступа используйте обратный прокси с HTTPS. Войдите по адресу `/fasti/` с существующей учётной записью.
Поскольку аутентификация включена, каждый REST-клиент (обработчики внешних задач, интеграции) должен передавать
учётные данные Basic или токен Bearer.

- **Совместная работа** с узлами Camunda 7.24 CE на одной базе данных: одинаковый уровень истории, одинаковые
  `TZ` и настройки паролей. Если старые узлы являются процессными приложениями с Java-классами, которых
  нет в Fasti, задайте для Fasti `FASTI_JOB_EXECUTOR_ENABLED=false` (или включите учёт развёртываний
  на каждом узле) и не запускайте эти процессы и не завершайте их задачи через Fasti:
  синхронный вызов выполняет делегаты на том узле, который его получает.
- **Откат**: Fasti не меняет схему, поэтому остановите Fasti и снова запустите узлы Camunda 7.24
  на той же базе данных. Развёрнутые процессы, использующие возможности, доступные только в Fasti, не будут работать в
  Camunda 7; см. [Откат к Camunda 7.24](docs/migrate-from-camunda-7.ru.md#rollback-to-camunda-724).
- **Более старые схемы (7.17–7.23)**: сначала выполните поставляемые в образе скрипты `sql/upgrade`
  (`/fasti/configuration/sql/upgrade`) для обновления до 7.24; например, для 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

С образом проверены (подключение к базе данных, созданной Camunda Run 7.24.0, откат
и совместная работа): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (драйвер Oracle не включён; добавьте `ojdbc11` самостоятельно; откат не проверялся
на MySQL). **DB2 не
проверена.** H2 предназначена только для ознакомления. Результаты отдельных тестов и ограничения:
[руководство по миграции](docs/migrate-from-camunda-7.ru.md#limits).

Подробные руководства:

- [Установка с Docker](docs/install-docker.ru.md): H2, PostgreSQL с `docker run` и
  `docker compose`, существующие базы данных, режим совместной работы, проверки работоспособности, обновление, откат,
  устранение неполадок.
- [Переход с Camunda 7](docs/migrate-from-camunda-7.ru.md): порядок действий, что осталось прежним и что
  отличается, откат, ограничения.
- [Справочник переменных окружения образа](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Без Docker: дистрибутив Tomcat (загрузка с fasti.dev)

Готовый дистрибутив Tomcat 1.0.0 (страница загрузки: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Перед распаковкой проверьте контрольную сумму, например командой `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

Дистрибутив Tomcat также можно собрать из исходного кода (`distro/tomcat`). Перед запуском прочитайте `README.txt`
в дистрибутиве: REST API (`/engine-rest`) по умолчанию требует аутентификацию HTTP Basic,
пользователи не создаются (создайте первого администратора на странице настройки `/fasti/`),
а при подключении к базе данных Camunda Run задайте `jobExecutorDeploymentAware=false` в
`conf/bpm-platform.xml`, иначе таймеры и асинхронные задания существующих развёртываний не будут выполняться.

<a id="a-ui-built-from-real-user-complaints"></a>

## Интерфейс, созданный на основе реальных жалоб пользователей

Возможности нового веб-интерфейса выбраны на основе публичных сообщений пользователей Camunda 7
на форуме Camunda и в GitHub, например:

| Что запрашивали пользователи | Источник | В Fasti |
|---|---|---|
| Поиск экземпляров по нескольким переменным с OR | форум [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Конструктор условий по нескольким переменным, AND/OR |
| Просмотр комментариев на каждом этапе дела | форум [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Хронология дела в задаче; комментарии к самому экземпляру процесса |
| Сортировка задач по переменным, начиная с самых старых | форум [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Сортировка по переменным, срокам выполнения и срочности |
| Делегирование, замещение на время отсутствия | форум [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Делегирование отдельных задач доступно; замещение на время отсутствия включено в [план развития](#roadmap-plan) |
| Бизнес-ключ и переменные при запуске процесса без формы | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Да |
| Загрузка развёрнутого BPMN XML | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Да |
| Единообразные подписи, несколько языков | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 языков |

<a id="comparison"></a>

## Сравнение

Краткое сравнение на основе публичной документации каждого проекта (изучена в ноябре 2026 года);
«не проверено» означает, что мы не нашли надёжного источника. У каждого проекта своё направление, и каждый
может подойти для определённых задач; перед выбором ознакомьтесь с документацией.

| | Лицензия | Работа с существующей БД C7 | Необходимость миграции | Исправления безопасности | Java / Spring Boot | Интерфейс | SSO | Образ Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Да, схема 7.24 без изменений | Образ: не требуется. Встраиваемые приложения: новые координаты Maven, Spring Boot 4.1 и Java 17+; пакеты `org.camunda.*` сохранены | Да, см. [политику ниже](#commitments-support-and-security-patches) | 17+ / 4.1 | Новый (React) + классический (устаревший) | Встроенный OIDC | Да, начиная с 1.0.0 |
| Camunda 7 CE 7.24 | Исходный код под Apache-2.0; некоторые включённые веб-компоненты под MIT ([13]) | (исходная платформа) | — | После 7.24 выпусков CE нет ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 через стартер безопасности | Прежний образ Run |
| Camunda 7 Enterprise | Коммерческая | Да (та же продуктовая линейка) | Нет | До 04/2030, платное продление до 04/2032 ([3]) | не проверено | Cockpit Enterprise | не проверено | не проверено |
| Camunda 8 | Camunda License 1.0; самостоятельная эксплуатация в промышленной среде требует лицензии начиная с 8.6 ([4]) | Нет (другой движок) | Да (модели, экземпляры, данные) | Да (от поставщика) | другой движок, сравнение неприменимо | Operate, Tasklist | не проверено | не проверено |
| Operaton | Apache-2.0 | Та же структура таблиц, что в 7.24; версия 2.2 записывает версию схемы 7.25 через скрипт обновления ([5]) | Пакеты переименованы в `org.operaton.*` (доступен рецепт OpenRewrite); скрипт версии схемы (2.2) | Да, ветка 2.2.x ([6]) | 17+ / 4.1 ([5]) | Классический + webapps-neo (предварительная версия) | не проверено | Да ([7]) |
| CIB seven | Apache-2.0; для EE нужен лицензионный ключ | Да (изменения 7.24 включены) | Пакеты переименованы в `org.cibseven.*`, префикс `cibseven.*` | Да, в каждом выпуске ([8]) | 17, 21 / 3.5 и 4.0 ([9]) | Веб-клиент на Vue 3 | OAuth2/JWT для REST ([8]) | Да ([8]) |
| OrqueIO | Apache-2.0 | Совместимость с таблицами C7 согласно документации проекта ([10]) | Группа Maven `io.orqueio`; переименование пакетов не указано | Да, согласно документации проекта ([10]) | 21 / 3.5, 4.0, 4.1 | Переписан на Angular | OIDC с несколькими провайдерами | не проверено |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | Проект заявляет о сохранении совместимости схемы с Camunda 7 (не проверено Fasti) ([14]) | Пакеты переименованы в `org.eximeebpms.*`, Model API переименован | Да, два последних минорных выпуска ([11]) | 21 / версия Spring Boot не указана | Отдельный Monitor | не проверено | Да (репозиторий `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | не проверено | не проверено | не проверено | 21 (образ Corretto) / не проверено | Control Center | не проверено | Да ([12]) |

Слабые стороны самого Fasti, без прикрас: молодой проект с небольшой командой, пока нет коммерческой
поддержки, DB2 не проверена.

Источники: [1] [Объявление о завершении жизненного цикла CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [Поддерживаемые среды 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Продление поддержки Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Лицензирование Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [Поддерживаемые среды CIB seven](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [Документация OrqueIO](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Лицензии Camunda 7](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
независимый обзор: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## Хронология

<a id="camunda-7-milestones"></a>

### Основные этапы Camunda 7

| Дата | Событие | Источник |
|---|---|---|
| 2024-03-06 | Camunda объявляет о завершении жизненного цикла Camunda 7 CE в октябре 2025 года; 7.24 станет последним выпуском CE | [Форум Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda продлевает поддержку Camunda 7 Enterprise до 2030-04-09 с платной расширенной поддержкой до апреля 2032 года | [Блог Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Создан тег `7.24.0`, выпуск опубликован в Maven Central: последний выпуск CE | [тег 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Запланированная Camunda дата выпуска 7.24; после этой даты новые выпуски CE, включая исправления безопасности, не выходят. Репозиторий CE впоследствии архивирован | [объявление](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [страница поддержки](https://docs.camunda.org/enterprise/announcement) |
| Апрель 2030 | Завершение поддержки Camunda 7 Enterprise (7.24 LTS: 2030-04-13) | [страница поддержки](https://docs.camunda.org/enterprise/announcement) |
| Апрель 2032 | Завершение платной расширенной поддержки Camunda 7 Enterprise | [страница поддержки](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti продолжает разработку непосредственно от тега `7.24.0`; все исправления и изменения отсчитываются от этой точки
(`git log 7.24.0..`). Этапы ниже перечислены в порядке выполненных работ, без дат:

1. **Основа**: Java 17, Spring Boot 4.1, обновления библиотек; координаты Maven
   `dev.fasti.platform`.
2. **Безопасность**: исправления публично раскрытых проблем ветки 7.x, список разрешённых выражений,
   проверка вложений и комментариев внутри движка, усиление защиты веб-приложений.
3. **Новый веб-интерфейс** по адресу `/fasti/`, постепенно заменяющий классические Cockpit, Tasklist и Admin.
4. **Единый вход** (OIDC) для Run, Spring Boot, Tomcat и WildFly.
5. **Подключение к существующим базам данных без изменений**: образ поддерживает прежние переменные `DB_*` и проверяет версию схемы; проверен на пяти СУБД.
6. **Проверка безопасности перед выпуском** (ревью кода, тестирование образа на проникновение, базовое
   сканирование ZAP; обнаруженные проблемы исправлены) и **защита от Java4Shell**.
7. **1.0.0**: первый выпуск (2026-11-20).

<a id="roadmap-plan"></a>

## План развития

> Это **план**, а не обязательство по срокам. Объём работ и порядок могут меняться.

### Ноябрь и декабрь 2026 года (в работе)

В первую очередь — всё, о чём пользователи уже просили.

**1.x: Проще в эксплуатации**

- Артефакты Maven в Maven Central.
- Helm chart и примеры манифестов Kubernetes.
- Метрики Prometheus и трассировка OpenTelemetry.
- Структурированные логи в формате JSON.
- Обновление MyBatis, которое заодно обновляет OGNL.
- Проверка DB2 и вычитка переводов носителями языка.

**2.0: То, о чём просят команды в регулируемых отраслях**

- Замещение на время отсутствия, чтобы работа не ждала сотрудника в отпуске
  ([форум 2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199)).
- Контроль по принципу четырёх глаз для согласований.
- Неизменяемый журнал аудита с подписью или хешем и отчёты по аудиту.
- CMMN в новом интерфейсе и вывод из эксплуатации приложений на AngularJS.

**Новые направления**

- Ветки с долгосрочной поддержкой.
- MCP-сервер для движка.
- Шаги с ИИ в процессах.

### Цели на 2027 год

- **I квартал 2027 года: Работа в большом масштабе**: оператор Kubernetes для поэтапных обновлений и
  масштабирования; потоковая передача истории в Kafka и OpenSearch, чтобы рабочая база данных
  оставалась небольшой; публикуемые тесты производительности, повторяемые для каждого выпуска.
- **II квартал 2027 года: Инструменты для разработчиков**: SDK для коннекторов с коннекторами для
  REST, Kafka, электронной почты и объектного хранилища; набор для тестирования процессов со
  сценарными тестами и отчётами о покрытии; конструктор форм с повторно используемыми компонентами.
- **III квартал 2027 года: Видеть, как работают процессы**: аналитика процессов (тепловые карты,
  узкие места и отчёты о длительности); целевые уровни обслуживания с оповещениями; ассистент,
  который составляет черновик BPMN по тексту и объясняет инциденты.
- **IV квартал 2027 года: Доверие корпоративного уровня**: многоарендное администрирование с квотами
  для каждого арендатора; независимый аудит безопасности с публикацией отчёта; воспроизводимые
  сборки с подписанными сведениями о происхождении для каждого выпуска.

<a id="commitments-support-and-security-patches"></a>

## Обязательства: поддержка и исправления безопасности

> Эта политика поддерживается командой проекта Fasti BPM и может обновляться; действующей является версия
> в этом репозитории.

- **Поддерживаемая ветка:** последний минорный выпуск ветки `1.x` получает исправления ошибок и безопасности
  (в виде патч-выпусков, например `1.0.1`).
- **Исправления безопасности:** уязвимости в Fasti и включённых в него библиотеках имеют приоритет перед
  другими работами; образ пересобирается при появлении исправлений в базовом образе или JRE. Проект отслеживает
  публичные уведомления о безопасности Camunda 7 и её форков и проверяет, затрагивают ли они Fasti.
- **Совместимость:** ветка `1.x` сохраняет схему базы данных 7.24 и пакеты `org.camunda.bpm.*`.
  Несовместимые изменения помечаются как **BREAKING** в [журнале изменений](CHANGELOG.md).
- **Уровень поддержки:** это проект сообщества; поддержка оказывается по мере возможностей, **без SLA**,
  коммерческой поддержки пока нет.
- **Сообщения об уязвимостях:** пожалуйста, **не** создавайте публичные обращения; см.
  [SECURITY.md](SECURITY.md#security-policy) (конфиденциальное сообщение через GitHub).
- **Исправленные дефекты:** разделы *Исправления* и *Безопасность* в [журнале изменений](CHANGELOG.md) перечисляют
  исправления по сравнению с Camunda 7 CE 7.24.0; уведомления о безопасности приведены в
  [SECURITY.md](SECURITY.md#advisories).

<a id="documentation"></a>

## Документация

| Документ | Содержание |
|---|---|
| [docs/install-docker.md](docs/install-docker.ru.md) | Пошаговая установка с Docker |
| [docs/api/README.md](docs/api/README.md) | Интеграция через REST API, клиенты внешних задач на Java и JavaScript |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.ru.md) | Переход с Camunda 7, откат, ограничения |
| [distro/docker/README.md](distro/docker/README.md) | Образ: переменные окружения, драйверы, эксплуатация, настройка |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | Новый веб-интерфейс |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell и Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Изменения, безопасность, участие в разработке |

<a id="what-this-is"></a>

## О проекте

Этот репозиторий содержит движок процессов BPMN 2.0 / DMN, REST API, веб-приложения
(Cockpit, Tasklist, Admin) и дистрибутивы (Run, Tomcat, WildFly). Он
основан на теге `7.24.0` Camunda 7 CE и продолжает развитие с исправлениями ошибок и обновлениями
библиотек.

Цель — **обратная совместимость** с приложениями, работающими на Camunda 7:

- сохранены пакеты `org.camunda.bpm.*`, пространство имён BPMN
  `http://camunda.org/schema/1.0/bpmn` и префикс конфигурации `camunda.bpm.*`;
- сохранена схема базы данных 7.24;
- сохранены все варианты развёртывания: Spring Boot, Run, Tomcat, WildFly; `javax` и `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Отличия от Camunda 7.24.0 CE

- **Безопасность:** исправления публично раскрытых проблем ветки 7.x (выражения в
  запросах, первоначальная настройка, доступ к вложениям и комментариям, права доступа Cockpit, открытая
  переадресация), а также дополнительные меры защиты: список разрешённых выражений в запросах и
  фильтрах, проверка вложений и комментариев внутри движка, усиленные фильтры безопасности,
  заголовки и файлы cookie в веб-приложениях.
- **Стабильность:** устранена утечка памяти при выполнении скриптов и выражений DMN на
  JavaScript (GraalJS), а также внесён ряд других исправлений движка.
- **Платформа:** Java 17 или новее, Spring Boot 4.1, GraalJS 25, Jackson 2.21 и многие другие
  библиотеки обновлены до выпусков с исправлениями безопасности.

Некоторые исправления меняют настройки по умолчанию в сторону более безопасного поведения; перед обновлением прочитайте раздел «Что отличается» в
[руководстве по миграции](docs/migrate-from-camunda-7.ru.md#what-differs).

<a id="versioning"></a>

## Версионирование

Fasti BPM Platform следует [семантическому версионированию](https://semver.org/), начиная с `1.0.0`.
Ветка `1.x` совместима с приложениями, написанными для Camunda 7.24.

<a id="maven-coordinates"></a>

## Координаты Maven

Артефакты Fasti BPM Platform имеют собственные координаты Maven. **Пакеты Java
остаются `org.camunda.bpm.*`**, поэтому импорты в коде приложений сохраняются; префикс конфигурации `camunda.bpm.*`,
пространства имён BPMN/DMN, схема базы данных и имена модулей в
WildFly также не меняются. С точки зрения Maven меняются только объявления
зависимостей (путь веб-приложения меняется отдельно, см.
[Путь веб-приложения](#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; группы `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` и `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** префикс `camunda-` заменяется на `fasti-`, остальная часть сохраняется.
- **version:** версия Fasti BPM Platform (см. [Версионирование](#versioning)) вместо
  `7.24.0`.

Ни один выпуск этих артефактов пока не опубликован в Maven Central. До публикации собирайте их
из исходного кода и размещайте в репозитории Maven, который использует ваш проект.

Управляйте версиями с помощью BOM:

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

Часто используемые артефакты:

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

Библиотека FEEL `org.camunda.feel:feel-engine` — внешняя библиотека, не входящая в
этот репозиторий; её координаты остаются прежними.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Сторонние библиотеки, которые по-прежнему подключают исходный движок

Поскольку пакеты не изменены, исходный движок (`org.camunda.bpm:camunda-engine`)
и `fasti-engine` содержат классы с одинаковыми именами. Maven считает их двумя независимыми
библиотеками и не исключает ни одну из них. Если сторонняя библиотека (например, расширение
сообщества) зависит от исходных артефактов, исключите их и объявите вместо них соответствующие
артефакты Fasti:

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

`mvn dependency:tree -Dincludes="org.camunda*"` выводит исходные артефакты, которые всё ещё
присутствуют (`org.camunda.feel:feel-engine` допустим, не исключайте его).

<a id="startup-check"></a>

### Проверка при запуске

При создании движок проверяет путь классов и **отказывается запускаться**, если обнаруживает
классы движка процессов более чем в одной библиотеке; ошибка `ENGINE-12021` перечисляет
расположение каждой из них. Стартер Spring Boot выполняет такую же проверку сразу при запуске
приложения. Библиотека, доступная только через родительский загрузчик классов (например, общий
движок сервера наряду с движком, встроенным в веб-приложение), не
учитывается. Если такая конфигурация намеренна, проверку можно отключить системным свойством:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Ограничение: если библиотека исходного движка расположена в пути классов перед `fasti-engine`,
движок создаётся классами исходной библиотеки, в которых этой проверки нет.
В таком случае останавливаются только приложения, использующие стартер Fasti Spring Boot; при
других вариантах интеграции проверьте зависимости, как описано выше.

<a id="web-application-path-fasti"></a>

## Путь веб-приложения: `/fasti`

Во всех дистрибутивах Cockpit, Tasklist, Admin и новый веб-интерфейс доступны по пути `/fasti` (ранее `/camunda`):
`http://localhost:8080/fasti/` открывает новый интерфейс
(`/fasti/app/next/default/`), классические приложения находятся по адресам `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` и `/fasti/app/admin/...`. REST API остаётся по адресу `/engine-rest`.

**Классические приложения объявлены устаревшими.** Классические Cockpit, Tasklist, Admin и Welcome
построены на AngularJS 1.8.2 и Bootstrap 3, жизненный цикл которых завершён. **Образ контейнера
по умолчанию не предоставляет к ним доступ** (несовместимое изменение): их страницы и старые закладки перенаправляют в соответствующий раздел нового интерфейса, а запросы их файлов возвращают `404`; `FASTI_CLASSIC_WEBAPPS=true` возвращает
доступ к ним (с предупреждением), см. [distro/docker/README.md](distro/docker/README.md). Дистрибутивы
Tomcat и WildFly также не предоставляют к ним доступ по умолчанию (вернуть его можно с помощью
`-Dfasti.webapp.classic.enabled=true` или `FASTI_CLASSIC_WEBAPPS=true`). Архив Run и стартер Spring
Boot продолжают предоставлять к ним доступ, как прежде, чтобы не нарушать работу существующих установок; отключите
их с помощью `fasti.webapp.classic.enabled: false`. Классические приложения будут удалены в одном из
последующих выпусков.

Старый путь продолжает работать: каждый запрос к `/camunda/...` перенаправляется на тот же путь
в `/fasti/...` с сохранением оставшейся части пути и строки запроса. GET и HEAD получают
`301`; все остальные методы (POST, PUT, DELETE, например старые клиенты, вызывающие `/camunda/api/...`)
получают `308`, чтобы клиенты повторяли запрос с тем же методом и телом. Перенаправления
содержат `Cache-Control: no-store`, поэтому возврат к `/camunda` вступает в силу немедленно.

Чтобы восстановить `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (в этом случае перенаправления нет).
  Чтобы отключить только перенаправление: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** удалите `webapps/camunda` (приложение перенаправления) и переименуйте
  `webapps/fasti` в `webapps/camunda`.
- **WildFly:** удалите `standalone/deployments/camunda.war` (и `camunda.war.dodeploy`) и
  задайте `<context-root>/camunda</context-root>` в `WEB-INF/jboss-web.xml` WAR-файла
  веб-приложения.

**Обратные прокси:** если прокси пересылает только `/camunda/` (или переписывает пути, задаёт пути
файлов cookie, CSP либо проверки работоспособности для `/camunda`), добавьте `/fasti/` или полностью перейдите на `/fasti/`.
Файл cookie `XSRF-TOKEN` (и `JSESSIONID` в Tomcat и WildFly) теперь имеет путь `/fasti`,
поэтому пользователям нужно войти ещё раз. Закладки и старые ссылки продолжают работать через перенаправление.

<a id="single-sign-on-openid-connect"></a>

## Единый вход (OpenID Connect)

Поддерживается в **Run** (`./start.sh --oauth2`, модуль `internal/oauth2`) и приложениях **Spring Boot**
с `fasti-bpm-spring-boot-starter-security`. Включается объявлением
регистрации клиента Spring Security (`spring.security.oauth2.client.*`). Для **Tomcat** и
**WildFly** см. конец этого раздела.

- **Вход:** страница входа нового веб-приложения показывает «Войти через &lt;provider&gt;»
  вместо формы пароля (подпись: `fasti.security.oidc.login-label`, по умолчанию используется
  `client-name`; при нескольких регистрациях выберите одну с помощью
  `fasti.security.oidc.registration-id`). Процесс авторизации с использованием кода (Authorization Code Flow) использует `state`, `nonce`
  и PKCE (S256, в том числе для конфиденциальных клиентов); при входе идентификатор сеанса заменяется. Пользователи
  возвращаются на исходную страницу нового веб-приложения (допускаются только пути внутри
  `/fasti/app/next/`, что исключает открытую переадресацию).
- **Выход:** `POST /logout` (только запросы с того же источника) завершает сеанс; при
  `camunda.bpm.oauth2.sso-logout.enabled=true` пользователь переходит на конечную точку завершения
  сеанса провайдера (выход по инициативе RP, `post-logout-redirect-uri`). Выход по обратному каналу
  Spring Security остаётся включённым. `GET /logout` больше не выполняет выход.
- **Истечение сеанса:** когда токен доступа невозможно обновить или время сеанса истекает,
  запросы к `/fasti/api/**` возвращают `401` (без перенаправления), а новое веб-приложение показывает страницу входа.
- **CSRF:** `/fasti/api` и `/fasti/app` сохраняют собственный CSRF-фильтр веб-приложений
  (`X-XSRF-TOKEN`); защита CSRF в Spring Security остаётся отключённой, как в исходном проекте.
- **Группы, администраторы, арендаторы:** утверждение с группами задаётся свойством
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (имя свойства из исходного проекта; вложенные
  утверждения, например `realm_access.roles`, поддерживаются). `fasti.security.oidc.groups.admin` перечисляет
  значения утверждений, предоставляющие `camunda-admin`; `groups.mapping` сопоставляет значения утверждений с идентификаторами
  групп движка; `groups.include-unmapped=false` отбрасывает несопоставленные значения. **Отличие от исходного проекта:**
  значение утверждения `camunda-admin` больше не предоставляет права администратора само по себе, а
  полномочия вроде `SCOPE_openid` или `OIDC_USER` больше не становятся группами.
  `fasti.security.oidc.tenants.claim` задаёт сопоставление арендаторов. Имя и электронная почта:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Провайдер идентификации:** по умолчанию движок читает пользователей, группы и арендаторов из
  токена текущего сеанса (только чтение). Задайте
  `camunda.bpm.oauth2.identity-provider.enabled=false`, чтобы использовать базу данных (в этом случае группы
  берутся из таблиц членства). Автоматическое создание учётных записей не реализовано.
  Пока пользователи берутся из токена (по умолчанию), страница первоначальной настройки (первый администратор)
  отключена, поскольку администраторы определяются утверждениями (`groups.admin`); ранее пользователи вне группы администраторов направлялись на страницу настройки.
- **REST API с токенами Bearer:** `fasti.security.oidc.rest.bearer-enabled=true` разрешает
  `Authorization: Bearer <JWT>` для `/engine-rest` в дополнение к аутентификации Basic.
  Обязательны `rest.issuer-uri` и `rest.audiences`; подписи проверяются по JWKS
  издателя только с асимметричными алгоритмами (`rest.jws-algorithms`, по умолчанию `RS256`),
  проверяются `iss`, `aud`, `exp`, `nbf`. Для Run также требуется `camunda.bpm.run.auth.enabled=true`.
  Файл cookie сеанса браузера никогда не используется для аутентификации в `/engine-rest`.

Пример Keycloak (`configuration/default.yml` в Run):

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

Пример Microsoft Entra ID:

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

**Tomcat, WildFly (WAR):** те же возможности настраиваются в `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; рядом находится `fasti-oidc.properties.example`
с пояснениями). Без этого файла ничего не меняется. Ключи соответствуют свойствам Run `fasti.security.oidc.*`, плюс `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`.
Провайдер должен разрешать URI перенаправления `<base>/fasti/sso/callback`; URL выхода по обратному каналу —
`<base>/fasti/sso/backchannel-logout`. Токены Bearer для `/engine-rest` включаются с помощью
`fasti.security.oidc.rest.bearer-enabled=true` (аутентификация Basic продолжает работать, если не задано
`rest.basic-enabled=false`; список движков `/engine` также требует учётных данных). Пользователи могут
отсутствовать в базе данных. Чтобы движок отвечал на запросы о группах и арендаторах
вошедшего пользователя на основе утверждений, включите `OidcIdentityProviderPlugin` (закомментирован в
`bpm-platform.xml` / `standalone.xml`). Ограничения: сеансы, отслеживаемые для выхода по обратному каналу,
хранятся в памяти каждого сервера (не разделяются между узлами кластера); классические приложения только перенаправляют к провайдеру; для полного выхода нужен новый веб-интерфейс.

<a id="build"></a>

## Сборка

Требуется JDK 17 или новее (рекомендуется 21).

```bash
./mvnw clean verify
```

Полная сборка запускает все тесты Java и модульные тесты веб-интерфейса (около часа; сквозные
тесты Playwright запускаются отдельно, см. [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) и требует сетевого доступа к Maven Central,
nodejs.org и реестру npm. См. [TESTING.md](TESTING.md).

<a id="acknowledgements"></a>

## Благодарности

- Fasti BPM Platform опирается на более чем десятилетнюю работу Camunda и сообщества
  Camunda 7.
- При изучении дефектов проект использовал публичную информацию (обращения, уведомления о безопасности,
  примечания к выпускам) других проектов сообщества, происходящих от Camunda 7: Operaton, CIB
  seven, OrqueIO и EximeeBPMS. Исходный код в этом репозитории написан
  независимо на основе Camunda 7 CE.

<a id="license"></a>

## Лицензия

[Лицензия Apache 2.0](LICENSE). Сведения об авторских правах на оригинальный код см. в [NOTICE](NOTICE).
