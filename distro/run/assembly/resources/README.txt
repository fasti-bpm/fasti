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

The packaged Apache Tomcat server is licensed under 
the Apache License v2.0 license.

==================

Contents:

  /
        The root directory contains two start scripts. One for Windows (.bat)
        and one for Linux/Mac (.sh). After executing it, you can access the 
        following web applications:

        webapps: http://localhost:8080/fasti/
        rest: http://localhost:8080/engine-rest/

        The former path http://localhost:8080/camunda/ redirects to /fasti/.
        To serve the web applications below /camunda again, set
        camunda.bpm.webapp.application-path: /camunda in
        configuration/default.yml (the redirect then turns off by itself);
        fasti.webapp.legacy-path-redirect: false turns off only the redirect.

  internal/
        This directory contains the Java application and optional components
        that Fasti BPM Run consists of.

  configuration/
        This directory contains all resources to configure the distro.
        Find a detailed guide on how to use this directory on the following
        documentation pages:
        https://docs.camunda.org/manual/latest/installation/camunda-bpm-run/
        https://docs.camunda.org/manual/latest/user-guide/camunda-bpm-run/

==================

Fasti BPM Platform version: ${project.version}

=================
