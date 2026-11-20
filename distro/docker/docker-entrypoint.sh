#!/bin/sh
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Starts Fasti BPM Run in the container. Accepts the environment variables of the former
# official Camunda 7 Run container image (DB_DRIVER, DB_URL, DB_USERNAME, DB_PASSWORD, WAIT_FOR,
# DEBUG, JMX_PROMETHEUS, ...) and FASTI_* aliases, and the arguments of its start script
# (--webapps, --rest, --example, --oauth2, --production).

set -eu
# no pathname expansion anywhere: options and paths are taken literally
set -f

FASTI_HOME=${FASTI_HOME:-/fasti}
cd "$FASTI_HOME"

log() { echo "fasti-entrypoint: $*"; }
fail() { echo "fasti-entrypoint: ERROR: $*" >&2; exit 1; }

# first non-empty of the given variable names
pick() {
  for name in "$@"; do
    eval "value=\${$name:-}"
    if [ -n "$value" ]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 0
}

# value of NAME, or the content of the file named by NAME_FILE (Docker/Kubernetes secrets)
secret() {
  for name in "$@"; do
    eval "value=\${$name:-}"
    if [ -n "$value" ]; then
      printf '%s' "$value"
      return 0
    fi
    eval "file=\${${name}_FILE:-}"
    if [ -n "$file" ]; then
      [ -r "$file" ] || fail "${name}_FILE names $file, which cannot be read"
      cat "$file"
      return 0
    fi
  done
  return 0
}

# whether a jar whose name starts with $1 is in one of the library directories
has_jar() {
  for dir in "$FASTI_HOME/configuration/userlib" "$FASTI_HOME/configuration/drivers" "${FASTI_EXTRA_LIB:-}" /camunda/configuration/userlib; do
    [ -n "$dir" ] && [ -d "$dir" ] || continue
    if [ -n "$(find "$dir" -maxdepth 1 -name "$1*.jar" 2>/dev/null | head -n 1)" ]; then
      return 0
    fi
  done
  return 1
}

is_true() {
  case "$(printf '%s' "${1:-}" | tr 'A-Z' 'a-z')" in
    true|yes|1|on) return 0 ;;
    *) return 1 ;;
  esac
}

ARGS=""
USER_ARGS=""
# quotes one argument for the final eval
quote() {
  escaped=$(printf '%s' "$1" | sed "s/'/'\\\\''/g")
  printf "'%s'" "$escaped"
}
# appends one argument for the application derived from the environment
add_arg() {
  ARGS="$ARGS $(quote "$1")"
}

# --- arguments of the start script ------------------------------------------------------------

webapps=false; rest=false; example=false; oauth2=false; production=false; chosen=false
for arg in "$@"; do
  case "$arg" in
    ./camunda.sh|camunda.sh|./fasti.sh|fasti.sh|start) ;;   # command of the former image
    --webapps) webapps=true; chosen=true ;;
    --rest) rest=true; chosen=true ;;
    --example) example=true; chosen=true ;;
    --oauth2) oauth2=true; chosen=true ;;
    --swaggerui) log "--swaggerui is not available in Fasti BPM and is ignored" ;;
    --production) production=true ;;
    --detached) log "--detached is ignored in a container" ;;
    *) USER_ARGS="$USER_ARGS $(quote "$arg")" ;;   # passed on after the derived ones
  esac
done
if [ "$chosen" = false ]; then
  webapps=true; rest=true
fi

LOADER_PATH="$FASTI_HOME/configuration/userlib/,$FASTI_HOME/configuration/drivers/,$FASTI_HOME/configuration/keystore/"
[ "$webapps" = true ] && LOADER_PATH="$FASTI_HOME/internal/webapps/,$LOADER_PATH"
[ "$rest" = true ] && LOADER_PATH="$FASTI_HOME/internal/rest/,$LOADER_PATH"
[ "$oauth2" = true ] && LOADER_PATH="$FASTI_HOME/internal/oauth2/,$LOADER_PATH"
if [ "$example" = true ]; then
  LOADER_PATH="$FASTI_HOME/internal/example/,$LOADER_PATH"
  add_arg "--camunda.bpm.run.example.enabled=true"
fi
# drivers and plugins mounted where the former image expected them
for dir in "${FASTI_EXTRA_LIB:-}" /camunda/configuration/userlib; do
  if [ -n "$dir" ] && [ -d "$dir" ]; then
    LOADER_PATH="$LOADER_PATH,$dir/"
    log "additional libraries from $dir"
  fi
done

# --- configuration files ----------------------------------------------------------------------

CONFIG="file:$FASTI_HOME/configuration/docker.yml"
if [ "$production" = true ]; then
  CONFIG="$CONFIG,file:$FASTI_HOME/configuration/docker-production.yml"
fi
user_config=false
# files mounted where Run or the former image read them
for file in "$FASTI_HOME/configuration/default.yml" /camunda/configuration/default.yml \
            /camunda/configuration/production.yml; do
  if [ -f "$file" ]; then
    case "$file" in
      */production.yml) [ "$production" = true ] || continue ;;
    esac
    CONFIG="$CONFIG,file:$file"
    user_config=true
    log "configuration from $file"
  fi
done
if [ -n "${FASTI_CONFIG:-}" ]; then
  old_ifs=$IFS; IFS=,
  for file in $FASTI_CONFIG; do
    [ -f "$file" ] || fail "FASTI_CONFIG names $file, which does not exist"
    CONFIG="$CONFIG,file:$file"
    user_config=true
    log "configuration from $file"
  done
  IFS=$old_ifs
fi

# --- database ---------------------------------------------------------------------------------

db_url=$(pick FASTI_DB_URL DB_URL)
db_driver=$(pick FASTI_DB_DRIVER DB_DRIVER)
db_user=$(secret FASTI_DB_USERNAME DB_USERNAME)
db_password=$(secret FASTI_DB_PASSWORD DB_PASSWORD)
schema_update=$(pick FASTI_DB_SCHEMA_UPDATE)
demo_h2=false

if is_true "${SKIP_DB_CONFIG:-}"; then
  log "SKIP_DB_CONFIG is set: the datasource comes from the configuration files or SPRING_DATASOURCE_*"
elif [ -n "$db_url" ]; then
  # MariaDB Connector/J may be left out of the image (see README); MySQL Connector/J speaks to
  # MariaDB as well, with its own URL options. Only switch if the user did not set the
  # datasource explicitly and no MariaDB driver is present.
  if [ "$db_driver" = "org.mariadb.jdbc.Driver" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]       && [ -z "${SPRING_DATASOURCE_DRIVER_CLASS_NAME:-}" ] && ! has_jar mariadb-java-client; then
    if is_true "${FASTI_MARIADB_USE_MYSQL_DRIVER:-true}"; then
      db_driver="com.mysql.cj.jdbc.Driver"
      case "$db_url" in
        jdbc:mariadb:*) db_url="jdbc:mysql:${db_url#jdbc:mariadb:}" ;;
      esac
      log "WARNING: no MariaDB driver found, connecting with MySQL Connector/J (jdbc:mysql:). Add mariadb-java-client to configuration/userlib to keep the MariaDB driver; FASTI_MARIADB_USE_MYSQL_DRIVER=false disables this switch"
    else
      fail "DB_DRIVER is org.mariadb.jdbc.Driver, but no mariadb-java-client jar is in the image or in FASTI_EXTRA_LIB"
    fi
  fi
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-$db_url}"
  [ -n "$db_driver" ] && export SPRING_DATASOURCE_DRIVER_CLASS_NAME="${SPRING_DATASOURCE_DRIVER_CLASS_NAME:-$db_driver}"
  [ -n "$db_user" ] && export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$db_user}"
  [ -n "$db_password" ] && export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$db_password}"
elif [ -z "${SPRING_DATASOURCE_URL:-}" ] && [ "$user_config" = false ]; then
  [ "$production" = false ] || fail "--production needs a database (DB_URL/FASTI_DB_URL or SPRING_DATASOURCE_URL); the built-in H2 database is for trying out only"
  demo_h2=true
  mkdir -p "$FASTI_HOME/data" 2>/dev/null || true
  export SPRING_DATASOURCE_URL="jdbc:h2:$FASTI_HOME/data/process-engine;TRACE_LEVEL_FILE=0;DB_CLOSE_ON_EXIT=FALSE"
  export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
  export SPRING_DATASOURCE_USERNAME=sa
  export SPRING_DATASOURCE_PASSWORD=sa
  log "no database configured: using the H2 database in $FASTI_HOME/data (for trying out only)"
fi

# connection pool settings of the former image (Tomcat/WildFly pool names) mapped to HikariCP
max_active=$(pick FASTI_DB_CONN_MAXACTIVE DB_CONN_MAXACTIVE)
min_idle=$(pick FASTI_DB_CONN_MINIDLE DB_CONN_MINIDLE)
[ -n "$max_active" ] && add_arg "--spring.datasource.hikari.maximum-pool-size=$max_active"
[ -n "$min_idle" ] && add_arg "--spring.datasource.hikari.minimum-idle=$min_idle"
[ -n "${DB_CONN_MAXIDLE:-}" ] && log "DB_CONN_MAXIDLE has no counterpart in HikariCP and is ignored"
validation_query=$(pick FASTI_DB_VALIDATION_QUERY DB_VALIDATION_QUERY)
if [ -n "$validation_query" ] && is_true "$(pick FASTI_DB_VALIDATE_ON_BORROW DB_VALIDATE_ON_BORROW)"; then
  add_arg "--spring.datasource.hikari.connection-test-query=$validation_query"
fi

# schema: created only on request, or for the built-in H2 demo database
if [ -z "$schema_update" ] && [ "$demo_h2" = true ]; then
  schema_update=true
fi
if [ -n "$schema_update" ]; then
  add_arg "--camunda.bpm.database.schema-update=$schema_update"
fi
[ -n "${FASTI_DB_SCHEMA:-}" ] && add_arg "--camunda.bpm.database.schema-name=$FASTI_DB_SCHEMA"
[ -n "${FASTI_DB_TABLE_PREFIX:-}" ] && add_arg "--camunda.bpm.database.table-prefix=$FASTI_DB_TABLE_PREFIX"
case "${FASTI_SCHEMA_VERSION_CHECK:-enforce}" in
  enforce) ;;
  warn) add_arg "--camunda.bpm.generic-properties.properties.enforceDatabaseSchemaVersion=false" ;;
  *) fail "FASTI_SCHEMA_VERSION_CHECK must be enforce or warn" ;;
esac

# --- engine -----------------------------------------------------------------------------------

[ -n "${FASTI_HISTORY_LEVEL:-}" ] && add_arg "--camunda.bpm.history-level=$FASTI_HISTORY_LEVEL"
[ -n "${FASTI_JOB_EXECUTOR_ENABLED:-}" ] && add_arg "--camunda.bpm.job-execution.enabled=$FASTI_JOB_EXECUTOR_ENABLED"
[ -n "${FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE:-}" ] && add_arg "--camunda.bpm.job-execution.deployment-aware=$FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE"
if [ -n "${FASTI_CORS_ALLOWED_ORIGINS:-}" ]; then
  add_arg "--camunda.bpm.run.cors.enabled=true"
  add_arg "--camunda.bpm.run.cors.allowed-origins=$FASTI_CORS_ALLOWED_ORIGINS"
fi
# --- security mode ----------------------------------------------------------------------------
# REST authentication and authorization are on in every mode (docker.yml). The open mode of
# Camunda Run is only available through the loudly named FASTI_INSECURE_DEMO opt-in.
if is_true "${FASTI_INSECURE_DEMO:-}"; then
  [ "$production" = false ] || fail "FASTI_INSECURE_DEMO cannot be combined with --production"
  add_arg "--fasti.security.enforce-auth=false"
  add_arg "--camunda.bpm.run.auth.enabled=false"
  add_arg "--camunda.bpm.authorization.enabled=false"
  for line in     "#################################################################################"     "#  WARNING: FASTI_INSECURE_DEMO=true                                              #"     "#  The REST API at /engine-rest accepts requests WITHOUT authentication and       #"     "#  authorization checks are OFF. Anyone who reaches this port can create admin    #"     "#  users and deploy scripts that run code on this host. For local demos only:     #"     "#  publish the port on 127.0.0.1 only and never point this mode at real data.     #"     "#################################################################################"; do
    echo "fasti-entrypoint: $line" >&2
  done
elif [ -n "${FASTI_REST_AUTH:-}" ]; then
  is_true "$FASTI_REST_AUTH" || fail "FASTI_REST_AUTH=$FASTI_REST_AUTH would turn REST authentication off; use FASTI_INSECURE_DEMO=true for an open demo"
fi
[ -n "${FASTI_WEBAPP_PATH:-}" ] && add_arg "--camunda.bpm.webapp.application-path=$FASTI_WEBAPP_PATH"
[ -n "${FASTI_LEGACY_PATH_REDIRECT:-}" ] && add_arg "--fasti.webapp.legacy-path-redirect=$FASTI_LEGACY_PATH_REDIRECT"
# the classic web applications (AngularJS 1.8.2, end of life) are off in the image (docker.yml);
# the application logs a deprecation warning when they are turned back on
if [ -n "${FASTI_CLASSIC_WEBAPPS:-}" ]; then
  if is_true "$FASTI_CLASSIC_WEBAPPS"; then
    add_arg "--fasti.webapp.classic.enabled=true"
    log "WARNING: FASTI_CLASSIC_WEBAPPS=true: the classic Cockpit, Tasklist and Admin are served; they are deprecated and built on the end-of-life AngularJS 1.8.2 and Bootstrap 3"
  else
    add_arg "--fasti.webapp.classic.enabled=false"
  fi
fi
admin_password=$(secret FASTI_ADMIN_PASSWORD)
if [ -n "$admin_password" ] && [ -z "${FASTI_ADMIN_USER:-}" ]; then
  fail "FASTI_ADMIN_PASSWORD is set, but FASTI_ADMIN_USER is not"
fi
if is_true "${FASTI_INITIAL_SETUP:-}"; then
  add_arg "--camunda.bpm.webapp.initial-setup-enabled=true"
  log "WARNING: FASTI_INITIAL_SETUP=true: while no administrator exists, anyone who reaches the web application can create one"
fi
if [ -n "${FASTI_ADMIN_USER:-}" ]; then
  [ -n "$admin_password" ] || fail "FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD (or FASTI_ADMIN_PASSWORD_FILE)"
  # passed as environment variables, not as arguments: arguments are visible to every process
  # through /proc/<pid>/cmdline and ps
  export CAMUNDA_BPM_ADMINUSER_ID="$FASTI_ADMIN_USER"
  export CAMUNDA_BPM_ADMINUSER_PASSWORD="$admin_password"
fi
unset FASTI_ADMIN_PASSWORD admin_password 2>/dev/null || true
if [ "$demo_h2" = true ] && [ -z "${FASTI_ADMIN_USER:-}" ] && ! is_true "${FASTI_INSECURE_DEMO:-}"; then
  log "the H2 demo database starts empty and authentication is on: set FASTI_ADMIN_USER and FASTI_ADMIN_PASSWORD, or FASTI_INITIAL_SETUP=true to create the first admin on the setup page of the web application"
fi
if [ -n "${FASTI_PORT:-}" ]; then
  case "$FASTI_PORT" in
    *[!0-9]*)
      # Kubernetes sets FASTI_PORT=tcp://<ip>:<port> for a Service named "fasti"
      log "WARNING: ignoring FASTI_PORT=$FASTI_PORT: not a port number; the server listens on 8080"
      ;;
    *) add_arg "--server.port=$FASTI_PORT" ;;
  esac
fi
if [ "$production" = true ] && [ "$oauth2" = true ]; then
  # SameSite=Strict would drop the session on the redirect back from the identity provider
  add_arg "--server.servlet.session.cookie.same-site=lax"
fi

# --- JVM --------------------------------------------------------------------------------------

JVM_OPTS="-XX:MaxRAMPercentage=${FASTI_MAX_RAM_PERCENTAGE:-75} -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
# CVE-2026-114938 ("Java4Shell"): defence in depth on top of the fixed JRE. Turns off the HotSpot
# Base64 intrinsic that contains the flaw; Base64 then runs on the plain Java code, which is slower
# but correct. FASTI_BASE64_INTRINSICS=true keeps the intrinsic.
if [ "${FASTI_BASE64_INTRINSICS:-false}" != "true" ]; then
  JVM_OPTS="$JVM_OPTS -XX:+UnlockDiagnosticVMOptions -XX:-UseBASE64Intrinsics"
fi
if is_true "${FASTI_APPCDS:-}"; then
  # class data sharing: the first run writes the archive on a clean shutdown, later runs of the
  # same image start faster; an archive that does not match is ignored by the JVM (see README)
  JVM_OPTS="$JVM_OPTS -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=${FASTI_APPCDS_FILE:-$FASTI_HOME/data/fasti-appcds.jsa}"
fi
if [ -n "${TZ:-}" ]; then
  JVM_OPTS="$JVM_OPTS -Duser.timezone=$TZ"
fi
if is_true "${DEBUG:-}" || is_true "${FASTI_DEBUG:-}"; then
  # JDWP has no authentication: whoever reaches the port controls the JVM
  [ "$production" = false ] || fail "DEBUG/FASTI_DEBUG cannot be combined with --production"
  log "WARNING: remote debugging is unauthenticated; publish the debug port on 127.0.0.1 only"
  JVM_OPTS="$JVM_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${FASTI_DEBUG_PORT:-8000}"
  log "remote debugging on port ${FASTI_DEBUG_PORT:-8000}"
fi
if is_true "${JMX_PROMETHEUS:-}" || is_true "${FASTI_JMX_PROMETHEUS:-}"; then
  agent=$(find "$FASTI_HOME/javaagent" -maxdepth 1 -name 'jmx_prometheus_javaagent*.jar' 2>/dev/null | head -n 1)
  if [ -n "$agent" ]; then
    conf=${JMX_PROMETHEUS_CONF:-$FASTI_HOME/javaagent/prometheus-jmx.yml}
    JVM_OPTS="$JVM_OPTS -javaagent:$agent=${JMX_PROMETHEUS_PORT:-9404}:$conf"
    log "Prometheus JMX exporter on port ${JMX_PROMETHEUS_PORT:-9404}"
  else
    log "JMX_PROMETHEUS is set, but no jmx_prometheus_javaagent*.jar is in $FASTI_HOME/javaagent: not enabled (see README)"
  fi
fi

# --- wait for other services ------------------------------------------------------------------

wait_for=$(pick FASTI_WAIT_FOR WAIT_FOR)
if [ -n "$wait_for" ]; then
  timeout=$(pick FASTI_WAIT_FOR_TIMEOUT WAIT_FOR_TIMEOUT)
  timeout=${timeout:-30}
  old_ifs=$IFS; IFS=', '
  for target in $wait_for; do
    host=${target%:*}; port=${target##*:}
    [ "$host" != "$target" ] || fail "WAIT_FOR entries must be host:port, got $target"
    case "$port$timeout" in *[!0-9]*|'') fail "WAIT_FOR needs host:port and WAIT_FOR_TIMEOUT seconds, got $target / $timeout" ;; esac
    [ -n "$host" ] || fail "WAIT_FOR entries must be host:port, got $target"
    log "waiting up to ${timeout}s for $host:$port"
    deadline=$(( $(date +%s) + timeout ))
    until nc -z -w 2 "$host" "$port" 2>/dev/null; do
      [ "$(date +%s)" -lt "$deadline" ] || fail "$host:$port did not answer within ${timeout}s"
      sleep 1
    done
  done
  IFS=$old_ifs
fi

DEPLOYMENT_DIR="$FASTI_HOME/configuration/resources"
if [ -d /camunda/configuration/resources ]; then
  DEPLOYMENT_DIR=/camunda/configuration/resources
  log "deployments from /camunda/configuration/resources"
fi

eval "set -- $ARGS $USER_ARGS"
# exec: the JVM becomes PID 1 and receives SIGTERM for a graceful shutdown
# shellcheck disable=SC2086
exec java $JVM_OPTS ${JAVA_OPTS:-} \
  -Dloader.path="$LOADER_PATH" \
  -Dcamunda.deploymentDir="$DEPLOYMENT_DIR" \
  -jar "$FASTI_HOME/internal/fasti-bpm-run-core.jar" \
  --spring.config.location="$CONFIG" \
  "$@"
