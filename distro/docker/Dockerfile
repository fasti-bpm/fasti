# syntax=docker/dockerfile:1
#
# Container image of Fasti BPM Run.
#
# The build context is prepared by the Maven module distro/docker (target/docker): it holds the
# Run distribution (fasti-bpm-run.tar.gz), the JDBC drivers (drivers/) and the files of this
# directory. See README.md.
#
#   ./mvnw -pl distro/docker -am package -DskipTests
#   docker build -t fasti-bpm:1.0.0 distro/docker/target/docker
#
# Pin the base image by digest for reproducible builds, for example
#   --build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>

ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

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
ARG VERSION=1.0.0-SNAPSHOT
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
COPY --from=unpack /fasti /fasti
RUN chown fasti:fasti /fasti/data

USER 1000:1000
WORKDIR /fasti
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=4 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${FASTI_PORT:-8080}/actuator/health/readiness" || exit 1

STOPSIGNAL SIGTERM
ENTRYPOINT ["/fasti/docker-entrypoint.sh"]
CMD []
