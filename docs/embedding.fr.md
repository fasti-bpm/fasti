[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | Français | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

# Intégrer Fasti BPM : compatibilité et coordonnées Maven

Ce que Fasti conserve de Camunda 7 et ce qui diffère de 7.24.0, la numérotation des versions, et les coordonnées Maven pour les applications qui intègrent le moteur. Pour exécuter Fasti comme serveur, consultez [Installation avec Docker](install-docker.fr.md) ou la distribution Tomcat sur <https://fasti.dev/download>.

<a id="what-this-is"></a>

## Présentation du projet

Ce dépôt contient le moteur de processus BPMN 2.0 / DMN, l’API REST, les applications
web (Cockpit, Tasklist, Admin) et les distributions (Run, Tomcat, WildFly). Il
part du tag `7.24.0` de Camunda 7 CE et poursuit le développement avec des corrections de bogues et des mises à niveau
de bibliothèques.

L’objectif est la **rétrocompatibilité** avec les applications qui fonctionnent sur Camunda 7 :

- les packages `org.camunda.bpm.*`, l’espace de noms BPMN
  `http://camunda.org/schema/1.0/bpmn` et le préfixe de configuration `camunda.bpm.*` sont conservés ;
- le schéma de base de données de 7.24 est conservé ;
- tous les modes de déploiement sont conservés : Spring Boot, Run, Tomcat, WildFly ; `javax` et `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Différences par rapport à Camunda 7.24.0 CE

- **Sécurité :** correctifs pour les problèmes divulgués publiquement de la branche 7.x (expressions dans les
  requêtes, configuration initiale, accès aux pièces jointes et aux commentaires, permissions Cockpit, redirections
  ouvertes), ainsi que des protections supplémentaires : liste d’autorisation pour les expressions dans les requêtes et les
  filtres, vérifications des pièces jointes et des commentaires au sein du moteur, renforcement des filtres de sécurité,
  des en-têtes et des cookies dans les applications web.
- **Stabilité :** suppression de la fuite mémoire lors de l’évaluation de scripts et d’expressions DMN avec
  JavaScript (GraalJS), ainsi que plusieurs autres corrections du moteur.
- **Plateforme :** Java 17 ou version ultérieure, Spring Boot 4.1, GraalJS 25, Jackson 2.21 et de nombreuses autres
  bibliothèques mises à niveau vers des versions contenant des correctifs de sécurité.

Certains correctifs modifient les paramètres par défaut pour adopter un comportement plus sûr ; lisez « Différences » dans le
[guide de migration](migrate-from-camunda-7.fr.md#what-differs) avant la mise à niveau.

<a id="versioning"></a>

## Gestion des versions

Fasti BPM Platform suit le [versionnage sémantique](https://semver.org/), à partir de `1.0.0`.
La branche `1.x` conserve l’API Java, les packages `org.camunda.bpm.*`, le préfixe de configuration
`camunda.bpm.*` et le schéma de base de données de Camunda 7.24. Les applications qui embarquent le
moteur passent à Java 17 ou version ultérieure et, avec le starter Spring Boot, à Spring Boot 4.1 ;
les plugins et applications de processus sont recompilés avec les artefacts `dev.fasti.platform`.

<a id="maven-coordinates"></a>

## Coordonnées Maven

Les artefacts de Fasti BPM Platform disposent de leurs propres coordonnées Maven. **Les packages Java
restent `org.camunda.bpm.*`**, de sorte que le code des applications conserve ses imports ; le préfixe de configuration `camunda.bpm.*`,
les espaces de noms BPMN/DMN, le schéma de base de données et les noms de modules sur
WildFly restent également inchangés. Pour Maven, seules les déclarations de dépendances
changent (le chemin des applications web change séparément, voir
[Chemin des applications web](web-ui.fr.md#web-application-path-fasti)) :

- **groupId :** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>` ; les groupes `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` et `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId :** le préfixe `camunda-` devient `fasti-`, le reste est conservé.
- **version :** la version de Fasti BPM Platform (voir [Gestion des versions](#versioning)) à la place de
  `7.24.0`.

Aucune version de ces artefacts n’a encore été publiée sur Maven Central. En attendant, compilez
à partir des sources et mettez les artefacts à disposition dans le dépôt Maven utilisé par votre projet.

Gérez les versions avec la BOM :

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

Artefacts fréquemment utilisés :

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

La bibliothèque FEEL `org.camunda.feel:feel-engine` est une bibliothèque externe qui ne fait pas partie de
ce dépôt ; ses coordonnées restent inchangées.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Bibliothèques tierces qui incluent encore le moteur d’origine

Les packages étant inchangés, le moteur d’origine (`org.camunda.bpm:camunda-engine`)
et `fasti-engine` contiennent des classes portant les mêmes noms. Maven les traite comme deux
bibliothèques indépendantes et n’en élimine aucune. Si une bibliothèque tierce (une extension communautaire,
par exemple) dépend des artefacts d’origine, excluez-les et déclarez les artefacts
Fasti correspondants à leur place :

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

`mvn dependency:tree -Dincludes="org.camunda*"` répertorie les artefacts d’origine encore
présents (`org.camunda.feel:feel-engine` est légitime, ne l’excluez pas).

<a id="startup-check"></a>

### Vérification au démarrage

Lors de sa création, le moteur inspecte le chemin de classes et **refuse de démarrer** s’il trouve
les classes du moteur de processus dans plusieurs bibliothèques ; l’erreur `ENGINE-12021` indique
l’emplacement de chacune d’elles. Le starter Spring Boot effectue la même vérification dès le
démarrage de l’application. Une bibliothèque visible uniquement via le chargeur de classes parent (par exemple, un moteur
partagé du serveur à côté d’un moteur embarqué dans une application web) n’est pas
comptabilisée. Si votre configuration est intentionnelle, la vérification peut être désactivée avec une propriété système :

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limite : si la bibliothèque du moteur d’origine précède `fasti-engine` dans le chemin de classes, le
moteur est construit par les classes de la bibliothèque d’origine, qui ne contiennent pas cette vérification.
Dans ce cas, seules les applications utilisant le starter Spring Boot de Fasti sont arrêtées ; pour
les autres intégrations, examinez les dépendances comme décrit ci-dessus.
