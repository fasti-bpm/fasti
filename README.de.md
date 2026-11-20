[English](README.md) | [Tiếng Việt](README.vi.md) | Deutsch | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Mit 7.24.0 erschien die letzte Version der Camunda 7 Community
Edition. Fasti BPM setzt sie von dort aus fort: dieselbe Engine auf derselben Datenbank, mit
weiteren Fehlerbehebungen und Bibliotheksaktualisierungen sowie einer neuen Weboberfläche.

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
  fastidev/fasti-bpm:1.0.0
```

Öffnen Sie <http://localhost:8080/fasti/> und melden Sie sich an. Ohne `DB_URL` verwendet das Image eine H2-Datenbank
innerhalb des Containers, ausschließlich zu Evaluierungszwecken. Die REST-API benötigt Basic-Zugangsdaten:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Erstellen Sie das Image selbst** aus dem Quellcode. Dafür sind Docker, JDK 17+ und Netzwerkzugriff
auf Maven Central, nodejs.org und die npm-Registry erforderlich (der Build lädt Node/npm für die
Weboberfläche herunter); der erste Build dauert etwa 10–20 Minuten:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

Das Image **erfordert standardmäßig REST-Authentifizierung und erzwingt Berechtigungsprüfungen** (anders als Camunda
Run); nur `FASTI_INSECURE_DEMO=true` deaktiviert diese für Demonstrationszwecke.

<a id="highlights"></a>

## Highlights

- **Neue Weboberfläche** unter `/fasti/` (React):
  - Prozessinstanzen anhand mehrerer Variablen gleichzeitig suchen, mit AND/OR verknüpft;
  - Fallverlauf innerhalb der Aufgabe (Kommentare und Aktionen zu jedem Schritt);
  - eine Schaltfläche „Nächste Aufgabe abrufen“;
  - Fälligkeitstermine und Dringlichkeit (SLA), überfällige Aufgaben;
  - Teamauslastung nach Benutzer und Gruppe;
  - direkt an Incidents hinterlegte Runbooks;
  - eine Seite zum Systemzustand (Incidents, fehlgeschlagene Jobs, verspätete Jobs, externe Aufgaben, Batches);
  - mobilfreundliches Layout;
  - 13 Sprachen einschließlich Vietnamesisch; auch der BPMN-Modeler unterstützt Vietnamesisch.
- **Single Sign-on (OIDC)**: Keycloak, Microsoft Entra ID und andere OpenID Connect-Anbieter;
  Zuordnung von Claims zu Gruppen und Mandanten; JWT-Bearer-Token für die REST-API. Verfügbar für Run,
  Spring Boot, Tomcat und WildFly.
- **Sicherheitshärtung**: Positivliste für Ausdrücke in Abfragen und Filtern, Prüfungen von Anhängen und
  Kommentaren innerhalb der Engine, Ersteinrichtung, Cockpit-Berechtigungen, offene Weiterleitungen,
  Header der Webanwendungen, Cookies und CSP. Einzelheiten im [Änderungsprotokoll](CHANGELOG.md).
- **Authentifizierung standardmäßig aktiviert**: Das Image erfordert Zugangsdaten für REST und erzwingt
  Berechtigungsprüfungen in jedem Modus.
- **Prüfung der Schemaversion**: Die Engine verweigert den Start bei einem Schema älter als 7.24 und
  aktualisiert ein Schema niemals selbstständig.
- **Java4Shell-Abwehr** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): Das amd64-Image
  enthält eine korrigierte Temurin JRE und deaktiviert das Base64-Intrinsic; die Engine gibt eine Warnung aus, wenn sie
  auf einer betroffenen JVM läuft.
- **Leistung**: Die Datenmenge beim ersten Laden der neuen Weboberfläche sinkt von 960 KB auf 330 KB (gzip standardmäßig
  aktiviert); der Durchsatz des Job Executors steigt durch Optimierung um etwa das 2,5-Fache (Zahlen und Testaufbau in
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Aktuelle Plattform**: Java 17 oder höher, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## Installation

<a id="one-command-on-your-existing-camunda-724-database"></a>

### Ein Befehl für Ihre bestehende Camunda 7.24-Datenbank

Das Image akzeptiert die `DB_*`-Variablen des früheren Camunda 7 Run-Images. Sichern Sie die Datenbank,
und führen Sie dann Folgendes aus:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

Benutzer, Passwörter, Gruppen, Mandanten, Berechtigungen, Filter, Deployments, laufende Instanzen,
Historie, Jobs und Incidents bleiben unverändert. Der Port ist nur an diesen Rechner gebunden; verwenden Sie für den
Fernzugriff einen Reverse Proxy mit HTTPS. Melden Sie sich unter `/fasti/` mit einem bestehenden Konto an.
Da die Authentifizierung aktiviert ist, muss jeder REST-Client (Worker für externe Aufgaben, Integrationen)
Basic-Zugangsdaten oder ein Bearer-Token senden.

- **Parallelbetrieb** mit Camunda 7.24 CE-Knoten auf derselben Datenbank: dieselbe Historienstufe, derselbe
  `TZ`-Wert und dieselben Passworteinstellungen. Wenn die alten Knoten Prozessanwendungen mit Java-Klassen sind, die
  Fasti fehlen, setzen Sie auf Fasti `FASTI_JOB_EXECUTOR_ENABLED=false` (oder konfigurieren Sie jeden Knoten
  als deployment-aware), und starten Sie diese Prozesse bzw. schließen Sie deren Aufgaben nicht über Fasti ab: Ein
  synchroner Aufruf führt die Delegates auf dem Knoten aus, der ihn empfängt.
- **Rollback**: Fasti ändert das Schema nicht. Stoppen Sie daher Fasti und starten Sie die Camunda 7.24-Knoten
  wieder mit derselben Datenbank. Bereitgestellte Prozesse, die ausschließlich in Fasti verfügbare Funktionen verwenden, laufen nicht auf
  Camunda 7; siehe [Rollback auf Camunda 7.24](docs/migrate-from-camunda-7.de.md#rollback-to-camunda-724).
- **Ältere Schemas (7.17–7.23)**: Führen Sie zunächst die im Image enthaltenen `sql/upgrade`-Skripte
  (`/fasti/configuration/sql/upgrade`) bis 7.24 aus; beispielsweise für 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Mit dem Image getestet (Übernahme einer von Camunda Run 7.24.0 erstellten Datenbank, Rollback,
Parallelbetrieb): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (der Oracle-Treiber ist nicht enthalten; fügen Sie `ojdbc11` selbst hinzu; ein Rollback wurde
auf MySQL nicht getestet). **DB2 ist nicht
verifiziert.** H2 dient ausschließlich der Evaluierung. Ergebnisse der einzelnen Tests und Einschränkungen:
[Migrationsleitfaden](docs/migrate-from-camunda-7.de.md#limits).

Ausführliche Anleitungen:

- [Installation mit Docker](docs/install-docker.de.md): H2, PostgreSQL mit `docker run` und
  `docker compose`, bestehende Datenbanken, Parallelbetrieb, Zustandsprüfungen, Upgrade, Rollback,
  Fehlerbehebung.
- [Umstieg von Camunda 7](docs/migrate-from-camunda-7.de.md): Schritte, Gemeinsamkeiten und
  Unterschiede, Rollback, Einschränkungen.
- [Referenz der Umgebungsvariablen für das Image](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Ohne Docker: Tomcat-Bundle (Download von fasti.dev)

Fertiges Tomcat 1.0.0-Bundle (Downloadseite: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Prüfen Sie vor dem Entpacken die Prüfsumme, beispielsweise mit `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

Die Tomcat-Distribution kann auch aus dem Quellcode erstellt werden (`distro/tomcat`). Lesen Sie vor
dem Start die `README.txt` im Bundle: Die REST-API (`/engine-rest`) erfordert standardmäßig HTTP-Basic-Authentifizierung,
es wird kein Benutzer angelegt (erstellen Sie den ersten Administrator auf der Einrichtungsseite unter `/fasti/`),
und setzen Sie bei der Übernahme einer Camunda Run-Datenbank `jobExecutorDeploymentAware=false` in
`conf/bpm-platform.xml`, da andernfalls Timer und asynchrone Jobs bestehender Deployments nicht ausgeführt werden.

<a id="a-ui-built-from-real-user-complaints"></a>

## Eine Benutzeroberfläche auf Basis tatsächlicher Anwenderkritik

Die Funktionen der neuen Weboberfläche wurden anhand öffentlicher Beiträge von Camunda 7-Anwendern im
Camunda-Forum und auf GitHub ausgewählt, zum Beispiel:

| Was Anwender gewünscht haben | Quelle | In Fasti |
|---|---|---|
| Instanzen anhand mehrerer Variablen mit OR suchen | Forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Bedingungseditor für mehrere Variablen, AND/OR |
| Kommentare zu jedem Schritt eines Falls sehen | Forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Fallverlauf in der Aufgabe; Kommentare direkt an der Prozessinstanz |
| Aufgaben nach Variablen sortieren, älteste zuerst | Forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sortierung nach Variablen, Fälligkeitsterminen und Dringlichkeit |
| Delegation, Vertretungen bei Abwesenheit | Forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Delegation einzelner Aufgaben vorhanden; Abwesenheitsvertretungen stehen auf der [Roadmap](#roadmap-plan) |
| Business Key und Variablen beim Start eines Prozesses ohne Formular | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Ja |
| Bereitgestelltes BPMN-XML herunterladen | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Ja |
| Einheitliche Bezeichnungen, mehrere Sprachen | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 Sprachen |

<a id="comparison"></a>

## Vergleich

Ein kompakter Vergleich auf Grundlage der öffentlichen Dokumentation der jeweiligen Projekte (gelesen im November 2026);
„nicht verifiziert“ bedeutet, dass wir keine verlässliche Quelle gefunden haben. Jedes Projekt verfolgt eine eigene Ausrichtung und ist
für unterschiedliche Anforderungen eine sinnvolle Wahl; prüfen Sie vor Ihrer Entscheidung die jeweilige Dokumentation.

| | Lizenz | Läuft auf einer bestehenden C7-Datenbank | Migration erforderlich | Sicherheitspatches | Java / Spring Boot | Benutzeroberfläche | SSO | Docker-Image |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Ja, Schema 7.24 unverändert | Image: keine. Eingebettete Anwendungen: neue Maven-Koordinaten, Spring Boot 4.1 und Java 17+; `org.camunda.*`-Packages bleiben erhalten | Ja, siehe die [nachstehende Richtlinie](#commitments-support-and-security-patches) | 17+ / 4.1 | Neu (React) + klassisch (veraltet) | OIDC integriert | Ja, ab 1.0.0 |
| Camunda 7 CE 7.24 | Quellcode unter Apache-2.0; einige enthaltene Webkomponenten unter MIT ([13]) | (das Original) | — | Keine CE-Releases nach 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 über den Security-Starter | Früheres Run-Image |
| Camunda 7 Enterprise | Kommerziell | Ja (dieselbe Produktlinie) | Nein | Bis 04/2030, kostenpflichtige Verlängerung bis 04/2032 ([3]) | nicht verifiziert | Cockpit Enterprise | nicht verifiziert | nicht verifiziert |
| Camunda 8 | Camunda License 1.0; selbstverwalteter Produktivbetrieb erfordert seit 8.6 eine Lizenz ([4]) | Nein (andere Engine) | Ja (Modelle, Instanzen, Daten) | Ja (Hersteller) | andere Engine, nicht vergleichbar | Operate, Tasklist | nicht verifiziert | nicht verifiziert |
| Operaton | Apache-2.0 | Dieselbe Tabellenstruktur wie 7.24; 2.2 trägt über ein Upgrade-Skript die Schemaversion 7.25 ein ([5]) | Packages in `org.operaton.*` umbenannt (OpenRewrite-Rezept verfügbar); Skript für die Schemaversion (2.2) | Ja, 2.2.x-Linie ([6]) | 17+ / 4.1 ([5]) | Klassisch + webapps-neo (Vorschau) | nicht verifiziert | Ja ([7]) |
| CIB seven | Apache-2.0; EE erfordert einen Lizenzschlüssel | Ja (Änderungen aus 7.24 übernommen) | Packages in `org.cibseven.*` umbenannt, Präfix `cibseven.*` | Ja, je Release ([8]) | 17, 21 / 3.5 und 4.0 ([9]) | Vue 3-Webclient | OAuth2/JWT für REST ([8]) | Ja ([8]) |
| OrqueIO | Apache-2.0 | Laut eigener Dokumentation mit C7-Tabellen kompatibel ([10]) | Maven-Gruppe `io.orqueio`; Umbenennung der Packages nicht angegeben | Ja, laut eigener Dokumentation ([10]) | 21 / 3.5, 4.0, 4.1 | Neuentwicklung mit Angular | OIDC mit mehreren Anbietern | nicht verifiziert |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | Laut Projekt bleibt das Schema mit Camunda 7 kompatibel (von Fasti nicht verifiziert) ([14]) | Packages in `org.eximeebpms.*` umbenannt, Model API umbenannt | Ja, die beiden neuesten Minor-Releases ([11]) | 21 / Spring Boot-Version nicht angegeben | Separater Monitor | nicht verifiziert | Ja (Repository `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | nicht verifiziert | nicht verifiziert | nicht verifiziert | 21 (Corretto-Image) / nicht verifiziert | Control Center | nicht verifiziert | Ja ([12]) |

Die eigenen Schwachstellen von Fasti, klar benannt: ein junges Projekt mit einem kleinen Team, bisher kein kommerzieller
Support, DB2 nicht verifiziert.

Quellen: [1] [Ankündigung des Supportendes der CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [Unterstützte Umgebungen für 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Verlängerung für Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Lizenzierung von Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [Unterstützte Umgebungen für CIB seven](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [OrqueIO-Dokumentation](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Camunda 7-Lizenzen](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
unabhängiger Überblick: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## Zeitlicher Überblick

<a id="camunda-7-milestones"></a>

### Meilensteine von Camunda 7

| Datum | Meilenstein | Quelle |
|---|---|---|
| 2024-03-06 | Camunda kündigt das Supportende von Camunda 7 CE für Oktober 2025 an, mit 7.24 als letztem CE-Release | [Camunda-Forum](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda verlängert den Support für Camunda 7 Enterprise bis 2030-04-09, mit kostenpflichtigem erweitertem Support bis April 2032 | [Camunda-Blog](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Tag `7.24.0` erstellt und auf Maven Central veröffentlicht: das letzte CE-Release | [Tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Von Camunda geplanter Veröffentlichungstermin für 7.24; nach diesem Datum gibt es keine weiteren CE-Releases, einschließlich Sicherheitspatches. Das CE-Repository wurde inzwischen archiviert | [Ankündigung](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [Supportseite](https://docs.camunda.org/enterprise/announcement) |
| April 2030 | Ende des Supports für Camunda 7 Enterprise (7.24 LTS: 2030-04-13) | [Supportseite](https://docs.camunda.org/enterprise/announcement) |
| April 2032 | Ende des kostenpflichtigen erweiterten Supports für Camunda 7 Enterprise | [Supportseite](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti setzt direkt beim Tag `7.24.0` an; jede Fehlerbehebung und Änderung wird von diesem Punkt aus gezählt
(`git log 7.24.0..`). Die folgenden Phasen sind nach den durchgeführten Arbeiten geordnet, ohne Datumsangaben:

1. **Grundlage**: Java 17, Spring Boot 4.1, Bibliotheksaktualisierungen; Maven-Koordinaten
   `dev.fasti.platform`.
2. **Sicherheit**: Behebungen öffentlich bekannt gewordener Probleme der 7.x-Linie, Positivliste für Ausdrücke,
   Prüfungen von Anhängen und Kommentaren innerhalb der Engine, Härtung der Webanwendungen.
3. **Neue Weboberfläche** unter `/fasti/`, die das klassische Cockpit, Tasklist und Admin schrittweise ersetzt.
4. **Single Sign-on** (OIDC) für Run, Spring Boot, Tomcat und WildFly.
5. **Direkter Einsatz mit bestehenden Datenbanken**: Das Image akzeptiert die früheren `DB_*`-Variablen und prüft die Schemaversion; auf fünf Datenbanksystemen getestet.
6. **Sicherheitsprüfung vor der Veröffentlichung** (Code-Review, Penetrationstest des Images, ZAP-Baseline-
   Scan; die Befunde wurden behoben) und **Java4Shell-Abwehr**.
7. **1.0.0**: das erste Release (2026-11-20).

<a id="roadmap-plan"></a>

## Roadmap (Plan)

> Dies ist ein **Plan**, keine Zusage; die Reihenfolge kann sich durch Anwenderfeedback ändern.

- **Abwesenheitsvertretung**: eine Vertretung für einen Datumsbereich; neue Aufgaben werden neu zugewiesen und die
  Änderung wird protokolliert ([Forum 2788](https://forum.camunda.io/t/2788),
  [38199](https://forum.camunda.io/t/38199)).
- **Freigabe nach dem Vier-Augen-Prinzip**: Die freigebende Person eines späteren Schritts darf nicht diejenige sein, die einen früheren
  Schritt ausgeführt hat; dies wird in der Engine erzwungen.
- **Helm-Chart** für Kubernetes.
- **Metriken und OpenTelemetry**: native Unterstützung für Micrometer/Prometheus, Tracing für Prozesse und Jobs.
- **MyBatis-Upgrade** auf eine aktuelle Version.
- **Versionslinien mit Langzeitunterstützung (LTS)** für Anwender, die selten aktualisieren.
- **DB2-Verifizierung**.
- **Endpunkt für aggregierte Auslastungsdaten**, damit die Auslastungsseite auch bei großen Datenbeständen schnell bleibt.

<a id="commitments-support-and-security-patches"></a>

## Zusagen: Support und Sicherheitspatches

> Diese Richtlinie wird vom Fasti BPM-Projektteam gepflegt und kann aktualisiert werden; maßgeblich ist die Version in diesem
> Repository.

- **Unterstützte Versionslinie:** Das neueste Minor-Release der `1.x`-Linie erhält Fehlerbehebungen und Sicherheits-
  patches (als Patch-Releases, beispielsweise `1.0.1`).
- **Sicherheitspatches:** Schwachstellen in Fasti und den mitgelieferten Bibliotheken haben Vorrang vor
  anderen Arbeiten; das Image wird neu erstellt, wenn das Basis-Image oder die JRE eine Korrektur erhält. Das Projekt verfolgt
  öffentliche Sicherheitshinweise zu Camunda 7 und dessen Forks und prüft, ob Fasti betroffen ist.
- **Kompatibilität:** Die `1.x`-Linie behält das Datenbankschema von 7.24 und die `org.camunda.bpm.*`-
  Packages bei. Inkompatible Änderungen werden im [Änderungsprotokoll](CHANGELOG.md) mit **BREAKING** gekennzeichnet.
- **Supportumfang:** Dies ist ein Community-Projekt; Support erfolgt nach bestem Bemühen, **ohne SLA**,
  und es gibt bisher keinen kommerziellen Support.
- **Meldung von Schwachstellen:** Bitte eröffnen Sie **kein** öffentliches Issue; siehe
  [SECURITY.md](SECURITY.md#security-policy) (private Meldung über GitHub).
- **Behobene Fehler:** Die Abschnitte *Behoben* und *Sicherheit* des [Änderungsprotokolls](CHANGELOG.md) führen auf,
  was gegenüber Camunda 7 CE 7.24.0 behoben wurde; Sicherheitshinweise sind in
  [SECURITY.md](SECURITY.md#advisories) aufgeführt.

<a id="documentation"></a>

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [docs/install-docker.md](docs/install-docker.de.md) | Installation mit Docker, Schritt für Schritt |
| [docs/api/README.md](docs/api/README.md) | REST-API-Integration, Java- und JavaScript-Clients für externe Aufgaben |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.de.md) | Umstieg von Camunda 7, Rollback, Einschränkungen |
| [distro/docker/README.md](distro/docker/README.md) | Image: Umgebungsvariablen, Treiber, Betrieb, Optimierung |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | Die neue Weboberfläche |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell und Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Änderungen, Sicherheit, Mitwirkung |

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
[Migrationsleitfaden](docs/migrate-from-camunda-7.de.md#what-differs).

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
[Pfad der Webanwendung](#web-application-path-fasti)):

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

<a id="web-application-path-fasti"></a>

## Pfad der Webanwendung: `/fasti`

Cockpit, Tasklist, Admin und die neue Weboberfläche werden in jeder Distribution unter `/fasti` (früher `/camunda`)
bereitgestellt: `http://localhost:8080/fasti/` öffnet die neue Oberfläche
(`/fasti/app/next/default/`), die klassischen Anwendungen befinden sich unter `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` und `/fasti/app/admin/...`. Die REST-API bleibt unter `/engine-rest`.

**Die klassischen Anwendungen sind veraltet.** Das klassische Cockpit, Tasklist, Admin und Welcome
basieren auf AngularJS 1.8.2 und Bootstrap 3, deren Lebenszyklus jeweils beendet ist. **Das Container-Image stellt sie
standardmäßig nicht bereit** (inkompatible Änderung): Ihre Seiten und alte Lesezeichen werden zum entsprechenden Bereich der neuen Oberfläche weitergeleitet, und Anfragen nach ihren Dateien liefern `404`; `FASTI_CLASSIC_WEBAPPS=true` aktiviert sie
wieder (mit einer Warnung), siehe [distro/docker/README.md](distro/docker/README.md). Auch die
Tomcat- und WildFly-Bundles stellen sie standardmäßig nicht bereit (reaktivieren Sie sie mit
`-Dfasti.webapp.classic.enabled=true` oder `FASTI_CLASSIC_WEBAPPS=true`). Das Run-Zip und der Spring
Boot-Starter stellen sie weiterhin wie bisher bereit, damit laufende Installationen funktionsfähig bleiben; deaktivieren Sie sie
mit `fasti.webapp.classic.enabled: false`. Die klassischen Anwendungen werden in einem
späteren Release entfernt.

Der alte Pfad funktioniert weiterhin: Jede Anfrage an `/camunda/...` wird zum selben Pfad
unter `/fasti/...` weitergeleitet, wobei der restliche Pfad und die Abfragezeichenfolge erhalten bleiben. GET und HEAD erhalten
`301`; alle anderen Methoden (POST, PUT, DELETE, z. B. alte Clients mit Aufrufen an `/camunda/api/...`)
erhalten `308`, damit Clients die Anfrage mit derselben Methode und demselben Inhalt wiederholen. Die Weiterleitungen
enthalten `Cache-Control: no-store`, sodass ein Wechsel zurück zu `/camunda` sofort wirksam wird.

So stellen Sie `/camunda` wieder her:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (dann ohne Weiterleitung).
  Um nur die Weiterleitung zu deaktivieren: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** Löschen Sie `webapps/camunda` (die Weiterleitungsanwendung) und benennen Sie
  `webapps/fasti` in `webapps/camunda` um.
- **WildFly:** Löschen Sie `standalone/deployments/camunda.war` (und `camunda.war.dodeploy`) und
  setzen Sie `<context-root>/camunda</context-root>` in `WEB-INF/jboss-web.xml` der Web-
  anwendungs-WAR.

**Reverse Proxys:** Wenn ein Proxy nur `/camunda/` weiterleitet (oder Pfade umschreibt, Cookie-
Pfade, CSP oder Zustandsprüfungen für `/camunda` festlegt), ergänzen Sie `/fasti/` oder stellen Sie vollständig auf `/fasti/` um.
Das Cookie `XSRF-TOKEN` (und `JSESSIONID` auf Tomcat und WildFly) hat jetzt den Pfad `/fasti`,
sodass sich Benutzer erneut anmelden müssen. Lesezeichen und alte Links funktionieren über die Weiterleitung weiterhin.

<a id="single-sign-on-openid-connect"></a>

## Single Sign-on (OpenID Connect)

Unterstützt auf **Run** (`./start.sh --oauth2`, Modul `internal/oauth2`) und in **Spring Boot**-
Anwendungen mit `fasti-bpm-spring-boot-starter-security`. Die Aktivierung erfolgt durch Deklaration einer
Spring Security-Clientregistrierung (`spring.security.oauth2.client.*`). Hinweise zu **Tomcat** und
**WildFly** finden Sie am Ende dieses Abschnitts.

- **Anmeldung:** Die Anmeldeseite der neuen Webanwendung zeigt „Mit &lt;provider&gt; anmelden“
  anstelle des Passwortformulars (Beschriftung: `fasti.security.oidc.login-label`, standardmäßig der
  `client-name`; wählen Sie bei mehreren Registrierungen eine mit
  `fasti.security.oidc.registration-id` aus). Der Authorization-Code-Flow verwendet `state`, `nonce`
  und PKCE (S256, auch für vertrauliche Clients); die Sitzungs-ID wird bei der Anmeldung ersetzt. Benutzer
  kehren zu der Seite der neuen Webanwendung zurück, von der aus sie gestartet sind (nur Pfade unter
  `/fasti/app/next/` werden akzeptiert, sodass keine offene Weiterleitung möglich ist).
- **Abmeldung:** `POST /logout` (nur Anfragen derselben Herkunft) beendet die Sitzung; mit
  `camunda.bpm.oauth2.sso-logout.enabled=true` wird der Benutzer zum End-
  Session-Endpunkt des Anbieters weitergeleitet (RP-initiierte Abmeldung, `post-logout-redirect-uri`). Die
  Back-Channel-Abmeldung von Spring Security bleibt aktiviert. `GET /logout` meldet den Benutzer nicht mehr ab.
- **Sitzungsablauf:** Wenn das Zugriffstoken nicht erneuert werden kann oder die Sitzung abläuft,
  liefern Anfragen an `/fasti/api/**` den Status `401` (ohne Weiterleitung), und die neue Webanwendung zeigt ihre Anmeldeseite.
- **CSRF:** `/fasti/api` und `/fasti/app` behalten den eigenen CSRF-Filter der Webanwendungen
  (`X-XSRF-TOKEN`); der CSRF-Schutz von Spring Security bleibt wie im Ursprungsprojekt deaktiviert.
- **Gruppen, Administratoren, Mandanten:** Der Gruppen-Claim wird über
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` festgelegt (Eigenschaftsname aus dem Ursprungsprojekt; verschachtelte
  Claims wie `realm_access.roles` funktionieren). `fasti.security.oidc.groups.admin` führt die
  Claim-Werte auf, die `camunda-admin` gewähren; `groups.mapping` ordnet Claim-Werte Gruppen-
  IDs der Engine zu; `groups.include-unmapped=false` verwirft nicht zugeordnete Werte. **Änderung gegenüber dem Ursprungsprojekt:** Ein
  Claim-Wert `camunda-admin` gewährt für sich allein keine Administratorrechte mehr, und
  Berechtigungen wie `SCOPE_openid` oder `OIDC_USER` werden nicht mehr zu Gruppen.
  `fasti.security.oidc.tenants.claim` ordnet Mandanten zu. Name und E-Mail:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Identitätsanbieter:** Standardmäßig liest die Engine Benutzer, Gruppen und Mandanten aus dem
  Token der aktuellen Sitzung (schreibgeschützt). Setzen Sie
  `camunda.bpm.oauth2.identity-provider.enabled=false`, um stattdessen die Datenbank zu verwenden (Gruppen
  stammen dann aus den Mitgliedschaftstabellen). Automatische Provisionierung ist nicht implementiert.
  Solange Benutzer aus dem Token stammen (Standard), ist die Seite zur Ersteinrichtung (erster Administrator)
  deaktiviert, da Administratoren aus den Claims (`groups.admin`) hervorgehen; zuvor wurden Benutzer außerhalb der Administratorengruppe zur Einrichtungsseite weitergeleitet.
- **REST-API mit Bearer-Token:** `fasti.security.oidc.rest.bearer-enabled=true` akzeptiert
  `Authorization: Bearer <JWT>` auf `/engine-rest` zusätzlich zur Basic-Authentifizierung.
  `rest.issuer-uri` und `rest.audiences` sind erforderlich; Signaturen werden anhand des
  JWKS des Ausstellers ausschließlich mit asymmetrischen Algorithmen geprüft (`rest.jws-algorithms`, standardmäßig `RS256`), und
  `iss`, `aud`, `exp`, `nbf` werden validiert. Run benötigt außerdem `camunda.bpm.run.auth.enabled=true`.
  Ein Browser-Sitzungscookie authentifiziert niemals Anfragen an `/engine-rest`.

Keycloak-Beispiel (`configuration/default.yml` von Run):

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

Microsoft Entra ID-Beispiel:

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

**Tomcat, WildFly (WAR):** Dieselben Funktionen, konfiguriert in `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; eine kommentierte `fasti-oidc.properties.example`
liegt daneben). Ohne diese Datei ändert sich nichts. Die Schlüssel entsprechen den `fasti.security.oidc.*`-Eigenschaften von Run, ergänzt um `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. Der
Anbieter muss die Weiterleitungs-URI `<base>/fasti/sso/callback` zulassen; die URL für die Back-Channel-Abmeldung lautet
`<base>/fasti/sso/backchannel-logout`. Bearer-Token auf `/engine-rest` werden mit
`fasti.security.oidc.rest.bearer-enabled=true` aktiviert (Basic-Authentifizierung funktioniert weiterhin, sofern nicht
`rest.basic-enabled=false` gesetzt ist; auch die Engine-Liste `/engine` erfordert Zugangsdaten). Benutzer müssen nicht
in der Datenbank vorhanden sein. Damit die Engine Abfragen zu Gruppen und Mandanten des
angemeldeten Benutzers anhand der Claims beantworten kann, aktivieren Sie das `OidcIdentityProviderPlugin` (auskommentiert in
`bpm-platform.xml` / `standalone.xml`). Einschränkungen: Die für die Back-Channel-Abmeldung erfassten Sitzungen werden
je Server im Arbeitsspeicher gehalten (nicht zwischen Cluster-Knoten geteilt); die klassischen Anwendungen leiten nur zum Anbieter weiter; eine vollständige Abmeldung erfordert die neue Weboberfläche.

<a id="build"></a>

## Build

Erfordert JDK 17 oder höher (21 empfohlen).

```bash
./mvnw clean verify
```

Ein vollständiger Build führt alle Java-Tests und die Unit-Tests der Weboberfläche aus (knapp eine Stunde; die Playwright-
End-to-End-Tests werden separat ausgeführt, siehe [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) und benötigt Netzwerkzugriff auf Maven Central,
nodejs.org und die npm-Registry. Siehe [TESTING.md](TESTING.md).

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
