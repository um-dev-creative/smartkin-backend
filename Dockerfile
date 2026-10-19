# syntax=docker/dockerfile:1

# JVM (JIT) image of smartkin-backend. Build the jar first (`mvn -B clean package`), then:
#
#   docker build --secret id=prx_internal_ca,src=certs/smartkin/prx-internal-ca.crt -t lamata/smartkin-backend:0.0.1 .
#
# Two stages: the first splits the Spring Boot jar into layers (dependencies / loader / snapshots / application), so a code-only
# change rebuilds one small layer instead of the whole 100+ MB jar; the second is the runtime image. The GraalVM image is in
# Dockerfile.native and exposes the same port and health probe.

ARG JAVA_IMAGE=amazoncorretto:25.0.4-alpine3.24

FROM ${JAVA_IMAGE} AS layers
WORKDIR /workspace
COPY target/smartkin-backend.jar smartkin-backend.jar
RUN java -Djarmode=tools -jar smartkin-backend.jar extract --layers --destination /workspace/extracted

FROM ${JAVA_IMAGE}
LABEL version="0.0.1"
LABEL description="SmartKin Backend API"
LABEL maintainer="Luis Mata luis.antonio.mata@gmail.com"

WORKDIR /usr/local/runme

# The old image imported five .crt files that no longer exist in the repository (src/main/resources/*.crt, keystore.jks), so it
# could not be built from a fresh checkout. The keystores of the TLS bundle now travel inside the jar (pom.xml, certs/smartkin),
# and the internal CA, which is git-ignored too, comes in as an optional BuildKit secret instead of a COPY from a path that may be
# missing. When given, it is added to the JVM trust store so outbound calls to the *.tst services (Vault, Config Server, backbone,
# mercury) verify their certificates; without it those calls use the default trust store only.
RUN --mount=type=secret,id=prx_internal_ca,target=/run/secrets/prx-internal-ca.crt,required=false \
    if [ -f /run/secrets/prx-internal-ca.crt ]; then \
      keytool -importcert -alias prx-internal-ca -cacerts -file /run/secrets/prx-internal-ca.crt -storepass changeit -noprompt; \
    fi

COPY --from=layers /workspace/extracted/dependencies/ ./
COPY --from=layers /workspace/extracted/spring-boot-loader/ ./
COPY --from=layers /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers /workspace/extracted/application/ ./
COPY docker-entrypoint.sh docker-entrypoint.sh

RUN addgroup -S appmng && adduser -S jvapps -G appmng \
    && chown -R jvapps:appmng . \
    && chmod -R 740 . \
    && chmod 750 docker-entrypoint.sh

USER jvapps:appmng

EXPOSE 8166
# Liveness over the container's own loopback; the certificate is not verified because it only reaches this process.
# /actuator/health/liveness does not depend on Postgres, MongoDB, Redis or Kafka, so a cloud outage does not restart the container.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD wget --no-check-certificate -qO- https://127.0.0.1:8166/actuator/health/liveness || exit 1
CMD ["./docker-entrypoint.sh"]
