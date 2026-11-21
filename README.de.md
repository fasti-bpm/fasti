[English](README.md) | [Tiếng Việt](README.vi.md) | Deutsch | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

# Fasti BPM Platform

Fasti BPM ist eine unabhängige Open-Source-Engine auf Basis von Camunda 7 Community Edition 7.24.0.
Sie läuft auf Ihrer bestehenden Camunda-7-Datenbank, mit aktuellen Bibliotheken,
Sicherheitskorrekturen und einer neuen Weboberfläche. Website: <https://fasti.dev>

> **Fasti BPM ist nicht mit Camunda Services GmbH verbunden; Camunda ist eine Marke der Camunda Services GmbH.**
> Fasti BPM ist ein unabhängiges Open-Source-Projekt (Apache-2.0). Es ist weder Teil von Camunda
> Services GmbH noch wird es von ihr gesponsert oder empfohlen. Der Name „Camunda“ wird nur verwendet,
> um die Herkunft des Quellcodes und die Kompatibilität von Fasti zu beschreiben.

<a id="quick-start"></a>

## Schnellstart

Das Image ist auf Docker Hub (`fastidev/fasti-bpm`) und in der GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`) für amd64 und arm64 verfügbar:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.1.0
```

Öffnen Sie <http://localhost:8080/fasti/> und melden Sie sich an. Ohne `DB_URL` verwendet das Image eine H2-Datenbank
innerhalb des Containers, ausschließlich zu Evaluierungszwecken. Die REST-API benötigt Basic-Zugangsdaten:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Vollständige Anleitung, auch mit Ihrer eigenen Datenbank: <https://fasti.dev/install>

<a id="highlights"></a>

## Highlights

- **Verbindung ändern. Das ist die Migration.** Das Camunda-7.24-Schema wird unverändert verwendet;
  die Pakete `org.camunda.bpm.*` bleiben erhalten ([Migration](https://fasti.dev/install#migrate)).
- **Ein Container, ein Befehl**, mit denselben `DB_*`-Variablen wie bei Camunda Run ([Installation](https://fasti.dev/install)).
- **Gebaut für die Menschen, die die Aufgaben abarbeiten**: eine neue Weboberfläche in 13 Sprachen,
  hell und dunkel, auf dem Smartphone nutzbar ([Funktionen](https://fasti.dev/#features)).
- **Im Browser modellieren und deployen**: BPMN, DMN und Formulare ([Modeler](https://fasti.dev/#modeler)).
- **Die Werkzeuge für den Betrieb sind schon dabei**: Historie, Reparatur von Instanzen, gesammeltes
  erneutes Ausführen von Jobs, Operationsprotokoll ([Betrieb](https://fasti.dev/#operate)).
- **Im Container-Image standardmäßig geschlossen**: Anmeldung ab dem ersten Start erforderlich,
  kein Standardbenutzer, Einstellungen, die die REST-Authentifizierung oder die Autorisierungen
  abschalten, werden abgelehnt, und Java4Shell ist entschärft. Tomcat und WildFly verlangen
  Zugangsdaten für die REST-API und legen keine Benutzer an (der erste Administrator wird auf der
  Einrichtungsseite angelegt); das Run-Zip und der Spring Boot Starter behalten die Standardwerte
  von Camunda 7. Jede Distribution enthält die Sicherheitskorrekturen und warnt beim Start vor einer
  JVM, die für Java4Shell anfällig ist ([Sicherheit](https://fasti.dev/security)).
- **Single Sign-on**: OpenID Connect und JWT-Bearer-Tokens ([Anleitung](docs/single-sign-on.de.md)).
- **Aktuelle Plattform**: Java 17 oder höher, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([Leistung](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Fasti beziehen

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker-Image** (`fastidev/fasti-bpm`, amd64 und arm64): <https://fasti.dev/install>;
  Schritt für Schritt in [docs/install-docker.de.md](docs/install-docker.de.md), alle Variablen in
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat-Bundle**, ohne Docker: <https://fasti.dev/download>. Fertiges Tomcat 1.1.0-Bundle:
  - [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
  - [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

  Prüfen Sie vor dem Entpacken die Prüfsumme, beispielsweise mit
  `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`, und lesen Sie vor dem Start die `README.txt`
  im Bundle.
- **Eingebettet** in Spring Boot oder eine andere Java-Anwendung: neue Maven-Koordinaten
  (`dev.fasti.platform:fasti-*`), dieselben Pakete `org.camunda.bpm.*`; siehe
  [docs/embedding.de.md](docs/embedding.de.md).

<a id="moving-from-camunda-7"></a>

## Umstieg von Camunda 7

Wenn Sie Camunda 7.24 betreiben, läuft Fasti auf derselben Datenbank, ohne deren Schema zu ändern.
Sie können die alten Knoten ersetzen, Fasti parallel dazu betreiben und jederzeit zu Camunda 7.24
zurückwechseln. Das Fasti-Image wurde bei der Übernahme von Datenbanken getestet, die Camunda Run
7.24 unter PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022 und Oracle Database 23ai erstellt
hatte; die Rückkehr zu Camunda Run wurde auf denselben Datenbanken außer MySQL getestet. DB2 ist
noch nicht getestet. Jeder REST-Client, External-Task-Worker eingeschlossen, muss nun
Basic-Zugangsdaten oder ein Bearer-Token senden.

Eingebettete Spring-Boot-Anwendungen wechseln außerdem auf Spring Boot 4.1 und Java 17; ältere
Schemas werden zuvor mit den Standardskripten aktualisiert. Plugins und Prozessanwendungen, die für
Camunda 7 gebaut wurden, müssen gegen die Fasti-Artefakte neu gebaut werden, um in Fasti zu laufen.
Parallelbetrieb und Rückkehr haben Grenzen (gleiche Konfiguration auf allen Knoten, welcher Knoten
Jobs ausführt, Prozesse mit Fasti-spezifischen Funktionen): siehe
[Mischbetrieb](docs/migrate-from-camunda-7.de.md#mixed-operation-with-camunda-7-nodes) und
[Rückkehr](docs/migrate-from-camunda-7.de.md#rollback-to-camunda-724).

Migrationsschritte und Grenzen: <https://fasti.dev/install#migrate> und der
[Migrationsleitfaden](docs/migrate-from-camunda-7.de.md) (Rollback, ältere Schemas, Testergebnisse).

<a id="comparison"></a><a id="how-it-compares"></a>

## Vergleich

Mehrere gute Projekte führen Camunda 7 weiter. Fasti behält die Pakete `org.camunda.bpm.*` und das
7.24-Schema bei, sodass der Umstieg ein Wechsel der Verbindung ist und kein Migrationsprojekt, und
bringt eine neue Oberfläche mit. Auch die anderen Projekte sind eine solide Wahl. Die vollständige
Tabelle mit Quellen: <https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Sicherheit und Support

Das neueste Minor-Release der Linie `1.x` erhält Bugfixes und Sicherheitspatches als Patch-Releases
wie `1.0.1`. Bitte melden Sie Sicherheitsprobleme vertraulich, nicht in einem öffentlichen Issue:
siehe [SECURITY.md](SECURITY.md#security-policy), wo auch die
[Support-Richtlinie](SECURITY.md#support-policy) steht. Liste der Korrekturen, Java4Shell und die
Prüfung vor der Veröffentlichung: <https://fasti.dev/security>. Für Hilfe bei Installation, Migration
oder Betrieb sowie für Support-Bedingungen schreiben Sie an <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Roadmap

Dies ist ein Plan, keine Terminzusage. Mit `1.1.0` veröffentlicht: ein Helm-Chart und
Kubernetes-Manifeste, Prometheus-Metriken, OpenTelemetry-Tracing, strukturierte JSON-Logs, eine
Vertretung bei Abwesenheit, das Vier-Augen-Prinzip für Freigaben, ein unveränderlicher
Audit-Trail, ein MCP-Server für KI-Assistenten sowie Fälle (CMMN) in der neuen Oberfläche. Als
Nächstes in `1.x`: die Veröffentlichung der Maven-Artefakte auf Maven Central, die Verifizierung
mit DB2, die Durchsicht der Übersetzungen durch Muttersprachler und die Ablösung der
AngularJS-Anwendungen. Die Zeitleiste, vom Ende von Camunda 7 CE bis zu den Zielen für 2027:
<https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Dokumentation

Auf [fasti.dev](https://fasti.dev): [Installation mit Docker](https://fasti.dev/install) ·
[Tomcat-Download](https://fasti.dev/download) · [Leitfaden zur API-Integration](https://fasti.dev/docs) ·
[Sicherheit und Support](https://fasti.dev/security) · [Vergleich](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Dokument | Inhalt |
|---|---|
| [docs/install-docker.de.md](docs/install-docker.de.md) | Installation mit Docker, Schritt für Schritt |
| [docs/install-kubernetes.md](docs/install-kubernetes.md) | Installation auf Kubernetes: Helm-Chart und einfache Manifeste (ab 1.1.0) |
| [docs/migrate-from-camunda-7.de.md](docs/migrate-from-camunda-7.de.md) | Umstieg von Camunda 7, Rollback, Einschränkungen |
| [docs/embedding.de.md](docs/embedding.de.md) | Kompatibilität, Unterschiede zu 7.24.0, Versionierung, Maven-Koordinaten, Prüfung beim Start |
| [docs/web-ui.de.md](docs/web-ui.de.md) | Die Weboberfläche: von Anwendern gewünschte Funktionen, der Pfad `/fasti`, die klassischen Anwendungen |
| [docs/absence.md](docs/absence.md) | Vertretungen bei Abwesenheit (ab 1.1.0) |
| [docs/four-eyes.md](docs/four-eyes.md) | Vier-Augen-Prinzip für Freigaben (ab 1.1.0) |
| [docs/audit-trail.md](docs/audit-trail.md) | Unveränderlicher Audit-Trail (ab 1.1.0) |
| [docs/single-sign-on.de.md](docs/single-sign-on.de.md) | Single Sign-on (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | REST-API-Integration, Java- und JavaScript-Clients für externe Aufgaben |
| [distro/docker/README.md](distro/docker/README.md) | Image: Umgebungsvariablen, Treiber, Betrieb, Optimierung |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | Die neue Weboberfläche |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell und Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | Änderungen, Sicherheit, Support-Richtlinie |

<a id="build"></a>

## Build aus dem Quellcode

Erfordert JDK 17 oder höher (21 empfohlen) und Netzwerkzugriff auf Maven Central, nodejs.org und die
npm-Registry (der Build lädt Node/npm für die Weboberfläche herunter).

```bash
./mvnw clean verify
```

Ein vollständiger Build führt alle Java-Tests und die Unit-Tests der Weboberfläche aus (knapp eine
Stunde; die Playwright-End-to-End-Tests werden separat ausgeführt, siehe [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
Siehe [TESTING.md](TESTING.md). So erstellen Sie das Container-Image (der erste Build dauert etwa
10–20 Minuten):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Mitwirken

Fragen, Fehlerberichte und Pull Requests sind auf GitHub willkommen; siehe
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) und den
[Verhaltenskodex](CODE_OF_CONDUCT.md). Besonders willkommen sind Durchsichten der Übersetzungen
durch Muttersprachler.

<a id="acknowledgements"></a>

## Danksagungen

- Fasti BPM Platform baut auf mehr als einem Jahrzehnt Arbeit von Camunda und der Camunda 7-
  Community auf.
- Bei der Untersuchung von Fehlern hat das Projekt öffentliche Informationen (Issues, Sicherheitshinweise,
  Versionshinweise) anderer Community-Projekte herangezogen, die von Camunda 7 abstammen: Operaton, CIB
  seven, OrqueIO und EximeeBPMS. Der Quellcode in diesem Repository wurde
  unabhängig auf Basis von Camunda 7 CE geschrieben.

<a id="license"></a>

## Lizenz

[Apache License 2.0](LICENSE). Angaben zum Urheberrecht des ursprünglichen Codes finden Sie in [NOTICE](NOTICE).
