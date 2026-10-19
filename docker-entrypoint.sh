#!/bin/sh
# JVM entrypoint of the JIT image. `exec` makes java PID 1, so `docker stop` reaches it as SIGTERM and Spring shuts down gracefully.
#
# The TLS keystores are classpath resources inside the jar (see pom.xml's certs/smartkin <resource>); SSL_KEYSTORE_LOCATION,
# SSL_TRUSTSTORE_LOCATION and the passwords come from config/local.env or Vault, so no -D flag repeats them here.
#
# JAVA_OPTS is appended last, so a deployment can override any default below, e.g. JAVA_OPTS="-XX:MaxRAMPercentage=50".
set -e

exec java \
    -XX:+UseCompactObjectHeaders \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Dspring.cloud.vault.enabled="${VAULT_ENABLED:-false}" \
    -Dspring.application.name=smartkin-backend \
    -Dapi.info.version=1.0.0 \
    ${JAVA_OPTS} \
    -jar smartkin-backend.jar
