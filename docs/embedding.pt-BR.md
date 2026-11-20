[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | Português (Brasil) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

# Incorporar o Fasti BPM: compatibilidade e coordenadas Maven

O que o Fasti mantém do Camunda 7 e o que muda em relação à 7.24.0, como as versões são numeradas e as coordenadas Maven para aplicações que incorporam o motor. Para executar o Fasti como servidor, consulte [Instalação com Docker](install-docker.pt-BR.md) ou o pacote Tomcat em <https://fasti.dev/download>.

<a id="what-this-is"></a>

## O que é este projeto

Este repositório contém o motor de processos BPMN 2.0 / DMN, a API REST, as aplicações
web (Cockpit, Tasklist, Admin) e as distribuições (Run, Tomcat, WildFly). Ele
parte da tag `7.24.0` do Camunda 7 CE e dá continuidade com correções de bugs e atualizações de
bibliotecas.

O objetivo é a **compatibilidade retroativa** com aplicações executadas no Camunda 7:

- os pacotes `org.camunda.bpm.*`, o namespace BPMN
  `http://camunda.org/schema/1.0/bpmn` e o prefixo de configuração `camunda.bpm.*` são mantidos;
- o esquema de banco de dados do 7.24 é mantido;
- todas as modalidades de implantação são mantidas: Spring Boot, Run, Tomcat, WildFly; `javax` e `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## O que é diferente em relação ao Camunda 7.24.0 CE

- **Segurança:** correções para os problemas divulgados publicamente da linha 7.x (expressões em
  consultas, configuração inicial, acesso a anexos e comentários, permissões do Cockpit, redirecionamento
  aberto), além de proteções adicionais: uma lista de permissões para expressões em consultas e
  filtros, verificações de anexos e comentários dentro do motor, filtros de segurança reforçados,
  cabeçalhos e cookies nas aplicações web.
- **Estabilidade:** eliminação do vazamento de memória quando scripts e expressões DMN são avaliados com
  JavaScript (GraalJS), além de várias outras correções no motor.
- **Plataforma:** Java 17 ou posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21 e muitas outras
  bibliotecas atualizadas para versões com correções de segurança.

Algumas correções alteram os padrões para um comportamento mais seguro; leia "O que difere" no
[guia de migração](migrate-from-camunda-7.pt-BR.md#what-differs) antes de atualizar.

<a id="versioning"></a>

## Versionamento

O Fasti BPM Platform segue o [Versionamento Semântico](https://semver.org/), começando em `1.0.0`.
A linha `1.x` é compatível com aplicações escritas para o Camunda 7.24.

<a id="maven-coordinates"></a>

## Coordenadas Maven

Os artefatos do Fasti BPM Platform têm suas próprias coordenadas Maven. **Os pacotes Java
permanecem `org.camunda.bpm.*`**, portanto o código das aplicações mantém suas importações; o prefixo de configuração `camunda.bpm.*`,
os namespaces BPMN/DMN, o esquema do banco de dados e os nomes dos módulos no
WildFly também permanecem inalterados. No que diz respeito ao Maven, apenas as declarações de dependências
mudam (o caminho da aplicação web muda separadamente, consulte
[Caminho da aplicação web](web-ui.pt-BR.md#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; os grupos `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` e `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** o prefixo `camunda-` passa a ser `fasti-`, o restante é mantido.
- **version:** a versão do Fasti BPM Platform (consulte [Versionamento](#versioning)) em vez de
  `7.24.0`.

Nenhuma versão desses artefatos foi publicada no Maven Central ainda. Até lá, compile
a partir do código-fonte e disponibilize os artefatos no repositório Maven usado pelo seu projeto.

Gerencie as versões com o BOM:

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

Artefatos usados com frequência:

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

A biblioteca FEEL `org.camunda.feel:feel-engine` é uma biblioteca externa que não faz parte
deste repositório; suas coordenadas permanecem como estão.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Bibliotecas de terceiros que ainda trazem o motor original como dependência

Como os pacotes permanecem inalterados, o motor original (`org.camunda.bpm:camunda-engine`)
e `fasti-engine` contêm classes com os mesmos nomes. O Maven os trata como duas bibliotecas
sem relação entre si e não remove nenhuma delas. Se uma biblioteca de terceiros (uma extensão da comunidade,
por exemplo) depender dos artefatos originais, exclua-os e declare os artefatos
Fasti correspondentes em seu lugar:

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

`mvn dependency:tree -Dincludes="org.camunda*"` lista os artefatos originais que ainda estão
presentes (`org.camunda.feel:feel-engine` é legítimo, não o exclua).

<a id="startup-check"></a>

### Verificação na inicialização

Quando é criado, o motor inspeciona o classpath e **se recusa a iniciar** se encontrar
as classes do motor de processos em mais de uma biblioteca; o erro `ENGINE-12021` lista
a localização de cada uma delas. O starter do Spring Boot realiza a mesma verificação assim que a
aplicação inicia. Uma biblioteca visível apenas pelo carregador de classes pai (um motor
compartilhado do servidor ao lado de um motor embarcado em uma aplicação web, por exemplo) não é
contabilizada. Se sua configuração for intencional, a verificação pode ser desativada com uma propriedade de sistema:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limitação: se a biblioteca do motor original preceder `fasti-engine` no classpath, o
motor será construído pelas classes da biblioteca original, que não contêm essa verificação.
Nesse caso, apenas as aplicações que usam o starter do Fasti para Spring Boot são interrompidas; com
outras integrações, revise as dependências conforme descrito acima.
