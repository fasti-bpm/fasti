#!/bin/sh

export CATALINA_HOME="$(dirname "$0")/server/apache-tomcat-${version.tomcat}"

UNAME=`which uname`
if [ -n "$UNAME" -a "`$UNAME`" = "Darwin" ]
then
	BROWSERS="open"
else
	BROWSERS="xdg-open gnome-www-browser x-www-browser firefox chromium chromium-browser google-chrome"
fi

echo "starting Fasti BPM Platform ${project.version} on Apache Tomcat ${version.tomcat}"

if [ -z "$BROWSER" ]; then
  for executable in $BROWSERS; do
    BROWSER=`which $executable 2> /dev/null`
    if [ -n "$BROWSER" ]; then
      break;
    fi
  done
fi

if [ -z "$BROWSER" ]; then
  (sleep 15; printf "Could not find a web browser. Open http://localhost:8080/camunda-welcome/index.html\n";) &
else
  (sleep 5; $BROWSER "http://localhost:8080/camunda-welcome/index.html";) &
fi

/bin/sh "$(dirname "$0")/server/apache-tomcat-${version.tomcat}/bin/startup.sh"
