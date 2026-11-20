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

!!! IMPORTANT when taking over the database of Camunda Run (or of a server
!!! whose processes were deployed through the REST API)

  This Tomcat bundle keeps jobExecutorDeploymentAware=true (the Tomcat
  default of Camunda 7, needed for process applications): the job executor
  only runs jobs (timers, async continuations, retries) of deployments
  registered on this server. Jobs of deployments made by Camunda Run or
  through the REST API do NOT RUN, without any error. Before starting on
  such a database, set in
  server/apache-tomcat-${version.tomcat}/conf/bpm-platform.xml
      <property name="jobExecutorDeploymentAware">false</property>
  Keep it if other (Camunda 7) nodes run process applications with Java
  classes of their own: see the migration guide (docs/migrate-from-camunda-7.md
  in the source repository), "Mixed operation with Camunda 7 nodes".

==================

Contents:

  lib/
        This directory contains the java libraries for application 
        development.

  server/
        This directory contains a preconfigured distribution 
        of Apache Tomcat with Fasti BPM Platform readily 
        installed. 

        run the
            start-camunda.{bat/sh}
        script (or server/apache-tomcat-${version.tomcat}/bin/startup.{bat/sh})
        to start up the server, and shutdown-camunda.{bat/sh} to stop it.
        Java 17 or later is required (21 recommended).

        After starting the server, you can access the
        following web applications:

        http://localhost:8080/fasti         (new web UI: /fasti/app/next/)
        http://localhost:8080/engine-rest

        No user exists after the first start: open http://localhost:8080/fasti/
        and create the first administrator on the setup page (see "First
        administrator" below).

        The classic Cockpit, Tasklist, Admin and Welcome applications
        (/fasti/app/cockpit/, ...) are NOT served by default: they are
        built on AngularJS 1.8.2 and Bootstrap 3, both end of life. Their
        pages redirect to the matching section of the new web UI. To serve
        them (deprecated, they will be removed in a later release), add
        -Dfasti.webapp.classic.enabled=true to CATALINA_OPTS (environment
        variable or bin/setenv.{bat/sh}), set the environment variable
        FASTI_CLASSIC_WEBAPPS=true, or uncomment the context parameter
        fasti.webapp.classic.enabled in webapps/fasti/WEB-INF/web.xml.

        The web applications of Tomcat itself (examples, docs, manager,
        host-manager and the Tomcat default page) are not included;
        http://localhost:8080/ leads to the Fasti welcome page
        (webapps/camunda-welcome).

        The former path http://localhost:8080/camunda redirects to /fasti
        (webapps/camunda). To serve the web applications below /camunda
        again, delete webapps/camunda and rename webapps/fasti to
        webapps/camunda.

  examples/
        camunda-invoice.war, the invoice example, is NOT deployed. To try it,
        copy it to server/apache-tomcat-${version.tomcat}/webapps/. On its
        first start it creates the users demo/demo (administrator),
        john/john, mary/mary and peter/peter unless a user "demo" exists:
        never deploy it on a server that is reachable by others, and delete
        these users after trying it.

  sql/
        This directory contains the create and upgrade sql script
        for the different databases.
        The engine create script contain the engine and history tables.

        Execute the current upgrade script to make the database compatible
        with this release of Fasti BPM Platform.

==================

First administrator:

  The distribution creates no users. When you first open
  http://localhost:8080/fasti/ and the engine has no administrator yet,
  the web UI shows the setup page: enter the user name and the password of
  the first administrator (group camunda-admin). The page closes once an
  administrator exists. Then sign in with that account and create further
  users under Administration. On the database of an existing Camunda 7
  installation, sign in with an existing account. The REST API cannot
  create the first administrator (it requires authentication).

==================

Database:

  H2 is used by default (directory camunda-h2-dbs, relative to the working
  directory at startup), for trying things out only. Only the H2 driver is bundled. To use
  PostgreSQL (the same applies to MySQL, MariaDB, Oracle, SQL Server, DB2):
  1. Copy the JDBC driver jar (e.g. postgresql-42.7.x.jar) to
     server/apache-tomcat-${version.tomcat}/lib/.
  2. In server/apache-tomcat-${version.tomcat}/conf/server.xml, change
     <Resource name="jdbc/ProcessEngine" ...>: driverClassName, url,
     username, password. Example:
         driverClassName="org.postgresql.Driver"
         url="jdbc:postgresql://localhost:5432/fasti"
  3. Restart. On an empty database the tables are created automatically
     (databaseSchemaUpdate in conf/bpm-platform.xml), or run
     sql/create/<db>_engine_*.sql and <db>_identity_*.sql beforehand. A
     Camunda 7.24 database works as it is, without schema changes.
  When taking over the database of Camunda Run (or deploying through REST),
  set jobExecutorDeploymentAware to false in conf/bpm-platform.xml, otherwise
  timers and async jobs of those deployments do not run (see IMPORTANT at
  the top of this file).
  If PostgreSQL reports invalid value for parameter "TimeZone": "Asia/Saigon"
  (Windows), set the environment variable
  CATALINA_OPTS=-Duser.timezone=Asia/Ho_Chi_Minh.

==================

Security:

  The REST API (/engine-rest) REQUIRES AUTHENTICATION (HTTP Basic) by
  default: every client must send the user name and the password of a user
  of the engine, e.g.
      curl -u user:password http://localhost:8080/engine-rest/engine/default/process-definition
  Requests without credentials are answered with 401. Only the list of
  engines (/engine) is open. Unlike Camunda 7.24 (open REST API), existing
  REST clients must send credentials after moving to Fasti.
  To switch authentication off (NOT recommended: every client that can reach
  the server then has unrestricted access): in
  server/apache-tomcat-${version.tomcat}/webapps/engine-rest/WEB-INF/web.xml
  remove (or put into a comment) the "camunda-auth" filter and its
  filter-mapping. OpenID Connect bearer tokens: see below; once enabled,
  requests with a valid token pass the "camunda-auth" filter without a
  password.

  CVE-2026-114938 ("Java4Shell"): on x86-64, use a fixed JVM (Temurin/Oracle
  17.0.21.1, 21.0.13.1, 25.0.5.1 or later). Until then add
  -XX:-UseBASE64Intrinsics to CATALINA_OPTS (environment variable, or
  server/apache-tomcat-${version.tomcat}/bin/setenv.{bat/sh}). The engine logs
  the warning ENGINE-12023 while the JVM is affected.

==================

Single sign-on (OpenID Connect):

  Copy server/apache-tomcat-${version.tomcat}/conf/fasti-oidc.properties.example
  to fasti-oidc.properties, fill in the provider and the client, and restart.
  Without that file users sign in with a password as before. The same file
  enables bearer tokens on /engine-rest (fasti.security.oidc.rest.*); HTTP
  Basic keeps working unless fasti.security.oidc.rest.basic-enabled=false.

==================

Tieng Viet (Vietnamese):

!!! QUAN TRONG khi tiep quan co so du lieu cua Camunda Run (hoac cua mot
!!! may chu ma process duoc deploy qua REST API)

  Ban Tomcat nay giu jobExecutorDeploymentAware=true (mac dinh cua Tomcat
  trong Camunda 7, can cho process application): job executor chi chay job
  (timer, async, retry) cua deployment da dang ky tren may chu nay. Job cua
  deployment tao boi Camunda Run hoac qua REST API se KHONG CHAY va khong bao
  loi. Truoc khi khoi dong tren co so du lieu do, trong
  server/apache-tomcat-${version.tomcat}/conf/bpm-platform.xml dat
      <property name="jobExecutorDeploymentAware">false</property>
  Khong doi neu cac node khac (Camunda 7) dung process application co lop
  Java rieng: xem huong dan chuyen doi (docs/migrate-from-camunda-7.vi.md trong
  kho ma nguon), muc "Chay lan voi node Camunda 7".

Nguoi quan tri dau tien:

  Ban phan phoi khong tao san nguoi dung nao. Lan dau mo
  http://localhost:8080/fasti/ khi engine chua co quan tri vien, giao dien
  web hien trang thiet lap: nhap ten dang nhap va mat khau cua quan tri vien
  dau tien (nhom camunda-admin). Trang nay tu dong khi da co quan tri vien.
  Sau do dang nhap bang tai khoan nay, tao them nguoi dung trong Quan tri.
  Tiep quan co so du lieu cua Camunda 7 thi dang nhap bang tai khoan co san.
  REST API khong tao duoc quan tri vien dau tien (no can dang nhap).

Co so du lieu:

  Mac dinh dung H2 (thu muc camunda-h2-dbs, tinh tu thu muc hien hanh
  khi khoi dong), chi de thu. Chi co driver H2 di kem. Doi sang PostgreSQL (tuong tu cho
  MySQL, MariaDB, Oracle, SQL Server, DB2):
  1. Chep jar driver JDBC (vd. postgresql-42.7.x.jar) vao
     server/apache-tomcat-${version.tomcat}/lib/.
  2. Trong server/apache-tomcat-${version.tomcat}/conf/server.xml, sua
     <Resource name="jdbc/ProcessEngine" ...>: driverClassName, url,
     username, password.
  3. Khoi dong lai. Co so du lieu trong: bang duoc tao tu dong
     (databaseSchemaUpdate trong conf/bpm-platform.xml) hoac chay truoc
     sql/create/<db>_engine_*.sql va <db>_identity_*.sql. Co so du lieu cua
     Camunda 7.24 dung duoc ngay, khong doi schema.
  Khi tiep quan co so du lieu cua Camunda Run (hoac deploy qua REST), dat
  jobExecutorDeploymentAware thanh false trong conf/bpm-platform.xml, neu
  khong timer va job async cua cac deployment do se khong chay (xem muc
  QUAN TRONG o dau tep).
  Neu PostgreSQL bao invalid value for parameter "TimeZone": "Asia/Saigon"
  (Windows), dat bien moi truong CATALINA_OPTS=-Duser.timezone=Asia/Ho_Chi_Minh.

Bao mat:

  REST API (/engine-rest) YEU CAU DANG NHAP (HTTP Basic) theo mac dinh: moi
  client phai gui ten va mat khau cua mot nguoi dung cua engine, vd.
      curl -u nguoidung:matkhau http://localhost:8080/engine-rest/engine/default/process-definition
  Khong co thong tin dang nhap thi tra 401. Chi danh sach engine (/engine)
  la mo. Khac voi Camunda 7.24 (REST mo): client REST cu phai gui thong tin
  dang nhap sau khi chuyen sang Fasti.
  Tat xac thuc (KHONG khuyen nghi; ai goi duoc may chu deu co toan quyen):
  trong server/apache-tomcat-${version.tomcat}/webapps/engine-rest/WEB-INF/web.xml
  xoa (hoac dat vao chu thich) bo loc "camunda-auth" va filter-mapping cua no.
  Bearer token OpenID Connect: xem phan duoi; khi bat, request co token hop le
  di qua bo loc "camunda-auth" ma khong can mat khau.

Dang nhap mot lan (OpenID Connect):

  Sao chep server/apache-tomcat-${version.tomcat}/conf/fasti-oidc.properties.example
  thanh fasti-oidc.properties, dien nha cung cap va client, roi khoi dong lai.
  Khong co tep nay thi nguoi dung dang nhap bang mat khau nhu truoc.

==================

Fasti BPM Platform version: ${project.version}
Apache Tomcat Server version: ${version.tomcat}

=================
