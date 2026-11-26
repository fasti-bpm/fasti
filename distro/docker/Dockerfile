# syntax=docker/dockerfile:1
#
# Container image of Fasti BPM Run.
#
# The build context is prepared by the Maven module distro/docker (target/docker): it holds the
# Run distribution (fasti-bpm-run.tar.gz), the JDBC drivers (drivers/) and the files of this
# directory. See README.md.
#
#   ./mvnw -pl distro/docker -am package -DskipTests
#   docker build -t fasti-bpm:1.3.2 distro/docker/target/docker
#
# Pin the base image by digest for reproducible builds, for example
#   --build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>

ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# CVE-2026-114938 ("Java4Shell"): the x86-64 Base64 intrinsic of HotSpot writes out of bounds on
# CPUs with AVX-512 VBMI; fixed in Temurin 21.0.13.1. The official eclipse-temurin image carries
# that version, so its JRE is used as it is (1.2.0 replaced it with a second copy, 165 MB). The
# build fails if the base image has an older JRE.
ARG MIN_JAVA_VERSION=21.0.13.1

FROM ${JRE_IMAGE} AS unpack
COPY fasti-bpm-run.tar.gz /tmp/
# The application is extracted to plain jars (internal/app/fasti-bpm-run-core.jar and its lib/)
# instead of the executable jar: loading classes from nested jars cost about 1.2 s of every start.
# The build fails if a module directory (internal/*, not the extracted app/) holds two versions of
# one jar, which a build without clean can leave behind; the older classes could win.
RUN mkdir -p /fasti \
 && tar -xzf /tmp/fasti-bpm-run.tar.gz -C /fasti \
 && rm -f /fasti/start.sh /fasti/start.bat /fasti/shutdown.sh /fasti/shutdown.bat \
          /fasti/internal/run.sh /fasti/internal/run.bat \
          /fasti/configuration/keystore/keystore.p12 \
 && rm -rf /fasti/configuration/resources/* \
 && mv /fasti/configuration/default.yml /fasti/configuration/default.yml.example \
 && mkdir -p /fasti/configuration/drivers /fasti/data /fasti/javaagent \
 && java -Djarmode=tools -jar /fasti/internal/fasti-bpm-run-core.jar extract --destination /fasti/internal/app \
 && rm /fasti/internal/fasti-bpm-run-core.jar \
 && test -f /fasti/internal/app/fasti-bpm-run-core.jar \
 && dup="$(cd /fasti/internal && find . -path ./app -prune -o -name '*.jar' -print | sed -E 's#/([^/]*)-[0-9][^/]*\.jar$#/\1#' | sort | uniq -d)" \
 && if [ -n "$dup" ]; then \
      echo "More than one version of these jars (left over from an earlier build?): $dup" >&2; exit 1; \
    fi
COPY drivers/ /fasti/configuration/drivers/
COPY fasti-docker.yml /fasti/configuration/docker.yml
COPY fasti-docker-production.yml /fasti/configuration/docker-production.yml
COPY docker-entrypoint.sh /fasti/docker-entrypoint.sh
RUN chmod 0755 /fasti/docker-entrypoint.sh \
 && find /fasti -type d -exec chmod 0755 {} + \
 && find /fasti -type f ! -name docker-entrypoint.sh -exec chmod 0644 {} +
# The files go into the image in three layers, so that an update pulls only what changed:
# GraalJS with Truffle and ICU (about 65 MB, rarely updated), the other third-party jars of the
# application, its modules and the JDBC drivers, and last the Fasti jars, configuration and
# scripts, which change with every release. Only the build splits them; every file ends up at
# the same path with the same mode. The third-party files get a fixed modification time, so that
# the same jars (their names carry their versions) give the same layer in every build.
RUN mkdir -p /layers/graal/fasti /layers/deps/fasti /layers/app \
 && cd /fasti \
 && find ./internal ./configuration/drivers -type f -name '*.jar' ! -name 'fasti-*' > /tmp/third-party \
 && while IFS= read -r f; do \
      case "${f##*/}" in \
        js-language-*|js-scriptengine-*|icu4j-*|truffle-*|regex-*|polyglot-*|collections-*|nativeimage-*|word-*|jniutils-*) layer=graal ;; \
        *) layer=deps ;; \
      esac; \
      mkdir -p "/layers/$layer/fasti/${f%/*}" && mv "$f" "/layers/$layer/fasti/$f" || exit 1; \
    done < /tmp/third-party \
 && rm /tmp/third-party \
 && cd / \
 && mv /fasti /layers/app/fasti \
 && find /layers -type d -exec chmod 0755 {} + \
 && find /layers/graal /layers/deps -exec touch -d '2000-01-01 00:00:00' {} +

FROM ${JRE_IMAGE}
ARG VERSION=1.3.2
ARG REVISION=unknown
LABEL org.opencontainers.image.title="Fasti BPM" \
      org.opencontainers.image.description="Fasti BPM Run: BPMN/DMN process engine with REST API and web applications" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="Fasti BPM"

ENV FASTI_HOME=/fasti \
    LANG=C.UTF-8

# unprivileged user; uid/gid 1000 like the former official image, so that mounted volumes keep
# working. The application files belong to root and are read-only for the user; only data/ is
# writable (H2 demo database).
# security fixes of the Alpine packages released after the base image was built
RUN apk upgrade --no-cache \
 && addgroup -S -g 1000 fasti \
 && adduser -S -D -H -u 1000 -G fasti -h /fasti -s /sbin/nologin fasti
COPY --from=unpack /layers/graal/fasti/ /fasti/
COPY --from=unpack /layers/deps/fasti/ /fasti/
COPY --from=unpack /layers/app/fasti/ /fasti/
ARG MIN_JAVA_VERSION
RUN chown fasti:fasti /fasti/data \
 && java -version \
 && v="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.runtime.version = //p')" \
 && v="${v%%[+-]*}" \
 && if ! printf '%s\n%s\n' "$MIN_JAVA_VERSION" "$v" | sort -V -C; then \
      echo "Java $v of the base image is older than $MIN_JAVA_VERSION" >&2; exit 1; \
    fi

USER 1000:1000
WORKDIR /fasti
EXPOSE 8080

# the port the actuator answers on, chosen like the entrypoint does: FASTI_MANAGEMENT_PORT if it
# is a number, else FASTI_PORT if it is a number, else 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=4 \
  CMD p="${FASTI_MANAGEMENT_PORT:-}"; case "$p" in ''|*[!0-9]*) p="${FASTI_PORT:-}";; esac; \
      case "$p" in ''|*[!0-9]*) p=8080;; esac; \
      wget -q -O /dev/null "http://127.0.0.1:$p/actuator/health/readiness" || exit 1

STOPSIGNAL SIGTERM
ENTRYPOINT ["/fasti/docker-entrypoint.sh"]
CMD []
