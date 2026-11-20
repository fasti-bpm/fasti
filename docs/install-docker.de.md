[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | Deutsch | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

# Fasti BPM mit Docker installieren

Eine Schritt-für-Schritt-Anleitung: ein schneller Test mit H2, Produktivbetrieb mit PostgreSQL (`docker run` oder
`docker compose`), Anbindung an eine bestehende Camunda 7-Datenbank, Parallelbetrieb, Zustandsprüfungen,
Upgrade, Rollback und Fehlerbehebung. Vollständige Image-Referenz:
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM basiert auf Camunda 7 CE
7.24.0 und ist ein unabhängiges Projekt, das nicht mit Camunda Services GmbH verbunden ist.

<a id="1-requirements"></a>

## 1. Voraussetzungen

- Docker Engine mit Docker Compose v2 (`docker compose`).
- Für den regulären Betrieb: mindestens 2 CPUs und 2 GB Arbeitsspeicher für den Fasti-Container (bei weniger Ressourcen
  wählt die JVM den Serial GC).
- Produktionsdatenbank: PostgreSQL, MariaDB, MySQL, SQL Server oder Oracle (siehe die
  [getestete Kompatibilitätsmatrix](migrate-from-camunda-7.de.md#limits)). DB2 wurde nicht überprüft. H2 dient ausschließlich der Evaluierung.

<a id="2-pull-the-image"></a>

## 2. Image herunterladen

<!-- update with the Docker Hub image name at release -->
Das Image wird für amd64 und arm64 an zwei Stellen veröffentlicht:

```bash
docker pull fastidev/fasti-bpm:1.0.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.0.0     # GitHub Container Registry
```

Tags: `1.0.0` (genau diese Version) und `1.0` (neueste Patch-Version von 1.0). Das Image ist mit
cosign signiert (schlüssellos, GitHub OIDC) und enthält eine SBOM sowie einen Herkunftsnachweis.

Sie können das Image auch aus dem Quellcode erstellen (erfordert JDK 17+ und Netzwerkzugriff auf Maven
Central, nodejs.org und die npm-Registry) und in allen folgenden Befehlen den Namen `fasti-bpm:local` anstelle von
`fastidev/fasti-bpm:1.0.0` verwenden:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Schnelltest mit H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.0.0
```

- Öffnen Sie <http://localhost:8080/fasti/> und melden Sie sich als `admin` an.
- Ohne `DB_URL` verwendet das Image H2 unter `/fasti/data`; das Volume `fasti-data` erhält die Daten,
  wenn der Container neu erstellt wird. Nur zur Evaluierung, niemals für echte Daten verwenden.
- Ergänzen Sie den Befehl um `--webapps --rest --example`, um zusätzlich einen Beispielprozess (Rechnung)
  zu erhalten. Mit `--example` allein bleiben die Weboberfläche und die REST-API deaktiviert.
- Die REST-API ist unter `http://localhost:8080/engine-rest` erreichbar und erfordert HTTP-Basic-Zugangsdaten; siehe die
  [REST-API-Anleitung](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Produktivbetrieb mit PostgreSQL: `docker run`

Erstellen Sie zwei Secret-Dateien (Datenbankpasswort und Administratorpasswort). Mit `--production` erzwingt die Engine
ihre Passwortrichtlinie: mindestens 10 Zeichen mit Groß- und Kleinbuchstaben, einer Ziffer
und einem Sonderzeichen.

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
  -e TZ=Europe/Berlin \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.0.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` erstellt nur fehlende Tabellen in einer neuen Datenbank; Fasti aktualisiert niemals
  ein bestehendes Schema. Die Einstellung kann bei späteren Starts bestehen bleiben.
- `--production` aktiviert `Secure`-Cookies und verweigert eine implizite H2-Datenbank sowie `DEBUG=true`.
  Terminieren Sie TLS an einem Reverse-Proxy vor Fasti; Browser senden `Secure`-Cookies nur über HTTPS (oder an
  `localhost`).
- `WAIT_FOR` verzögert den Start, bis die Datenbank Verbindungen annimmt.
- Das Administratorpasswort wird nur gesetzt, wenn der Administrator noch nicht existiert; ändern Sie es später in Admin.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Produktivbetrieb mit PostgreSQL: `docker compose`

Beispieldatei: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, Secrets, Zustandsprüfungen, `--production`, Speicherlimit von 2 GB).

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

Beim Start können Sie folgende Variablen setzen: `FASTI_IMAGE` (Standard: `fastidev/fasti-bpm:1.0.0`, zum
Beispiel `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (Standard: `8080`), `TZ` (Standard: `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Anbindung an eine bestehende Camunda 7-Datenbank

Fasti läuft auf dem Schema 7.24, ohne es zu verändern. Vor dem Start:

1. **Sichern** Sie die Datenbank.
2. Prüfen Sie die Schemaversion: `select VERSION_ from ACT_GE_SCHEMA_LOG` — der höchste Wert muss
   `7.24.x` sein. Bei den Schemaversionen 7.17–7.23 führen Sie zuerst die Skripte unter `sql/upgrade` aus (im Image unter
   `/fasti/configuration/sql/upgrade` enthalten), siehe die
   [Migrationsanleitung](migrate-from-camunda-7.de.md#older-versions-717723).
3. Prüfen Sie die Berechtigungen, da Fasti sie durchsetzt: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` und `select count(*) from ACT_RU_AUTHORIZATION`. Wenn das alte System
   mit deaktivierter Berechtigungsprüfung lief, erteilen Sie regulären Benutzern die erforderlichen Berechtigungen, bevor Sie den Datenverkehr umleiten.
4. Notieren Sie `TZ`, die Historienstufe, das Tabellenpräfix/Schema und die Passworteinstellungen der
   alten Knoten.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0 --production
```

- Der Port ist nur an diesen Rechner gebunden; verwenden Sie für den Fernzugriff einen Reverse-Proxy mit HTTPS.
- Setzen Sie `FASTI_DB_SCHEMA_UPDATE` und `FASTI_ADMIN_*` **nicht**: Die Tabellen und Konten existieren bereits. Das
  Startprotokoll muss `ENGINE-03115 Database schema version 7.24.0 matches this engine.` enthalten.
- Die Historienstufe ist standardmäßig `auto`: Es wird die in der Datenbank gespeicherte Stufe verwendet.
- Jeder REST-Client muss nun Basic-Zugangsdaten oder ein Bearer-Token senden.
- Treiber: PostgreSQL, MySQL, MariaDB, SQL Server und H2 sind enthalten. Fügen Sie die Treiber für **Oracle** und **DB2** selbst hinzu (`ojdbc11.jar`, `jcc.jar`), mit `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: Der Treiber 13.x verschlüsselt die Verbindung und überprüft standardmäßig das Zertifikat; der
  Server benötigt ein gültiges Zertifikat, das zum Hostnamen in der URL passt. `trustServerCertificate=true`
  (überspringt Zertifikatsprüfungen) oder `encrypt=false` (keine Verschlüsselung) sind nur für lokale Tests vorgesehen. MySQL/MariaDB benötigen
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Parallelbetrieb mit Camunda 7-Knoten

Fasti und Camunda 7.24 können gleichzeitig auf einer Datenbank laufen (getestet: Beide sehen die Daten
des jeweils anderen). Voraussetzungen:

- gleiche Historienstufe, gleiches `TZ`, gleiche Passworteinstellungen, gleiches Tabellenpräfix;
- wenn die alten Knoten Prozessanwendungen mit Java-Klassen (`JavaDelegate`, Listenern) sind, die Fasti fehlen, setzen Sie bei Fasti `-e FASTI_JOB_EXECUTOR_ENABLED=false` oder konfigurieren Sie **jeden** Knoten
  so, dass er Deployments berücksichtigt (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` für Fasti). Dies schützt nur
  asynchrone Jobs: Beim Starten von Instanzen oder Abschließen von Aufgaben über Fasti werden die Delegates weiterhin
  auf Fasti ausgeführt; senden Sie diese Anfragen daher an Knoten, auf denen die Java-Klassen vorhanden sind;
- Load-Balancer: Sticky Sessions für die Webanwendungen; REST mit Basic-Zugangsdaten benötigt keine.

Details: [Mischbetrieb mit Camunda 7-Knoten](migrate-from-camunda-7.de.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Häufig verwendete Umgebungsvariablen

| Variable | Bedeutung |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | Datenbankverbindung (Variablennamen des früheren Camunda 7 Run-Images; Aliasse: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | vor dem Start auf `host:port` warten |
| `TZ` | JVM-Zeitzone; muss auf allen Knoten gleich sein |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: fehlende Tabellen in einer neuen Datenbank erstellen |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | den Administrator erstellen, falls er fehlt |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | Job-Executor dieses Knotens |
| `FASTI_EXTRA_LIB` | Verzeichnis mit zusätzlichen JAR-Dateien (Oracle-, DB2-Treiber) |
| `FASTI_DB_CONN_MAXACTIVE` | Größe des Verbindungspools (Standard: 10) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | JVM-Optionen, prozentualer Speicheranteil für den Heap (75) |

Vollständige Tabelle: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Zustandsprüfungen

| Endpunkt | Verwendung |
|---|---|
| `/actuator/health/liveness` | Kubernetes-Liveness-Probe |
| `/actuator/health/readiness` | Readiness-Probe, Load-Balancer; umfasst die Datenbankverbindung und die Engine |
| `/actuator/health` | Gesamtzustand |

Eine Anmeldung ist nicht erforderlich; nur die Zustandsendpunkte sind verfügbar. Der `HEALTHCHECK` des Images verwendet
die Bereitschaftsprüfung, sodass `docker ps` den Status `healthy` anzeigt, sobald Fasti bereit ist:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. Upgrade und Rollback

**Fasti aktualisieren** (zum Beispiel `1.0.0` → `1.0.1`):

```bash
docker pull fastidev/fasti-bpm:1.0.1
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.0.1 --production   # same options as before
```

Mit Docker Compose: Ändern Sie `FASTI_IMAGE` (oder die Zeile `image:`), und führen Sie anschließend
`docker compose -f docker-compose.postgres.yml up -d` aus. Lesen Sie zuvor das [Änderungsprotokoll](../CHANGELOG.md),
insbesondere die Einträge unter **BREAKING**. Die Versionsreihe `1.x` behält das Schema 7.24 bei, sodass Upgrades
innerhalb dieser Reihe das Schema nicht verändern. `docker stop -t 60` lässt laufende Anfragen abschließen, bevor der
Container gestoppt wird.

**Rollback:**

- zur vorherigen Fasti-Version: erneut mit dem alten Tag starten;
- zu Camunda 7.24: Fasti stoppen und die Camunda 7.24-Knoten wieder mit derselben Datenbank starten. Die Einstellungen der neuen Benutzeroberfläche bleiben erhalten (`fasti.prefs.*` in `ACT_ID_INFO`, von Camunda 7 ignoriert). Bereitgestellte Prozesse, die ausschließlich in Fasti verfügbare Funktionen verwenden, laufen nicht auf Camunda 7. Siehe
  [Rollback zu Camunda 7.24](migrate-from-camunda-7.de.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Fehlerbehebung

Lesen Sie das Protokoll mit `docker logs fasti` (oder `docker compose logs fasti`).

| Symptom | Ursache und Behebung |
|---|---|
| Die REST-API gibt `401` zurück | Fasti erfordert Authentifizierung: Senden Sie `-u user:password` oder ein Bearer-Token |
| Leere Listen oder `403` | Dem Benutzer fehlen Berechtigungen: Erteilen Sie ihm unter Admin → Berechtigungen die erforderlichen Berechtigungen für die betroffenen Ressourcen. Fügen Sie nur tatsächliche Administratoren zu `camunda-admin` hinzu |
| Stoppt mit `ENGINE-03111 The database schema has version …` | Schema älter als 7.24: Erstellen Sie eine Sicherung und führen Sie anschließend die Skripte unter `sql/upgrade` aus (Abschnitt 6). Umgehen Sie die Prüfung nicht mit `FASTI_SCHEMA_VERSION_CHECK=warn`: Der Betrieb mit einem älteren Schema wird nicht unterstützt und kann zur Laufzeit fehlschlagen |
| Stoppt mit `Insecure configuration refused` | Eine Einstellung deaktiviert die REST-Authentifizierung oder die Berechtigungsprüfung; entfernen Sie sie (nur `FASTI_INSECURE_DEMO=true` öffnet die Instanz, für Demos) |
| Stoppt mit `Password does not match policy` | `--production` wendet die Passwortrichtlinie auf `FASTI_ADMIN_PASSWORD` an: mindestens 10 Zeichen mit Groß- und Kleinbuchstaben, einer Ziffer und einem Sonderzeichen |
| `password authentication failed` | Das Passwort in der Secret-Datei weicht vom Datenbankpasswort ab. Unter Windows erstellte Dateien können mit `\r\n` enden: Erstellen Sie sie mit `tr -d '\r\n'` neu. PostgreSQL liest `POSTGRES_PASSWORD_FILE` nur bei der erstmaligen Initialisierung des Volumes |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | Setzen Sie `FASTI_ADMIN_PASSWORD` oder `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | Es ist keine Datenbank konfiguriert; `--production` greift nicht auf die H2-Demodatenbank zurück: Setzen Sie `DB_URL` auf eine Produktionsdatenbank |
| `… cannot be read` bei einer `_FILE`-Variablen | Die Datei ist nicht in den Container eingebunden oder UID 1000 kann sie nicht lesen (die Datei benötigt den Modus `0644`; ihr Verzeichnis kann `0700` behalten) |
| Die Webanmeldung über HTTP hält die Sitzung nicht aufrecht | Das `Secure`-Cookie (`--production`) wird nur über HTTPS (oder an `localhost`) gesendet: Terminieren Sie TLS am Proxy |
| Verbindung zu SQL Server nicht möglich (Zertifikatsfehler) | Statten Sie SQL Server mit einem gültigen Zertifikat aus, das zum Hostnamen in der URL passt; `trustServerCertificate=true` ist nur für lokale Tests vorgesehen |
| Der Container startet ständig neu und das Protokoll zeigt `OutOfMemoryError` | Die JVM beendet sich bei Speichermangel: Erhöhen Sie das Speicherlimit des Containers (2 GB) |

Kommen Sie weiterhin nicht weiter? Erstellen Sie ein GitHub-Issue mit dem Protokoll (Passwörter entfernen). Bei Sicherheitsproblemen siehe
[SECURITY.md](../SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Ohne Docker

Fertig zusammengestelltes Tomcat 1.0.0-Paket (Downloadseite: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Überprüfen Sie die Prüfsumme vor dem Entpacken, zum Beispiel mit `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.
