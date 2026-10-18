# SmartKin Backend: Technical Guide

## Stack

| Component | Version |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring Cloud | 2025.1.3 (Config, Bootstrap, OpenFeign, Vault, Eureka client) |
| springdoc | 3.1.0 (`springdoc-openapi-starter-webmvc-ui`) |
| MapStruct | 1.6.3 |
| PostgreSQL JDBC | 42.7.13 |
| Shared libraries | `com.umdc:commons`, `commons-services`, `security-oauth` 0.0.3 (Repsy) |
| Docker base image | `amazoncorretto:25.0.4-alpine3.24` |

Versions are aligned with `zerobound-rest`, `mercury` and `backbone-rest`. Boot-managed starters carry no explicit version.

## Build and test

```bash
mvn clean package            # jar at target/smartkin-backend.jar
mvn verify                   # tests, PMD, CPD and JaCoCo report (target/site/jacoco/jacoco.xml)
mvn test -Dtest=Class#method # single test
```

The private Repsy repository needs `REPSY_ACCOUNT_USER` and `REPSY_ACCOUNT_PASSWORD`; the `com.umdc` libraries can also come
from the local Maven cache.

## Coverage policy

- Every task must reach **85%** line and branch coverage on the classes and methods it adds or changes.
- `jacoco.minimum.coverage` is 0.85. The package-level check has `haltOnFailure=false` because legacy packages have no tests yet.
  Read the per-class numbers in `target/site/jacoco/jacoco.xml` to verify a task.
- Remove `haltOnFailure=false` once the legacy packages are covered.

## Spring Boot 4 migration notes

- `@EntityScan` lives in `org.springframework.boot.persistence.autoconfigure`.
- Jackson 3 uses `tools.jackson.*` (`ObjectMapper`, `JacksonException`, unchecked). Jackson annotations stay in `com.fasterxml.jackson.annotation`.
- javac no longer finds annotation processors on the classpath: `maven-compiler-plugin` lists `mapstruct-processor` and
  `spring-boot-configuration-processor` in `annotationProcessorPaths`.
- Surefire passes JaCoCo's `${argLine}` plus the Mockito agent; ASM 9.10.1 is required to read Java 25 bytecode.

## Feign client URLs

`@FeignClient(url = "${prx.clients.backbone.url}")` and `${prx.clients.mercury.url}` are resolved from `bootstrap.yml`, where
`BACKBONE_URL` and `MERCURY_URL` override the QA defaults. `FeignClientUrlConfigTest` checks the placeholders, the defaults and the override.

## Known gaps

- The service starts only with the full environment (Vault, Config Server, SSL bundle, OAuth clients); startup with a complete
  environment was not verified in this task.
- The Docker image was not built in this task.
- Tests exist only for `EmailMessageProducerServiceImpl`.
