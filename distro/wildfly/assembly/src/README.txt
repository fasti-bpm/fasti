This is a distribution of

       Fasti BPM Platform v${project.version}

an independent fork of Camunda 7 Community Edition 7.24.0. Fasti BPM is
not affiliated with, sponsored by or endorsed by Camunda Services GmbH.
"Camunda" is a trademark of Camunda Services GmbH. Most of the Camunda 7
documentation applies:
       https://docs.camunda.org/manual/7.24/

Fasti BPM Platform is released under the Apache License 2.0.

License information can be found in the LICENSE file.

Fasti BPM Platform includes libraries developed by third
parties. For license and attribution notices for these libraries,
please refer to the documentation that accompanies this distribution
(see the LICENSE_BOOK-${project.version} file).

The packaged Wildfly Application Server is licensed under
the LGPL license.

==================

Contents:

  lib/
        This directory contains the java libraries for application 
        development.

  modules/
        This directory contains additional modules for Wildfly Application
        Server. You can use these modules to patch a vanilla distribution
        of Wildfly Application Server.

  server/
        This directory contains a preconfigured distribution 
        of Wildfly Application Server with Fasti BPM Platform readily
        installed.

        run the
          server/wildfly-${version.wildfly}/bin/standalone.{bat/sh}
        script to start up the the server.

        After starting the server, you can access the 
        following web applications:

        http://localhost:8080/fasti
        http://localhost:8080/engine-rest

        The classic Cockpit, Tasklist, Admin and Welcome applications
        (/fasti/app/cockpit/, ...) are NOT served by default: they are
        built on AngularJS 1.8.2 and Bootstrap 3, both end of life. Their
        pages redirect to the matching section of the new web UI. To serve
        them (deprecated), start the server with
        -Dfasti.webapp.classic.enabled=true (JAVA_OPTS) or set the
        environment variable FASTI_CLASSIC_WEBAPPS=true.

        The former path http://localhost:8080/camunda redirects to /fasti
        (standalone/deployments/camunda.war). To serve the web applications
        below /camunda again, delete camunda.war and camunda.war.dodeploy
        and set <context-root>/camunda</context-root> in
        WEB-INF/jboss-web.xml of the web application WAR.

    examples/
        camunda-invoice.war, the invoice example, is NOT deployed. To try it,
        copy it to server/wildfly-${version.wildfly}/standalone/deployments/.
        On its first start it creates the users demo/demo (administrator),
        john/john, mary/mary and peter/peter unless a user "demo" exists:
        never deploy it on a server that is reachable by others, and delete
        these users after trying it.

        No user exists after the first start: open http://localhost:8080/fasti/
        and create the first administrator (group camunda-admin) on the setup
        page that the web UI shows as long as there is no administrator.

    sql/
        This directory contains the create and upgrade sql script
        for the different databases.
        The engine create script contain the engine and history tables.

        Execute the current upgrade script to make the database compatible
        with this release of Fasti BPM Platform.

==================

!!! IMPORTANT: taking over the database of Camunda Run

  The engine keeps jobExecutorDeploymentAware=true: jobs (timers, async
  continuations, retries) of deployments made by Camunda Run or through the
  REST API do NOT RUN, without any error. Before starting on such a
  database, set this property to false for the engine in
  standalone/configuration/standalone.xml.

==================

Security:

  The REST API (/engine-rest) REQUIRES AUTHENTICATION (HTTP Basic) by
  default: every client must send the user name and the password of a user
  of the engine (curl -u user:password ...); requests without credentials are
  answered with 401. Only the list of engines (/engine) is open. Unlike
  Camunda 7.24 (open REST API), existing REST clients must send credentials.
  To switch authentication off (NOT recommended: every client that can reach
  the server then has unrestricted access), remove the "camunda-auth" filter
  and its filter-mapping from WEB-INF/web.xml of
  standalone/deployments/fasti-engine-rest-*.war.

==================

Single sign-on (OpenID Connect):

  Copy server/wildfly-${version.wildfly}/standalone/configuration/fasti-oidc.properties.example
  to fasti-oidc.properties, fill in the provider and the client, and restart.
  Without that file users sign in with a password as before. The same file
  enables bearer tokens on /engine-rest (fasti.security.oidc.rest.*); HTTP
  Basic keeps working unless fasti.security.oidc.rest.basic-enabled=false.

==================

Tieng Viet (Vietnamese):

!!! QUAN TRONG: tiep quan co so du lieu cua Camunda Run

  Engine giu jobExecutorDeploymentAware=true: job (timer, async, retry) cua
  deployment tao boi Camunda Run hoac qua REST API KHONG CHAY va khong bao
  loi. Truoc khi khoi dong tren co so du lieu do, dat property nay thanh
  false cho engine trong standalone/configuration/standalone.xml.

Bao mat:

  REST API (/engine-rest) YEU CAU DANG NHAP (HTTP Basic) theo mac dinh: moi
  client phai gui ten va mat khau cua mot nguoi dung cua engine
  (curl -u nguoidung:matkhau ...); khong co thi tra 401. Chi danh sach engine
  (/engine) la mo. Khac voi Camunda 7.24 (REST mo): client REST cu phai gui
  thong tin dang nhap. Tat xac thuc (KHONG khuyen nghi): trong
  WEB-INF/web.xml cua standalone/deployments/fasti-engine-rest-*.war xoa bo loc
  "camunda-auth" va filter-mapping cua no.

Dang nhap mot lan (OpenID Connect):

  Sao chep server/wildfly-${version.wildfly}/standalone/configuration/fasti-oidc.properties.example
  thanh fasti-oidc.properties, dien nha cung cap va client, roi khoi dong lai.
  Khong co tep nay thi nguoi dung dang nhap bang mat khau nhu truoc.

==================

Fasti BPM Platform version: ${project.version}
Wildfly Application Server version: ${version.wildfly}

=================
