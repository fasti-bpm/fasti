[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | Русский | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> Этот перевод поддерживается сообществом и может отставать от английской версии; основной является английская версия. Исправления приветствуются.

# Встраивание Fasti BPM: совместимость и координаты Maven

Что Fasti сохраняет от Camunda 7 и чем отличается от 7.24.0, как нумеруются версии, а также координаты Maven для приложений, встраивающих движок. Чтобы запустить Fasti как сервер, см. [Установка с Docker](install-docker.ru.md) или дистрибутив Tomcat на <https://fasti.dev/download>.

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
[руководстве по миграции](migrate-from-camunda-7.ru.md#what-differs).

<a id="versioning"></a>

## Версионирование

Fasti BPM Platform следует [семантическому версионированию](https://semver.org/), начиная с `1.0.0`.
Ветка `1.x` сохраняет Java API, пакеты `org.camunda.bpm.*`, префикс конфигурации `camunda.bpm.*`
и схему базы данных Camunda 7.24. Приложения со встроенным движком переходят на Java 17 или новее
и, при использовании стартера Spring Boot, на Spring Boot 4.1; плагины и приложения процессов
пересобираются с артефактами `dev.fasti.platform`.

<a id="maven-coordinates"></a>

## Координаты Maven

Артефакты Fasti BPM Platform имеют собственные координаты Maven. **Пакеты Java
остаются `org.camunda.bpm.*`**, поэтому импорты в коде приложений сохраняются; префикс конфигурации `camunda.bpm.*`,
пространства имён BPMN/DMN, схема базы данных и имена модулей в
WildFly также не меняются. С точки зрения Maven меняются только объявления
зависимостей (путь веб-приложения меняется отдельно, см.
[Путь веб-приложения](web-ui.ru.md#web-application-path-fasti)):

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
