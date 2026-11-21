# syntax=docker/dockerfile:1
#
# Container image of Fasti BPM Run.
#
# The build context is prepared by the Maven module distro/docker (target/docker): it holds the
# Run distribution (fasti-bpm-run.tar.gz), the JDBC drivers (drivers/) and the files of this
# directory. See README.md.
#
#   ./mvnw -pl distro/docker -am package -DskipTests
#   docker build -t fasti-bpm:1.0.1 distro/docker/target/docker
#
# Pin the base image by digest for reproducible builds, for example
#   --build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>

ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# CVE-2026-114938 ("Java4Shell"): the x86-64 Base64 intrinsic of HotSpot writes out of bounds on
# CPUs with AVX-512 VBMI. The fix is Temurin 21.0.13.1. Until the official eclipse-temurin image
# carries it, the amd64 image replaces the JRE of the base image with the fixed Adoptium build,
# verified by its SHA-256 checksum. arm64 is not affected and keeps the JRE of the base image.
# Set FIXED_JRE_URL to an empty value to keep the base image JRE on amd64 as well.
ARG FIXED_JRE_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jre_x64_alpine-linux_hotspot_21.0.13.1_2.tar.gz
ARG FIXED_JRE_SHA256=72835f41a468ae949fc058f8ec6a73d303ca3e5ea165621e08aacb36bb537bba

FROM ${JRE_IMAGE} AS jre
ARG TARGETARCH
ARG FIXED_JRE_URL
ARG FIXED_JRE_SHA256
RUN mkdir -p /jre \
 && if [ "${TARGETARCH:-amd64}" = "amd64" ] && [ -n "$FIXED_JRE_URL" ]; then \
      wget -q -O /tmp/jre.tar.gz "$FIXED_JRE_URL" \
      && echo "$FIXED_JRE_SHA256  /tmp/jre.tar.gz" | sha256sum -c - \
      && tar -xzf /tmp/jre.tar.gz -C /jre --strip-components=1 \
      && rm -f /tmp/jre.tar.gz; \
    else \
      cp -a "$JAVA_HOME"/. /jre/; \
    fi

FROM ${JRE_IMAGE} AS unpack
COPY fasti-bpm-run.tar.gz /tmp/
RUN mkdir -p /fasti \
 && tar -xzf /tmp/fasti-bpm-run.tar.gz -C /fasti \
 && rm -f /fasti/start.sh /fasti/start.bat /fasti/shutdown.sh /fasti/shutdown.bat \
          /fasti/internal/run.sh /fasti/internal/run.bat \
          /fasti/configuration/keystore/keystore.p12 \
 && rm -rf /fasti/configuration/resources/* \
 && mv /fasti/configuration/default.yml /fasti/configuration/default.yml.example \
 && mkdir -p /fasti/configuration/drivers /fasti/data /fasti/javaagent
COPY drivers/ /fasti/configuration/drivers/
COPY fasti-docker.yml /fasti/configuration/docker.yml
COPY fasti-docker-production.yml /fasti/configuration/docker-production.yml
COPY docker-entrypoint.sh /fasti/docker-entrypoint.sh
RUN chmod 0755 /fasti/docker-entrypoint.sh \
 && find /fasti -type d -exec chmod 0755 {} + \
 && find /fasti -type f ! -name docker-entrypoint.sh -exec chmod 0644 {} +

FROM ${JRE_IMAGE}
ARG VERSION=1.0.1
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
RUN rm -rf "$JAVA_HOME"
COPY --from=jre /jre "$JAVA_HOME"
COPY --from=unpack /fasti /fasti
RUN chown fasti:fasti /fasti/data \
 && java -version

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
