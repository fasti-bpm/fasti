[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | Deutsch | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

# Fasti BPM einbetten: Kompatibilität und Maven-Koordinaten

Was Fasti von Camunda 7 beibehält und was sich gegenüber 7.24.0 unterscheidet, wie Versionen nummeriert werden, und die Maven-Koordinaten für Anwendungen, die die Engine einbetten. Um Fasti als Server zu betreiben, siehe [Installation mit Docker](install-docker.de.md) oder das Tomcat-Bundle auf <https://fasti.dev/download>.

<a id="what-this-is"></a>

## Was dieses Projekt umfasst

Dieses Repository enthält die BPMN 2.0 / DMN-Prozess-Engine, die REST-API, die Web-
anwendungen (Cockpit, Tasklist, Admin) und die Distributionen (Run, Tomcat, WildFly). Es
baut auf dem Tag `7.24.0` von Camunda 7 CE auf und führt die Entwicklung mit Fehlerbehebungen und Bibliotheks-
aktualisierungen fort.

Ziel ist die **Abwärtskompatibilität** mit Anwendungen, die auf Camunda 7 laufen:

- Die `org.camunda.bpm.*`-Packages, der BPMN-Namensraum
  `http://camunda.org/schema/1.0/bpmn` und das Konfigurationspräfix `camunda.bpm.*` bleiben erhalten;
- das Datenbankschema von 7.24 bleibt erhalten;
- alle Deployment-Varianten bleiben erhalten: Spring Boot, Run, Tomcat, WildFly; `javax` und `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Unterschiede zu Camunda 7.24.0 CE

- **Sicherheit:** Behebungen öffentlich bekannt gewordener Probleme der 7.x-Linie (Ausdrücke in
  Abfragen, Ersteinrichtung, Zugriff auf Anhänge und Kommentare, Cockpit-Berechtigungen, offene
  Weiterleitungen) sowie zusätzliche Schutzmaßnahmen: eine Positivliste für Ausdrücke in Abfragen und
  Filtern, Prüfungen von Anhängen und Kommentaren innerhalb der Engine, gehärtete Sicherheitsfilter,
  Header und Cookies in den Webanwendungen.
- **Stabilität:** Kein Speicherleck mehr bei der Auswertung von Skripten und DMN-Ausdrücken mit
  JavaScript (GraalJS) sowie eine Reihe weiterer Fehlerbehebungen in der Engine.
- **Plattform:** Java 17 oder höher, Spring Boot 4.1, GraalJS 25, Jackson 2.21 und viele weitere
  Bibliotheken auf Versionen mit Sicherheitskorrekturen aktualisiert.

Einige Korrekturen ändern die Standardwerte zugunsten eines sichereren Verhaltens; lesen Sie vor dem Upgrade den Abschnitt „Unterschiede“ im
[Migrationsleitfaden](migrate-from-camunda-7.de.md#what-differs).

<a id="versioning"></a>

## Versionierung

Fasti BPM Platform folgt der [semantischen Versionierung](https://semver.org/), beginnend mit `1.0.0`.
Die `1.x`-Linie ist mit Anwendungen kompatibel, die für Camunda 7.24 geschrieben wurden.

<a id="maven-coordinates"></a>

## Maven-Koordinaten

Die Artefakte von Fasti BPM Platform haben eigene Maven-Koordinaten. **Die Java-Packages
bleiben `org.camunda.bpm.*`**, sodass der Anwendungscode seine Imports behält; das Konfigurationspräfix `camunda.bpm.*`,
die BPMN/DMN-Namensräume, das Datenbankschema und die Modulnamen auf
WildFly bleiben ebenfalls unverändert. Aus Maven-Sicht ändern sich nur die Abhängigkeitsdeklarationen
(der Pfad der Webanwendung ändert sich unabhängig davon, siehe
[Pfad der Webanwendung](web-ui.de.md#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; die Gruppen `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` und `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** Das Präfix `camunda-` wird zu `fasti-`, der Rest bleibt erhalten.
- **version:** die Version von Fasti BPM Platform (siehe [Versionierung](#versioning)) anstelle von
  `7.24.0`.

Bisher wurde noch kein Release dieser Artefakte auf Maven Central veröffentlicht. Erstellen Sie sie bis dahin
aus dem Quellcode und stellen Sie die Artefakte im Maven-Repository bereit, das Ihr Projekt verwendet.

Verwalten Sie die Versionen mit der BOM:

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

Häufig verwendete Artefakte:

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

Die FEEL-Bibliothek `org.camunda.feel:feel-engine` ist eine externe Bibliothek, die nicht Teil
dieses Repositorys ist; ihre Koordinaten bleiben unverändert.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Drittanbieterbibliotheken, die weiterhin die ursprüngliche Engine einbinden

Da die Packages unverändert sind, enthalten die ursprüngliche Engine (`org.camunda.bpm:camunda-engine`)
und `fasti-engine` Klassen mit denselben Namen. Maven behandelt sie als zwei voneinander unabhängige
Bibliotheken und entfernt keine von beiden. Wenn eine Drittanbieterbibliothek (beispielsweise eine Community-Erweiterung)
von den ursprünglichen Artefakten abhängt, schließen Sie diese aus und deklarieren Sie stattdessen die entsprechenden
Fasti-Artefakte:

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

`mvn dependency:tree -Dincludes="org.camunda*"` listet ursprüngliche Artefakte auf, die noch
vorhanden sind (`org.camunda.feel:feel-engine` ist zulässig, schließen Sie es nicht aus).

<a id="startup-check"></a>

### Prüfung beim Start

Bei ihrer Erstellung untersucht die Engine den Klassenpfad und **verweigert den Start**, wenn sie
die Klassen der Prozess-Engine in mehr als einer Bibliothek findet; der Fehler `ENGINE-12021` nennt
den Speicherort jeder dieser Bibliotheken. Der Spring Boot-Starter führt dieselbe Prüfung durch, sobald die
Anwendung startet. Eine Bibliothek, die nur über den übergeordneten Classloader sichtbar ist (beispielsweise eine gemeinsam
genutzte Engine des Servers neben einer in eine Webanwendung eingebetteten Engine), wird nicht
mitgezählt. Wenn Ihre Konfiguration beabsichtigt ist, lässt sich die Prüfung über eine Systemeigenschaft deaktivieren:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Einschränkung: Wenn die ursprüngliche Engine-Bibliothek im Klassenpfad vor `fasti-engine` steht, wird die
Engine mit den Klassen der ursprünglichen Bibliothek erstellt, die diese Prüfung nicht enthalten.
In diesem Fall werden nur Anwendungen gestoppt, die den Fasti Spring Boot-Starter verwenden; prüfen Sie bei
anderen Integrationen die Abhängigkeiten wie oben beschrieben.
