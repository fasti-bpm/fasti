[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | Français | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

# L’interface web de Fasti

Pourquoi la nouvelle interface web a ces fonctionnalités, et sous quel chemin les applications web sont servies. Une visite de l’interface : <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## Une interface conçue à partir des problèmes concrets signalés par les utilisateurs

Les fonctionnalités de la nouvelle interface web ont été choisies à partir des témoignages publics d’utilisateurs de Camunda 7 sur le
forum Camunda et sur GitHub, par exemple :

| Demandes des utilisateurs | Source | Dans Fasti |
|---|---|---|
| Rechercher des instances selon plusieurs variables, avec OU | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Éditeur de conditions multivariables, ET/OU |
| Voir les commentaires à chaque étape d’un dossier | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Chronologie du dossier dans la tâche ; commentaires sur l’instance de processus elle-même |
| Trier les tâches par variables, les plus anciennes en premier | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Tri par variables, dates d’échéance et urgence |
| Délégation, remplaçants pendant les absences | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Délégation par tâche et [remplaçants pendant les absences](absence.md) avec passation (à partir de 1.1.0) |
| Clé métier et variables lors du démarrage d’un processus sans formulaire | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Oui |
| Télécharger le XML BPMN déployé | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Oui |
| Libellés cohérents, plusieurs langues | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 langues ; dans les éditeurs BPMN, DMN et de formulaires, les libellés intégrés ne sont qu’en anglais et en vietnamien |

<a id="cases-cmmn"></a>

## Dossiers (CMMN)

Les dossiers modélisés en CMMN 1.1 et déployés sous forme de fichiers `.cmmn` sont suivis et
traités dans la nouvelle interface : Cockpit → Dossiers (CMMN) affiche les définitions de dossier
avec leurs versions, leur diagramme et leurs instances ouvertes ; une instance de dossier montre
l’état de chaque élément de plan et propose les transitions manuelles démarrer, achever,
désactiver, réactiver et interrompre (les actions irréversibles demandent confirmation), ainsi
que les tâches, variables et l’historique ; Cockpit → Historique des dossiers liste les instances
ouvertes et clôturées. Dans la Tasklist, une tâche d’un dossier affiche son dossier, renvoie à
l’instance de dossier et dessine le diagramme. Fasti dessine les diagrammes à partir du CMMNDI du
modèle ; la bibliothèque cmmn-js, qui n’est plus maintenue
(dernière version 0.20.0), n’est pas utilisée.

<a id="web-application-path-fasti"></a>

## Chemin des applications web : `/fasti`

Cockpit, Tasklist, Admin et la nouvelle interface web sont accessibles sous `/fasti` (anciennement `/camunda`)
dans toutes les distributions : `http://localhost:8080/fasti/` ouvre la nouvelle interface
(`/fasti/app/next/default/`), les applications classiques se trouvent sur `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` et `/fasti/app/admin/...`. L’API REST reste sur `/engine-rest`.

**Les applications classiques sont obsolètes.** Les applications classiques Cockpit, Tasklist, Admin et Welcome
reposent sur AngularJS 1.8.2 et Bootstrap 3, tous deux en fin de vie. **L’image de conteneur ne les
expose pas par défaut** (modification incompatible) : leurs pages et les anciens favoris redirigent vers la section correspondante de la nouvelle interface, et les requêtes visant leurs fichiers renvoient `404` ; `FASTI_CLASSIC_WEBAPPS=true` les
réactive (avec un avertissement), voir [distro/docker/README.md](../distro/docker/README.md). Les
distributions Tomcat et WildFly ne les exposent pas non plus par défaut (réactivez-les avec
`-Dfasti.webapp.classic.enabled=true` ou `FASTI_CLASSIC_WEBAPPS=true`). L’archive zip Run et le starter Spring
Boot continuent de les exposer comme auparavant, afin de ne pas perturber les installations en service ; désactivez-les
avec `fasti.webapp.classic.enabled: false`. Les applications classiques seront supprimées dans une
version ultérieure.

L’ancien chemin continue de fonctionner : toute requête vers `/camunda/...` est redirigée vers le même chemin
sous `/fasti/...`, en conservant le reste du chemin et la chaîne de requête. GET et HEAD reçoivent
`301` ; toutes les autres méthodes (POST, PUT, DELETE, par exemple les anciens clients appelant `/camunda/api/...`)
reçoivent `308`, afin que les clients répètent la requête avec la même méthode et le même corps. Les redirections
comportent `Cache-Control: no-store`, de sorte qu’un retour à `/camunda` prend effet immédiatement.

Pour rétablir `/camunda` :

- **Spring Boot, Run :** `camunda.bpm.webapp.application-path: /camunda` (aucune redirection dans ce cas).
  Pour désactiver uniquement la redirection : `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat :** supprimez `webapps/camunda` (l’application de redirection) et renommez
  `webapps/fasti` en `webapps/camunda`.
- **WildFly :** supprimez `standalone/deployments/camunda.war` (ainsi que `camunda.war.dodeploy`) et
  définissez `<context-root>/camunda</context-root>` dans le fichier `WEB-INF/jboss-web.xml` du WAR de
  l’application web.

**Proxys inverses :** si un proxy ne transmet que `/camunda/` (ou réécrit les chemins, définit les chemins
des cookies, la CSP ou les contrôles d’état pour `/camunda`), ajoutez `/fasti/` ou passez entièrement à `/fasti/`.
Le cookie `XSRF-TOKEN` (et `JSESSIONID` sur Tomcat et WildFly) utilise désormais le chemin `/fasti`,
les utilisateurs doivent donc se reconnecter. Les favoris et les anciens liens continuent de fonctionner grâce à la redirection.
