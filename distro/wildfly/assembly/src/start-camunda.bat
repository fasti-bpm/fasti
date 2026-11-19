@echo off

set "JBOSS_HOME=%CD%\server\wildfly-${version.wildfly}"

echo "starting Fasti BPM Platform ${project.version} on WildFly ${version.wildfly}"

cd server\wildfly-${version.wildfly}\bin\
start standalone.bat

ping -n 5 localhost > nul
start http://localhost:8080/camunda-welcome/index.html
 