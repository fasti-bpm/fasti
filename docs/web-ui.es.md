[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | Español | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

# La interfaz web de Fasti

Por qué la nueva interfaz web tiene estas funciones y en qué ruta se sirven las aplicaciones web. Un recorrido por la interfaz: <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## Una interfaz creada a partir de quejas reales de los usuarios

Las funciones de la nueva interfaz web se eligieron a partir de lo que los usuarios de Camunda 7 han publicado en el
foro de Camunda y en GitHub, por ejemplo:

| Lo que pedían los usuarios | Fuente | En Fasti |
|---|---|---|
| Buscar instancias por varias variables, con OR | foro [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Constructor de condiciones con varias variables, AND/OR |
| Ver los comentarios en cada paso de un caso | foro [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Cronología del caso en la tarea; comentarios en la propia instancia del proceso |
| Ordenar las tareas por variables, las más antiguas primero | foro [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Ordenación por variables, fechas de vencimiento y urgencia |
| Delegación, suplentes durante las ausencias | foro [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Delegación por tarea y [suplentes durante las ausencias](absence.md) con traspaso |
| Clave de negocio y variables al iniciar un proceso sin formulario | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Sí |
| Descargar el XML BPMN desplegado | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Sí |
| Etiquetas coherentes, varios idiomas | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 idiomas; dentro de los editores de BPMN, DMN y formularios, las etiquetas integradas solo están en inglés y vietnamita |

<a id="web-application-path-fasti"></a>

## Ruta de la aplicación web: `/fasti`

Cockpit, Tasklist, Admin y la nueva interfaz web se sirven bajo `/fasti` (antes `/camunda`)
en todas las distribuciones: `http://localhost:8080/fasti/` abre la nueva interfaz
(`/fasti/app/next/default/`), las aplicaciones clásicas se encuentran en `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` y `/fasti/app/admin/...`. La API REST permanece en `/engine-rest`.

**Las aplicaciones clásicas están obsoletas.** Las aplicaciones clásicas Cockpit, Tasklist, Admin y Welcome
se basan en AngularJS 1.8.2 y Bootstrap 3, ambos fuera de soporte. **La imagen del contenedor no
las sirve de forma predeterminada** (cambio incompatible): sus páginas y los marcadores antiguos redirigen a la sección correspondiente de la nueva interfaz, y las solicitudes de sus archivos devuelven `404`; `FASTI_CLASSIC_WEBAPPS=true` las
reactiva (con una advertencia), consulte [distro/docker/README.md](../distro/docker/README.md). Los paquetes
de Tomcat y WildFly tampoco las sirven de forma predeterminada (reactívelas con
`-Dfasti.webapp.classic.enabled=true` o `FASTI_CLASSIC_WEBAPPS=true`). El zip de Run y el starter de Spring
Boot siguen sirviéndolas como antes, para que las instalaciones en funcionamiento no dejen de funcionar; desactívelas
con `fasti.webapp.classic.enabled: false`. Las aplicaciones clásicas se eliminarán en una
versión posterior.

La ruta antigua sigue funcionando: todas las solicitudes a `/camunda/...` se redirigen a la misma ruta
bajo `/fasti/...`, conservando el resto de la ruta y la cadena de consulta. GET y HEAD reciben
`301`; todos los demás métodos (POST, PUT, DELETE, por ejemplo, clientes antiguos que llaman a `/camunda/api/...`)
reciben `308`, para que los clientes repitan la solicitud con el mismo método y cuerpo. Las redirecciones
incluyen `Cache-Control: no-store`, por lo que volver a `/camunda` surte efecto de inmediato.

Para restaurar `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (en ese caso no hay redirección).
  Para desactivar solo la redirección: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** elimine `webapps/camunda` (la aplicación de redirección) y cambie el nombre de
  `webapps/fasti` a `webapps/camunda`.
- **WildFly:** elimine `standalone/deployments/camunda.war` (y `camunda.war.dodeploy`) y
  configure `<context-root>/camunda</context-root>` en `WEB-INF/jboss-web.xml` del WAR de la
  aplicación web.

**Proxies inversos:** si un proxy solo reenvía `/camunda/` (o reescribe rutas, configura rutas de cookies,
CSP o comprobaciones de estado para `/camunda`), añada `/fasti/` o migre por completo a `/fasti/`.
La cookie `XSRF-TOKEN` (y `JSESSIONID` en Tomcat y WildFly) ahora tiene la ruta `/fasti`,
por lo que los usuarios deben volver a iniciar sesión. Los marcadores y los enlaces antiguos siguen funcionando mediante la redirección.
