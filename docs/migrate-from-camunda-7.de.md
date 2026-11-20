[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | Deutsch | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

# Umstieg von Camunda 7 auf Fasti BPM

Fasti BPM basiert auf Camunda 7 Community Edition 7.24.0 und ist mit Camunda 7-
Datenbanken kompatibel. Es ist ein unabhängiges Projekt und nicht mit Camunda Services GmbH verbunden.

<a id="summary"></a>

## Zusammenfassung

Wenn Sie Camunda 7 **7.24** einsetzen (Run, das Container-Image, den Spring Boot Starter, Tomcat oder
WildFly), läuft Fasti mit **derselben Datenbank, ohne deren Schema zu ändern**: Es verwendet das 7.24-
Schema unverändert und fügt keine eigenen Tabellen, Spalten oder Schemaversionen hinzu. Sie können
die alten Knoten ersetzen, Fasti parallel dazu betreiben und jederzeit zu Camunda 7.24 zurückkehren.

Datenbanken von Camunda 7.23 und älter müssen zunächst mit den SQL-Skripten auf 7.24 aktualisiert werden (siehe
[Ältere Versionen](#older-versions-717723)). Fasti aktualisiert niemals selbst ein Schema; bei einem älteren Schema verweigert es den
Start und nennt die auszuführenden Skripte.

<a id="steps-container-image"></a>

## Schritte (Container-Image)

1. **Sichern Sie die Datenbank.** Keiner der folgenden Schritte ändert das Schema, aber eine Sicherung ist
   vor jeder Änderung an einem Produktionssystem zwingend erforderlich.
2. **Prüfen Sie die Schemaversion:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — der höchste Wert
   muss `7.24.x` sein.
3. **Notieren Sie die aktuelle Konfiguration** des alten Knotens: `DB_*`-Variablen, Historienlevel
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), REST-Authentifizierung
   (`camunda.bpm.run.auth.enabled`), Berechtigungen (`camunda.bpm.authorization.enabled`),
   Plugins, LDAP/SSO, JVM-Zeitzone, Passwort-Hashing (`passwordEncryptor`, `saltGenerator`),
   Tabellenpräfix/Schema.
4. **Erstellen Sie das Image** wie in [distro/docker/README.md](../distro/docker/README.md) beschrieben.
5. **Starten Sie Fasti mit denselben `DB_*`-Variablen** (und demselben `TZ`). Anders als Camunda Run
   **verlangt das Fasti-Image standardmäßig REST-Authentifizierung und erzwingt Berechtigungen**: Datenbank-
   Benutzerkonten funktionieren weiterhin, aber jeder REST-Client muss Basic-Anmeldedaten oder ein Bearer-Token senden,
   und Benutzer außerhalb von `camunda-admin` benötigen Berechtigungen in `ACT_RU_AUTHORIZATION` (siehe „Standardmäßig
   sicher“ in [distro/docker/README.md](../distro/docker/README.md)).
   `FASTI_INSECURE_DEMO=true` ist nur für Demos vorgesehen. Fasti protokolliert
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Prüfen Sie:** Melden Sie sich unter `/fasti/` mit einem bestehenden Benutzerkonto an; prüfen Sie, ob Filter, Aufgaben, Instanzen und Historie vorhanden sind; rufen Sie `GET /engine-rest/task` (mit Basic-Anmeldedaten) und `GET /actuator/health/readiness` auf.
7. **Leiten Sie den Datenverkehr** (Load Balancer, DNS) zu Fasti um; stoppen Sie die alten Knoten oder lassen Sie sie weiterlaufen (siehe
   [Mischbetrieb](#mixed-operation-with-camunda-7-nodes)).

Spring Boot Starter / Tomcat / WildFly: Ersetzen Sie die `org.camunda.bpm:camunda-*`-Artefakte durch
die entsprechenden `dev.fasti.platform:fasti-*`-Artefakte und behalten Sie `camunda.bpm.*`, `bpm-platform.xml` und
die `org.camunda.bpm.*`-Pakete bei. Behalten Sie nicht beide Engine-JARs im Klassenpfad; Fasti verweigert dann den Start (zu den Grenzen dieser Prüfung siehe „Startprüfung“ in der
[embedding.de.md](embedding.de.md#startup-check)).

> **Spring Boot Starter:** Fasti läuft auf **Spring Boot 4.1** und benötigt **Java 17 oder neuer**.
> Anwendungen mit dem Camunda 7.24 Starter (Spring Boot 3.5) müssen gleichzeitig mit dem
> Austausch der Artefakte auf Spring Boot 4.1 und Java 17+ umsteigen. Siehe die
> [embedding.de.md](embedding.de.md#what-is-different-from-camunda-7240-ce) und das
> [Änderungsprotokoll](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Tomcat- und WildFly-Distributionen

> **Bei der Übernahme der Datenbank von Camunda Run erforderlich** (oder eines Servers, dessen Prozesse
> über die REST-API bereitgestellt wurden): Die Tomcat- und WildFly-Distributionen behalten
> wie Camunda 7 `jobExecutorDeploymentAware=true` bei. Jobs (Timer, asynchrone Fortsetzungen, Wiederholungsversuche)
> von Deployments, die auf dem Knoten nicht registriert sind, **werden nicht ausgeführt, und es wird kein Fehler gemeldet**.
> Setzen Sie vor dem Start `<property name="jobExecutorDeploymentAware">false</property>` in
> `conf/bpm-platform.xml` (Tomcat) oder in den Engine-Eigenschaften von `standalone.xml` (WildFly).
> Behalten Sie `true` bei, wenn andere Knoten Prozessanwendungen mit eigenen Java-Klassen ausführen (siehe
> [Mischbetrieb](#mixed-operation-with-camunda-7-nodes)).

Im Vergleich zu den Tomcat-/WildFly-Distributionen von Camunda 7.24:

- **Die REST-API erfordert Authentifizierung** (inkompatible Änderung). Für `/engine-rest` ist der `camunda-auth`-
  Filter (HTTP Basic) aktiviert: Jeder REST-Client muss den Benutzernamen und das Passwort eines Engine-Benutzers senden; andernfalls erhält er 401; nur die Liste der Engines (`/engine`) bleibt frei zugänglich.
  Konfigurieren Sie vor dem Umstieg die Anmeldedaten in REST-Clients, External-Task-Workern und Skripten, die die REST-
  API anonym aufrufen. OpenID Connect-Bearer-Token (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) funktionieren ebenfalls: Anfragen mit einem gültigen Token benötigen kein Passwort. Um die Authentifizierung abzuschalten (**nicht empfohlen**), entfernen Sie den
  `camunda-auth`-Filter und dessen `filter-mapping` aus `WEB-INF/web.xml` der `engine-rest`-WAR.
- **Keine Standardbenutzer** (inkompatible Änderung). Das Rechnungsbeispiel wird nicht mehr bereitgestellt und erstellt daher
  weder `demo/demo` (Administrator) noch `john`, `mary` und `peter`; seine WAR-Datei ist
  `examples/camunda-invoice.war` und kann zum Ausprobieren nach `webapps/` (Tomcat) oder
  `standalone/deployments/` (WildFly) kopiert werden. Öffnen Sie bei einer leeren Datenbank `/fasti/`
  und erstellen Sie den ersten Administrator auf der Einrichtungsseite; verwenden Sie bei einer Camunda 7-Datenbank die
  bestehenden Benutzerkonten.
- **Die klassischen Anwendungen Cockpit, Tasklist, Admin und Welcome werden nicht ausgeliefert** (inkompatible Änderung), wie auch im
  Image: Ihre Seiten und Lesezeichen führen zum entsprechenden Bereich der neuen Benutzeroberfläche, Anfragen nach ihren Dateien liefern 404 zurück. Um sie vorübergehend wieder zu aktivieren (veraltet, wird entfernt), verwenden Sie eine dieser Einstellungen:
  `-Dfasti.webapp.classic.enabled=true` in `CATALINA_OPTS` (Tomcat) oder `JAVA_OPTS` (WildFly), die
  Umgebungsvariable `FASTI_CLASSIC_WEBAPPS=true` oder den Kontextparameter
  `fasti.webapp.classic.enabled` in `WEB-INF/web.xml` der Webanwendung.

<a id="what-is-identical"></a>

## Was identisch bleibt

- Das Schema und die Zeilen von `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti fügt keine Versionszeile hinzu),
  die Installations-ID, die ID-Generierung (`StrongUuidGenerator` in Run/Spring Boot, der
  standardmäßige `DbIdGenerator` der Engine).
- Passwörter: SHA-512 mit einem 16-Byte-Salt wie bei Camunda 7; bestehende Benutzer können sich anmelden, und
  in Fasti geänderte Passwörter funktionieren in Camunda 7.
- Die REST-API unter `/engine-rest` (unverändertes Verhalten plus zusätzliche Endpunkte), externe Aufgaben
  (`fetchAndLock`, Long Polling), die Java- und JS-Clients, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, `camunda.bpm.*`-
  Eigenschaften, der BPMN-Namensraum `camunda:`.
- Filter aus der klassischen Tasklist (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) werden von der neuen Tasklist im selben Format gelesen und geschrieben.

<a id="what-differs"></a>

## Was sich unterscheidet

- Webanwendungen befinden sich unter `/fasti/app/...`; `/camunda/...` leitet dorthin weiter (301/308). Wenn Ihr Proxy nur `/camunda` zugänglich macht, machen Sie auch `/fasti` zugänglich oder setzen Sie `FASTI_WEBAPP_PATH=/camunda`. Der Stammpfad
  öffnet die neue Webanwendung (`fasti.webapp.default-app: welcome` stellt Welcome wieder her, außer
  im Image, in dem die klassischen Anwendungen deaktiviert sind).
- **Inkompatible Änderung, Container-Image:** Die klassischen Anwendungen Cockpit, Tasklist, Admin und Welcome werden nicht
  ausgeliefert, da AngularJS 1.8.2 und Bootstrap 3 ihr Supportende erreicht haben. Ihre Seiten und Lesezeichen,
  auch unter `/camunda/app/`, leiten zum entsprechenden Bereich der neuen Weboberfläche weiter (`#/cockpit`,
  `#/tasklist`, `#/admin`); Anfragen nach ihren Dateien liefern 404 zurück. Stellen Sie die Benutzer auf die neue Weboberfläche um;
  `FASTI_CLASSIC_WEBAPPS=true` stellt die klassischen Anwendungen während einer Übergangszeit wieder bereit, mit einer
  Warnung. Auch Tomcat und WildFly liefern sie nicht aus (siehe
  [Tomcat- und WildFly-Distributionen](#tomcat-and-wildfly-distributions)). Das Run-Zip und der
  Spring Boot Starter liefern sie weiterhin aus, sie sind jedoch veraltet und werden in einer späteren
  Version entfernt (`fasti.webapp.classic.enabled: false` deaktiviert sie vorzeitig).
- Bei einem älteren Schema verweigert Fasti den Start mit `ENGINE-03111`, statt erst später fehlzuschlagen
  (`FASTI_SCHEMA_VERSION_CHECK=warn`, um trotzdem zu starten).
- Ausdrücke in Abfragen und Filtern sind auf eine Positivliste beschränkt (Datumsfunktionen, aktueller
  Benutzer); Filter, die benutzerdefinierte Beans oder Funktionen verwenden, benötigen deren Deklaration in den Engine-Konfigurations-
  Eigenschaften `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` und
  `allowedExpressionPropertiesInQueries`.
- Standardeinstellungen des Images: Schemaaktualisierung `false` (`FASTI_DB_SCHEMA_UPDATE=true` für eine neue Datenbank); kein
  Demobenutzer, Rechnungsbeispiel oder Filter „Alle Aufgaben“; CORS deaktiviert (`FASTI_CORS_ALLOWED_ORIGINS=*`
  aktiviert es wieder); `/actuator/health` hinzugefügt; Dateien unter `/fasti` (unter
  `/camunda/configuration` eingebundene Dateien werden ebenfalls gelesen); kein `--swaggerui`.
- OAuth2/OIDC: `camunda-admin` in einem Gruppen-Claim gewährt allein keine Administratorrechte mehr
  (`fasti.security.oidc.groups.admin`); `group-name-delimiter` ist wörtlicher Text, kein regulärer Ausdruck;
  die Abmeldung erfordert einen POST vom selben Ursprung.
- Neue Tasklist: Ein Filter ohne `refresh` wird automatisch aktualisiert.
- Die Run-JAR heißt `fasti-bpm-run-core.jar`.
- Sicherheitskorrekturen gegenüber 7.24.0 (Berechtigungen für Anhänge/Kommentare, Ersteinrichtung, ...) können dazu führen, dass einige zuvor erfolgreiche Aufrufe 403 zurückgeben; siehe die [README](../README.de.md).

<a id="rollback-to-camunda-724"></a>

## Rückkehr zu Camunda 7.24

Stoppen Sie Fasti und starten Sie die Camunda 7.24-Knoten wieder mit derselben Datenbank. Alles, was in
Fasti ausgeführt wurde, ist dort sichtbar. Zurück bleiben: `fasti.prefs.*`-Zeilen in `ACT_ID_INFO` (Einstellungen der
neuen Weboberfläche, von Camunda 7 ignoriert) und `refresh: true` bei Filtern, die von der neuen Tasklist gespeichert wurden und bei denen
dieser Eintrag zuvor fehlte. Bereitgestellte Prozesse, die ausschließlich in Fasti verfügbare Funktionen verwenden, laufen nicht auf Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Mischbetrieb mit Camunda 7-Knoten

Fasti und Camunda 7.24 können gleichzeitig mit einer Datenbank betrieben werden (getestet: Beide sehen die Daten
des jeweils anderen, und Jobs laufen auf beiden Knoten). Voraussetzungen:

- **Dasselbe Historienlevel** (das Image verwendet das gespeicherte Level), **dieselbe JVM-Zeitzone**, dieselben
  Passworteinstellungen und dasselbe Tabellenpräfix;
- **Job Executor:** Standardmäßig übernimmt Fasti (Image, Run, Spring Boot) wie Run die Jobs aller Deployments.
  Die Tomcat- und WildFly-Distributionen verwenden standardmäßig `jobExecutorDeploymentAware=true` (wie Camunda
  7): Jobs (Timer, asynchrone Fortsetzungen, Wiederholungsversuche) von Deployments, die auf diesem
  Knoten nicht registriert sind, einschließlich Deployments, die vor dem letzten Neustart durch Run oder über REST erstellt wurden, **werden nicht
  ausgeführt**; setzen Sie bei der Übernahme der Datenbank von Run den Wert in `conf/bpm-platform.xml`
  (Tomcat) oder `standalone.xml` (WildFly) auf `false`. Wenn die alten Knoten Prozessanwendungen mit Klassen ausführen, die Fasti nicht besitzt (`JavaDelegate`, Listener, Beans),
  würde Fasti deren Jobs übernehmen, die dann fehlschlagen und Incidents auslösen würden. Setzen Sie dann
  `FASTI_JOB_EXECUTOR_ENABLED=false` auf Fasti oder konfigurieren Sie **jeden** Knoten so, dass er Deployments berücksichtigt (der
  Job Executor eines Knotens führt nur Jobs von Deployments aus, die auf diesem Knoten registriert sind; ein über
  REST erstelltes Deployment ist bis zu einem Neustart nur auf dem Knoten registriert, der es empfangen hat). Synchrone REST-Aufrufe (Abschließen einer Aufgabe,
  Starten einer Instanz) führen Delegates weiterhin auf dem Knoten aus, der sie empfängt — leiten Sie solche
  Prozesse nicht an Fasti weiter;
- **Load Balancer:** Sticky Sessions für die Webanwendungen; REST-Aufrufe mit Basic-Authentifizierung benötigen keine;
- Die klassischen Webanwendungen (auf Camunda 7-Knoten oder Fasti mit
  `FASTI_CLASSIC_WEBAPPS=true`) und die neue Webanwendung verwenden gemeinsame Filter und Benutzer.

<a id="older-versions-717723"></a>

## Ältere Versionen (7.17–7.23)

Erstellen Sie eine Sicherung; führen Sie `sql/upgrade/<db>_engine_<from>_to_<to>.sql` der Reihe nach bis 7.24 aus, einschließlich
noch ausstehender Patch-Skripte Ihrer aktuellen Minor-Version (enthalten in `configuration/sql/upgrade`, im
Image unter `/fasti/configuration/sql/upgrade`); prüfen Sie, ob `ACT_GE_SCHEMA_LOG` den Wert `7.24.0` enthält;
starten Sie Fasti. Beispiel 7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, dann
`postgres_engine_7.23_to_7.24.sql`. Camunda-Knoten älter als 7.24 dürfen mit dem
aktualisierten Schema nicht weiterbetrieben werden, außer im Rahmen der Regeln von Camunda für Rolling Updates (nur eine Minor-Version, und die
alten Knoten dürfen keine neuen Funktionen verwenden).

<a id="performance"></a>

## Leistung

Die Standardeinstellungen des Images (10 Verbindungen im Pool, Job Executor mit 3 Kern-Threads/maximal 10 Threads) entsprechen Camunda Run. Wenn Sie den Verbindungspool oder Job Executor Ihrer bisherigen Camunda 7-Installation angepasst haben, übernehmen Sie diese Werte
(`FASTI_DB_CONN_MAXACTIVE`, `CAMUNDA_BPM_JOB_EXECUTION_*`); Hinweise und Messwerte finden Sie in
`distro/docker/README.md`, „Leistungsoptimierung“. Fasti fügt dem Schema keinen Index hinzu.

<a id="limits"></a>

## Einschränkungen

- Nur das Schema der Community Edition 7.24 wird unterstützt; Enterprise-Patch-Schemas sind nicht getestet.
- Mit dem Container-Image getestet (Camunda Run 7.24.0 erstellt die Datenbank und befüllt sie mit Ausgangsdaten → Fasti
  übernimmt → zurück zu Run → beide gleichzeitig):

  | Datenbank | Treiber | Übernahme | Rückkehr | Mischbetrieb | Upgrade 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | enthalten | bestanden | bestanden | bestanden | nicht getestet |
  | MariaDB 11.8 | MariaDB Connector/J (enthalten) oder MySQL Connector/J | bestanden | bestanden | bestanden | bestanden |
  | MySQL 8.4 | enthalten | bestanden | — | bestanden | bestanden |
  | SQL Server 2022 (Developer) | enthalten (`encrypt=false`) | bestanden | bestanden | bestanden | nicht getestet |
  | Oracle Database 23ai Free | `ojdbc11` über `FASTI_EXTRA_LIB` | bestanden | bestanden | bestanden | nicht getestet |
  | DB2 | — | nicht getestet | | | |

  Außerdem: Zwei Fasti-Knoten auf MariaDB (MariaDB Connector/J), die parallel dieselben Aufgaben abschlossen und externe Aufgaben abriefen, verursachten weder Verluste noch Duplikate; die unterlegene Transaktion erhielt einen Fehler wegen optimistischer Sperrung. MySQL/MariaDB benötigen `transaction-isolation=READ-COMMITTED`. Geben Sie unter MySQL
  jeder Engine eine eigene Datenbank: Die Engine (sowohl Camunda Run als auch Fasti) sucht nach Tabellen
  in jeder Datenbank, auf die das Benutzerkonto zugreifen kann.
- Für Camunda 7 erstellte Plugins und Prozessanwendungen müssen mit den Fasti-
  Artefakten neu gebaut werden, um innerhalb von Fasti zu laufen; REST-Clients und Clients für externe Aufgaben benötigen keine Änderungen.
- Die Schemaprüfung liest nur `ACT_GE_SCHEMA_LOG`; sie vergleicht keine Spalten.
