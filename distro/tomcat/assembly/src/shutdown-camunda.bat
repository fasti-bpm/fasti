@echo off

set "CATALINA_HOME=%~dp0server\apache-tomcat-${version.tomcat}"

cd /d "%CATALINA_HOME%\bin"
start shutdown.bat
