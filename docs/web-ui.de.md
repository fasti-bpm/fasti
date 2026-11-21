[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | Deutsch | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

# Die Weboberfläche von Fasti

Warum die neue Weboberfläche diese Funktionen hat und unter welchem Pfad die Webanwendungen bereitgestellt werden. Ein Rundgang durch die Oberfläche: <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## Eine Benutzeroberfläche auf Basis tatsächlicher Anwenderkritik

Die Funktionen der neuen Weboberfläche wurden anhand öffentlicher Beiträge von Camunda 7-Anwendern im
Camunda-Forum und auf GitHub ausgewählt, zum Beispiel:

| Was Anwender gewünscht haben | Quelle | In Fasti |
|---|---|---|
| Instanzen anhand mehrerer Variablen mit OR suchen | Forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Bedingungseditor für mehrere Variablen, AND/OR |
| Kommentare zu jedem Schritt eines Falls sehen | Forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Fallverlauf in der Aufgabe; Kommentare direkt an der Prozessinstanz |
| Aufgaben nach Variablen sortieren, älteste zuerst | Forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sortierung nach Variablen, Fälligkeitsterminen und Dringlichkeit |
| Delegation, Vertretungen bei Abwesenheit | Forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Delegation einzelner Aufgaben und [Abwesenheitsvertretungen](absence.md) mit Übergabe (ab 1.1.0) |
| Business Key und Variablen beim Start eines Prozesses ohne Formular | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Ja |
| Bereitgestelltes BPMN-XML herunterladen | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Ja |
| Einheitliche Bezeichnungen, mehrere Sprachen | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 Sprachen; in den BPMN-, DMN- und Formular-Editoren sind die eingebauten Bezeichnungen nur auf Englisch und Vietnamesisch |

<a id="web-application-path-fasti"></a>

## Pfad der Webanwendung: `/fasti`

Cockpit, Tasklist, Admin und die neue Weboberfläche werden in jeder Distribution unter `/fasti` (früher `/camunda`)
bereitgestellt: `http://localhost:8080/fasti/` öffnet die neue Oberfläche
(`/fasti/app/next/default/`), die klassischen Anwendungen befinden sich unter `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` und `/fasti/app/admin/...`. Die REST-API bleibt unter `/engine-rest`.

**Die klassischen Anwendungen sind veraltet.** Das klassische Cockpit, Tasklist, Admin und Welcome
basieren auf AngularJS 1.8.2 und Bootstrap 3, deren Lebenszyklus jeweils beendet ist. **Das Container-Image stellt sie
standardmäßig nicht bereit** (inkompatible Änderung): Ihre Seiten und alte Lesezeichen werden zum entsprechenden Bereich der neuen Oberfläche weitergeleitet, und Anfragen nach ihren Dateien liefern `404`; `FASTI_CLASSIC_WEBAPPS=true` aktiviert sie
wieder (mit einer Warnung), siehe [distro/docker/README.md](../distro/docker/README.md). Auch die
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
