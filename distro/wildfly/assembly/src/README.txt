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

        The former path http://localhost:8080/camunda redirects to /fasti
        (standalone/deployments/camunda.war). To serve the web applications
        below /camunda again, delete camunda.war and camunda.war.dodeploy
        and set <context-root>/camunda</context-root> in
        WEB-INF/jboss-web.xml of the web application WAR.

    sql/
        This directory contains the create and upgrade sql script
        for the different databases.
        The engine create script contain the engine and history tables.

        Execute the current upgrade script to make the database compatible
        with this release of Fasti BPM Platform.

==================

Dang nhap mot lan (OpenID Connect) / Single sign-on (OpenID Connect):

  Sao chep server/wildfly-${version.wildfly}/standalone/configuration/fasti-oidc.properties.example
  thanh fasti-oidc.properties, dien nha cung cap va client, roi khoi dong lai.
  Khong co tep nay thi nguoi dung dang nhap bang mat khau nhu truoc.

  Copy server/wildfly-${version.wildfly}/standalone/configuration/fasti-oidc.properties.example
  to fasti-oidc.properties, fill in the provider and the client, and restart.
  Without that file users sign in with a password as before. The same file
  enables bearer tokens on /engine-rest (fasti.security.oidc.rest.*).

==================

Fasti BPM Platform version: ${project.version}
Wildfly Application Server version: ${version.wildfly}

=================
