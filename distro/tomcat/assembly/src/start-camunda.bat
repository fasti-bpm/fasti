@echo off

set "CATALINA_HOME=%~dp0server\apache-tomcat-${version.tomcat}"

echo "starting Fasti BPM Platform ${project.version} on Apache Tomcat ${version.tomcat}"

cd /d "%CATALINA_HOME%\bin"
start startup.bat

ping -n 5 localhost > nul
start http://localhost:8080/camunda-welcome/index.html
