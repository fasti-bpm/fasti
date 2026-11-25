[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | Русский | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Этот перевод поддерживается сообществом и может отставать от английской версии; основной является английская версия. Исправления приветствуются.

# Fasti BPM Platform

Fasti BPM — независимый движок с открытым исходным кодом, построенный на Camunda 7 Community
Edition 7.24.0. Он работает на вашей существующей базе данных Camunda 7, с актуальными
библиотеками, исправлениями безопасности и новым веб-интерфейсом. Сайт: <https://fasti.dev>

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
  fastidev/fasti-bpm:1.3.0
```

Откройте <http://localhost:8080/fasti/> и войдите в систему. Без `DB_URL` образ использует базу данных H2
внутри контейнера, предназначенную только для ознакомления. Для REST API требуются учётные данные Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Полное руководство, включая подключение собственной базы данных: <https://fasti.dev/install>

<a id="highlights"></a>

## Основные возможности

- **Смените подключение. Это и есть миграция.** Схема Camunda 7.24 используется как есть; пакеты
  `org.camunda.bpm.*` сохраняются ([миграция](https://fasti.dev/install#migrate)).
- **Один контейнер, одна команда**, с теми же переменными `DB_*`, что и в Camunda Run ([установка](https://fasti.dev/install)).
- **Создан для тех, кто разбирает очередь задач**: новый веб-интерфейс на 13 языках, в светлой и
  тёмной теме, удобный на телефоне ([возможности](https://fasti.dev/#features)).
- **Моделирование и развёртывание в браузере**: BPMN, DMN и формы ([моделер](https://fasti.dev/#modeler)).
- **Инструменты для эксплуатации уже в комплекте**: история, исправление экземпляров, массовый
  повтор заданий, журнал операций ([эксплуатация](https://fasti.dev/#operate)).
- **Контейнерный образ закрыт по умолчанию**: вход обязателен с первого запуска, нет пользователя
  по умолчанию, настройки, отключающие аутентификацию REST или авторизацию, отклоняются, риск
  Java4Shell снижен. Tomcat и WildFly требуют учётные данные для REST API и не создают
  пользователей (первый администратор создаётся на странице первоначальной настройки); zip-архив
  Run и стартер Spring Boot сохраняют настройки Camunda 7 по умолчанию. Все дистрибутивы содержат
  исправления безопасности и при запуске предупреждают, если JVM уязвима для Java4Shell
  ([безопасность](https://fasti.dev/security)).
- **Единый вход**: OpenID Connect и JWT bearer-токены ([руководство](docs/single-sign-on.ru.md)).
- **Современная платформа**: Java 17 или новее, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([производительность](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Как получить Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Образ Docker** (`fastidev/fasti-bpm`, amd64 и arm64): <https://fasti.dev/install>; пошаговая
  инструкция — в [docs/install-docker.ru.md](docs/install-docker.ru.md), все переменные — в
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Дистрибутив Tomcat**, без Docker: <https://fasti.dev/download>. Готовый дистрибутив Tomcat 1.2.1:
  - [fasti-bpm-tomcat-1.2.1.zip](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.zip.sha256))
  - [fasti-bpm-tomcat-1.2.1.tar.gz](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.tar.gz.sha256))

  Перед распаковкой проверьте контрольную сумму, например командой `sha256sum -c fasti-bpm-tomcat-1.2.1.zip.sha256`,
  а перед запуском прочитайте `README.txt` в дистрибутиве.
- **Встраивание** в Spring Boot или другое Java-приложение: новые координаты Maven
  (`dev.fasti.platform:fasti-*`), те же пакеты `org.camunda.bpm.*`; см.
  [docs/embedding.ru.md](docs/embedding.ru.md).

<a id="moving-from-camunda-7"></a>

## Переход с Camunda 7

Если вы используете Camunda 7.24, Fasti работает на той же базе данных, не изменяя её схему. Вы
можете заменить старые узлы, запустить Fasti рядом с ними и в любой момент вернуться к Camunda 7.24.
Образ Fasti протестирован на переходе с баз данных, созданных Camunda Run 7.24, в PostgreSQL 16,
MariaDB 11.8, MySQL 8.4, SQL Server 2022 и Oracle Database 23ai; возврат к Camunda Run проверен на
тех же базах данных, кроме MySQL. DB2 пока не тестировалась. Каждый
REST-клиент, включая воркеры внешних задач, теперь должен отправлять учётные данные Basic или
bearer-токен.

Встроенные приложения Spring Boot также переходят на Spring Boot 4.1 и Java 17; более старые схемы
сначала обновляются стандартными скриптами. Плагины и process applications, собранные для Camunda 7,
необходимо пересобрать с артефактами Fasti, чтобы они работали внутри Fasti. У параллельной работы и
возврата есть ограничения (одинаковая конфигурация на всех узлах, какой узел выполняет задания,
процессы с функциями, которые есть только в Fasti): см.
[смешанная работа](docs/migrate-from-camunda-7.ru.md#mixed-operation-with-camunda-7-nodes) и
[откат](docs/migrate-from-camunda-7.ru.md#rollback-to-camunda-724).

Шаги и ограничения: <https://fasti.dev/install#migrate> и
[руководство по миграции](docs/migrate-from-camunda-7.ru.md) (откат, более старые схемы, результаты
тестов).

<a id="comparison"></a><a id="how-it-compares"></a>

## Сравнение с другими проектами

Несколько хороших проектов продолжают развитие Camunda 7. Fasti сохраняет пакеты
`org.camunda.bpm.*` и схему 7.24, поэтому переход — это смена подключения, а не проект миграции,
и добавляет новый интерфейс. Другие проекты — тоже достойный выбор. Полная таблица с
источниками: <https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Безопасность и поддержка

Последний минорный релиз линейки `1.x` получает исправления ошибок и патчи безопасности в виде
патч-релизов, например `1.0.1`. Пожалуйста, сообщайте о проблемах безопасности в частном порядке,
а не в публичном issue: см. [SECURITY.md](SECURITY.md#security-policy), где также описана
[политика поддержки](SECURITY.md#support-policy). Список исправлений, Java4Shell и проверка перед
выпуском: <https://fasti.dev/security>. За помощью с установкой, миграцией или эксплуатацией, а
также по условиям поддержки пишите на <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## План развития

Это план, а не обязательство по срокам. Выпущено в `1.2.0`, с упором на производительность: эндпоинт
для подсчёта нагрузки по задачам, необязательные индексы базы данных, проверка производительности
при запуске, настройка журнала аудита и более быстрый веб-интерфейс. Выпущено в `1.1.0`: Helm chart
и манифесты Kubernetes, метрики Prometheus, трассировка OpenTelemetry, структурированные логи в
формате JSON, замещение на время отсутствия, принцип «четырёх глаз» для согласований, неизменяемый
журнал аудита, MCP-сервер для ИИ-ассистентов и кейсы (CMMN) в новом интерфейсе. Далее в `1.x`:
публикация артефактов Maven в Maven Central, проверка на DB2, вычитка переводов носителями языка и
вывод из эксплуатации приложений на AngularJS. Хронология — от завершения Camunda 7 CE до целей на
2027 год: <https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Документация

На [fasti.dev](https://fasti.dev): [Установка с Docker](https://fasti.dev/install) ·
[Скачать для Tomcat](https://fasti.dev/download) · [Руководство по интеграции через API](https://fasti.dev/docs) ·
[Безопасность и поддержка](https://fasti.dev/security) · [Сравнение](https://fasti.dev/compare) ·
[Блог](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Документ | Содержание |
|---|---|
| [docs/install-docker.ru.md](docs/install-docker.ru.md) | Пошаговая установка с Docker |
| [docs/install-kubernetes.md](docs/install-kubernetes.md) | Установка в Kubernetes: Helm chart и простые манифесты (с 1.1.0) |
| [docs/migrate-from-camunda-7.ru.md](docs/migrate-from-camunda-7.ru.md) | Переход с Camunda 7, откат, ограничения |
| [docs/embedding.ru.md](docs/embedding.ru.md) | Совместимость, отличия от 7.24.0, версионирование, координаты Maven, проверка при запуске |
| [docs/web-ui.ru.md](docs/web-ui.ru.md) | Веб-интерфейс: функции, о которых просили пользователи, путь `/fasti`, классические приложения |
| [docs/absence.md](docs/absence.md) | Замещение на время отсутствия (с 1.1.0) |
| [docs/four-eyes.md](docs/four-eyes.md) | Принцип «четырёх глаз» для согласований (с 1.1.0) |
| [docs/audit-trail.md](docs/audit-trail.md) | Неизменяемый журнал аудита (с 1.1.0) |
| [docs/single-sign-on.ru.md](docs/single-sign-on.ru.md) | Единый вход (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | Интеграция через REST API, клиенты внешних задач на Java и JavaScript |
| [distro/docker/README.md](distro/docker/README.md) | Образ: переменные окружения, драйверы, эксплуатация, настройка |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | Новый веб-интерфейс |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell и Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | Изменения, безопасность, политика поддержки |

<a id="build"></a>

## Сборка из исходного кода

Требуется JDK 17 или новее (рекомендуется 21) и сетевой доступ к Maven Central, nodejs.org и
реестру npm (при сборке загружаются Node/npm для веб-интерфейса).

```bash
./mvnw clean verify
```

Полная сборка запускает все тесты Java и модульные тесты веб-интерфейса (около часа; сквозные
тесты Playwright запускаются отдельно, см. [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
См. [TESTING.md](TESTING.md). Чтобы собрать образ контейнера (первая сборка занимает примерно
10–20 минут):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Участие в проекте

Вопросы, сообщения об ошибках и pull requests приветствуются на GitHub; см.
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) и
[Кодекс поведения](CODE_OF_CONDUCT.md). Особенно приветствуются рецензии переводов от носителей
языка.

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
