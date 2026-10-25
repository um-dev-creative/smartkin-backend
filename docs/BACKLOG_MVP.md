# SmartKin: análisis del estado y backlog unificado del MVP

Fecha del análisis: 2026-10-21.
Repos cubiertos: `smartkin-backend` (rama `docker-1-container-images`) y `smartkin-frontend` (rama `rd-2-bff-session-redis`).

Este análisis se hizo leyendo el código, el historial de git, los CHANGELOG y las guías de `docs/`. No se ejecutó la compilación ni las pruebas.

---

## 0. Estado de ejecución

Actualizado: 2026-10-23. Backend: todo lo que no necesita QA está en `development` (PR #20 a #31) más S-2 en el PR #32. Frontend: pila de PR #7 a #24 pendiente de fusión. Lo que queda depende de QA, de plataforma o de una persona: BE-2, BE-3, X-1, X-4, FE-2, S-1, S-4 y el registro de métricas de BE-14.

| Ticket | Estado | Nota |
|---|---|---|
| BE-1 | Hecho | La cadena completa quedó en `development` (fast-forward, solo local). `mvn -o test` en verde con cobertura cumplida. |
| FE-1 | Hecho | La cadena ng-1 → ng-5 → rd-2 quedó en `development` (solo local). `pnpm test:server` en verde. Las specs de Angular tienen 27 de 45 fallos previos (`community-member`, `stage`, `partner`, `not-found`, `verify-code`), que no vienen de la migración; se corrigen en FE-4 y FE-15. |
| FE-3 | Hecho | Rama `sak-8-fix-auth-session-id`, mergeada en `development`. Además se corrigió un `logic.isValidUUID` indefinido. 25 pruebas del BFF en verde. |
| BE-4 | Hecho (en `development`) | PR #20: perfiles de padre e hijo. Enmienda al contrato: errores como estado + header `message-error`. `POST /children` (vincular por ID) fue **reemplazado** por invitaciones y alta de cuenta (S-2, PR #32). |
| BE-5 | Hecho (en `development`) | `AccessPolicy` reutilizable y pruebas de acceso cruzado entre dos familias sobre PostgreSQL. |
| BE-6 | Hecho (en `development`) | 5 operaciones del catálogo y migración V4 (`activities.lesson_id`). |
| BE-7 | Hecho (en `development`) | Asignar, listar y consultar asignaciones; evento `ACTIVITY_ASSIGNED` tras el commit. |
| BE-8 | Hecho (en `development`) | Leer/reportar progreso y comentario del padre, con bloqueo por asignación. |
| BE-9 | Hecho (en `development`) | Calificación en servidor (conjunto exacto, sin crédito parcial), intentos, progreso y `QUIZ_COMPLETED`. |
| BE-10 | Hecho (en `development`) | Puntos paginados y logros del hijo, solo lectura. |
| BE-11 | Hecho (en `development`) | Reglas `QUIZZES_PASSED`, `ACTIVITIES_COMPLETED`, `POINTS_REACHED`; migración V5. Los 3 logros de V5 son de ejemplo: ver `docs/ACHIEVEMENTS_CATALOG.md`. |
| BE-12 | Hecho (en `development`) | Bandeja, contador, marcar una/todas como leídas. |
| BE-13 | Hecho (en `development`) | Flujo completo sobre PostgreSQL, MongoDB y Kafka reales. No arranca la app completa (Vault/TLS/seguridad): eso es BE-2. |
| BE-14 | Hecho en código; falta plataforma | Contadores y logs `DEAD_LETTERED` (en `development`). Reglas de alerta listas en `docs/ops/smartkin-events.rules.yml`; falta el registro Prometheus expuesto al scraper y cargar las reglas (ver `docs/QA_RUNBOOK_MVP.md`). |
| S-2 | Hecho (backend #32 y frontend #23, fusionados) | Invitación con código de un solo uso y alta de la cuenta del hijo por el padre; `POST /children` eliminado. Migración V6. |
| S-5 | Hecho (frontend #24, fusionado) | `pnpm audit`: de 33 avisos (2 críticos) a 0. Dependabot, revisado el 2026-10-25: 0 alertas abiertas en frontend (23 `fixed`) y en backend. |
| X-5 | Hecho (revisión); hallazgos abiertos | `docs/SECURITY_REVIEW_MVP.md`. Corregidos: S-2, S-3 (secretos en logs), S-9. Abiertos: S-1 (verificar en QA que la librería PRX rechaza tokens falsos; el script `scripts/qa-smoke.sh` lo comprueba), S-4 (rotar token de Vault). |
| X-6 | Hecho (frontend #21) | README del frontend alineado con el producto real. |
| FE-4 a FE-14 | Hechos (frontend #10 a #20 y #23, fusionados) | Limpieza de pantallas heredadas, proxy del BFF, capa de datos, panel del padre, vista del hijo, quiz, puntos, notificaciones, roles y alta de hijos. Specs de Angular: 218 en verde. |
| FE-15 | Hecho de forma continua | Cada ticket trajo sus specs (de 18 a 218). |
| FE-2 | Bloqueado: acción manual | El token de Vault está en el commit `2eb6965` (2025-08-01, `run-local.sh`). Debe rotarse en Vault; no se puede hacer desde el repo. |
| BE-2 | Bloqueado: requiere QA | Necesita Vault, Config Server y Supabase reales. Pasos en `docs/QA_RUNBOOK_MVP.md`. |
| BE-3 | Scripts listos; ejecutarlos requiere QA | `scripts/kafka-create-topics.sh` (los 4 topics), `docs/db/smartkin_roles.sql` (usuarios `smartkin_owner` y `smartkin_app`) y `scripts/db-migrate.sh` (tablas, Flyway V1 a V6) están probados en contenedores desechables. Falta correrlos contra el entorno real con las credenciales de Vault. Pasos en `docs/QA_RUNBOOK_MVP.md`. |
| X-1 | Bloqueado | Depende de BE-2. |
| X-4 | Bloqueado: requiere QA | El script `scripts/qa-smoke.sh` automatiza S-1, la invitación y la rebanada; falta ejecutarlo contra QA y la prueba con el frontend. |
| X-2 | Hecho | Rama `sak-21-mvp-openapi-contract`: `docs/api/openapi-v1.yaml` (25 operaciones al inicio, 29 con S-2), decisión D-6 y `OpenApiContractTest`. 200 pruebas en verde. |
| X-3 | Hecho | Frontend, rama `sak-22-api-types`: `pnpm gen:api` / `pnpm check:api` y tipos en `src/app/shared/api`. |

---

## 1. smartkin-backend

### 1.1 Qué es

API REST Spring Boot 4.1 / Java 25 (Maven, paquete `com.umdc.smartkin`). Es una capa fina de orquestación: delega identidad y verificación en backbone y mercury mediante Feign, y guarda su dominio en PostgreSQL (Supabase), MongoDB, Redis y Kafka.

### 1.2 Estado actual

| Área | Estado |
|---|---|
| Plataforma | Listo: Java 25, Spring Boot 4.1.1, CI en GitHub Actions, 85% de cobertura mínima (JaCoCo) y PMD. |
| Usuarios y auth | Listo en lo básico: CRUD de usuarios, token de sesión y confirmación de código, delegados en backbone y mercury. |
| Esquema Postgres | Hecho: 13 entidades, migraciones Flyway V1 a V3, RLS y rol restringido `smartkin_app` (decisiones D-1 a D-3). |
| MongoDB | Hecho: colecciones `lessons`, `quiz_attempts` e `interaction_events`, con índices y TTL de 90 días para eventos. |
| Redis | Hecho: revocación de JWT (RD-1) y caché del catálogo de lecciones (RD-2). |
| Kafka | Definido: contrato de eventos, publisher, consumidores idempotentes, reintentos y topic de errores (D-4, D-5). |
| Contenedores | Imagen JVM con Dockerfile en capas, compose y healthcheck. La imagen nativa compila pero no arranca. |
| Documentación | Al día, con 5 registros de decisión. |

### 1.3 Brechas frente a un MVP

1. **Casi no hay endpoints de dominio.** Los únicos controladores son `UserApi` y `UserRegisterApi`. Faltan endpoints para actividades, asignaciones, progreso, metas, recompensas, logros, puntos, notificaciones, tópicos, catálogo de lecciones, quizzes y perfiles de padre e hijo. `LessonCatalogService` existe pero ninguna API lo expone.
2. **Nadie publica eventos.** `SmartKinEventPublisher` no se llama desde ningún flujo. Los consumidores esperan `ACTIVITY_ASSIGNED`, `QUIZ_COMPLETED` y `ACHIEVEMENT_EARNED`, que nunca se emiten.
3. **Nunca se verificó un arranque real.** Las guías indican que el arranque con el entorno completo no se verificó. Flyway solo se probó contra Testcontainers, no contra Supabase, y los DDL y enums de D-2 siguen sin confirmarse contra el esquema real.
4. **El código no está integrado.** `development` va 29 commits por detrás de la rama actual. Las ramas están encadenadas (jv, db, mg, rd, kf, docker) y no están mergeadas.
5. **Faltan permisos y reglas de dominio.** No hay verificación de que un padre solo vea a sus hijos, ni reglas de puntos, logros o metas. Los consumidores solo reaccionan a eventos.
6. **Infraestructura sin resolver.** Los topics de Kafka, incluido el de errores, se crean a mano y no hay alertas. `smartkin_app` necesita contraseña. Los keystores de `certs/` no están versionados. La imagen nativa no funciona.
7. **Calidad.** Hay 34 clases de prueba y ninguna recorre un flujo completo con el contexto entero. Un bug de arranque (`prx.*` contra `umdc.*`) solo apareció al probar el contenedor.

---

## 2. smartkin-frontend

### 2.1 Qué es

SPA Angular 22 (Tailwind 4, NgRx 22, Material 19) con un BFF Express 5 que hace de proxy hacia backbone y smartkin-backend. Tiene 237 archivos `.ts`, 21 specs y 2 archivos de pruebas del BFF.

### 2.2 Estado actual

| Área | Estado |
|---|---|
| Plataforma | Al día: Angular 19 a 22, Node 24, pnpm 12, Express 5 y builders `@angular/build` (NG-1 a NG-5). |
| BFF | Renombrado a smartkin y apuntando a `/smartkin-backend/api/v1`. Sesiones en Redis con respaldo en memoria (RD-2), con 15 pruebas con cliente falso. |
| UI base | Sistema de diseño (`components/ui`), página de marca, páginas legales, `about` y `stage` (landing). |
| Auth | Pantalla de registro e inicio de sesión, guard y verificación de código (`veracode`). |
| Dashboard del padre | Componentes `learners`, `family-contract`, `generate-link` y `settings`, alimentados por JSON estáticos, sin llamadas al backend. |
| Training | Existe `training.service.ts`, pero la pantalla parece de datos estáticos. |

### 2.3 Problemas importantes

1. **Heredó pantallas de otro producto.** `partner`, `deals` y `community-member` vienen de Directory y llaman a `businesses`, `categories` y ofertas que smartkin-backend no tiene. Hay también un `partner-mock.service`.
2. **Casi nada está conectado al backend real.** Solo auth y usuarios hablan con smartkin-backend. Las rutas `/auth/session-token` y `/profile/image` no se verificaron contra el backend.
3. **El BFF nunca arrancó completo.** No se probó `server.js` con Vault y los backends reales, ni el login completo con Redis.
4. **Bug conocido sin corregir.** `smartkin-backend-auth.controller` pasa el objeto `req` como id de sesión, así que la sesión nunca se encuentra.
5. **Seguridad.** Hay un token de Vault viejo en el historial de git que debe rotarse.
6. **Ramas sin integrar.** `development` va 10 commits por detrás de la rama actual (ng-1 a ng-5 y rd-2).
7. **Detección de cambios.** Usa zone.js con `ChangeDetectionStrategy.Eager`. No bloquea el MVP.

---

## 3. Vista conjunta

El backend no tiene endpoints de producto y el frontend no tiene pantallas conectadas a nada. Las dos mitades están terminadas a nivel de plataforma, pero ningún flujo de usuario funciona de punta a punta, salvo registrarse e iniciar sesión, y eso sin verificar en vivo.

**Definición de MVP asumida:** un padre registra a su hijo y le asigna lecciones o actividades; el hijo las completa y gana puntos y logros; el padre ve el progreso.

**Rebanada vertical recomendada:** "el padre asigna una lección, el hijo la completa y gana puntos". Cubre auth, catálogo, asignación, quiz, eventos Kafka y notificación en ambos repos y destapa los problemas de integración temprano.

---

## 4. Backlog unificado

### Convenciones

- Prefijos: `BE-n` para smartkin-backend, `FE-n` para smartkin-frontend y `X-n` para tickets transversales (el repo se indica).
- Tamaño: S es menos de 1 día, M de 1 a 3 días y L de 3 a 5 días. Son estimaciones sin validar con el equipo.
- Ramas: `sak-<n>-<descripción>`, a partir de `development`.
- Todo ticket de backend exige 85% de cobertura en el código tocado y actualizar `CHANGELOG.md`, `docs/USER_GUIDE.md` y `docs/TECHNICAL_GUIDE.md`.

### Épica 0: Estabilizar (bloquea todo lo demás)

| ID | Repo | Ticket | Tam. | Depende |
|---|---|---|---|---|
| BE-1 | backend | Mergear a `development` la cadena jv → db → mg → rd → kf → docker, con un PR por tramo y CI en verde | M | |
| FE-1 | frontend | Mergear a `development` la cadena ng-1 → ng-5 → rd-2 | S | |
| FE-2 | frontend | Rotar el token de Vault que sigue en el historial de git (purgar el historial es opcional, rotar es obligatorio) | S | |
| FE-3 | frontend | Corregir `smartkin-backend-auth.controller`, que pasa `req` como id de sesión, con una prueba | S | FE-1 |
| BE-2 | backend | Arrancar el contenedor con el entorno completo en QA, aplicar Flyway V1 a V3 contra Supabase y confirmar los DDL y enums de D-2 | M | BE-1 |
| BE-3 | backend | Crear los topics de Kafka (3 de eventos más `smartkin.events.error.v1`) y asignar la contraseña de `smartkin_app` | S | BE-2 |
| X-1 | ambos | Smoke test de registro → código de verificación → login a través de BFF, backend, backbone y mercury, con Redis | M | BE-2, FE-3 |

### Épica 1: Contrato de API

| ID | Repo | Ticket | Tam. |
|---|---|---|---|
| X-2 | backend | OpenAPI v1 de los endpoints de la rebanada vertical: hijos, catálogo, asignación, intento de quiz, puntos, logros y notificaciones. Se acuerda con el frontend antes de implementar | M |
| X-3 | frontend | Generar los tipos TypeScript del cliente a partir del OpenAPI, o escribirlos a mano si no se automatiza | S |

### Épica 2: Backend de dominio

| ID | Ticket | Tam. | Depende |
|---|---|---|---|
| BE-4 | Perfiles de padre e hijo: crear, listar y consultar hijos del padre autenticado | M | X-2 |
| BE-5 | Autorización por propietario: un padre solo ve a sus hijos y un hijo solo se ve a sí mismo, con pruebas de acceso cruzado | M | BE-4 |
| BE-6 | `GET` de tópicos y lecciones publicadas, exponiendo `LessonCatalogService` con su caché Redis | S | X-2 |
| BE-7 | Asignar una actividad o lección a un hijo (`POST`), listar asignaciones y publicar `ACTIVITY_ASSIGNED` | M | BE-4, BE-6 |
| BE-8 | Progreso de actividad: iniciar y actualizar `ActivityProgress` | M | BE-7 |
| BE-9 | Enviar un intento de quiz: guardar en Mongo `quiz_attempts`, calcular el puntaje y publicar `QUIZ_COMPLETED` | L | BE-7 |
| BE-10 | Consultar puntos, logros y saldo del hijo, con paginación | M | BE-9 |
| BE-11 | Reglas de logros: al cumplir una condición, otorgar el logro y publicar `ACHIEVEMENT_EARNED` | M | BE-9 |
| BE-12 | Bandeja de notificaciones: listar y marcar como leídas | S | BE-7 |
| BE-13 | Prueba de integración que arranque el contexto completo (Testcontainers de Postgres, Mongo, Redis y Kafka) y recorra asignar → quiz → puntos → notificación | L | BE-9, BE-11 |
| BE-14 | Alerta y métrica sobre el topic de errores y la profundidad de reintentos | S | BE-3 |

### Épica 3: Frontend

| ID | Ticket | Tam. | Depende |
|---|---|---|---|
| FE-4 | Retirar `partner`, `deals`, `community-member`, `partner-mock.service` y las llamadas a `businesses` y `categories`; limpiar rutas y menú | M | FE-1 |
| FE-5 | Rutas proxy del BFF para los endpoints nuevos (hijos, catálogo, asignaciones, quiz, puntos, notificaciones), con pruebas en `server/test` | M | X-2 |
| FE-6 | Capa de estado NgRx y servicios HTTP para las entidades del MVP, reemplazando los JSON estáticos del dashboard | M | X-3, FE-5 |
| FE-7 | Panel del padre: lista de hijos y alta de un hijo (`learners`, `generate-link`) | M | FE-6, BE-4 |
| FE-8 | Panel del padre: ver el catálogo y asignar una lección a un hijo | M | FE-7, BE-7 |
| FE-9 | Panel del padre: progreso real de cada hijo (reemplaza `progress-data.json`) | M | FE-8, BE-8 |
| FE-10 | Vista del hijo: mis asignaciones y reproducción de la lección | L | FE-6, BE-8 |
| FE-11 | Vista del hijo: quiz y resultado con puntos ganados | L | FE-10, BE-9 |
| FE-12 | Puntos y logros del hijo, con aviso al ganar uno | M | FE-11, BE-10 |
| FE-13 | Bandeja de notificaciones, con el contador en el encabezado | S | BE-12 |
| FE-14 | Guards por rol (padre o hijo) y manejo de errores 401, 403 y 503 | S | BE-5 |
| FE-15 | Ampliar los specs de componentes y servicios nuevos (hoy hay solo 21 specs) | M | continuo |

### Épica 4: Validación del MVP

| ID | Repo | Ticket | Tam. | Depende |
|---|---|---|---|---|
| X-4 | ambos | Prueba extremo a extremo en QA: el padre crea un hijo y le asigna una lección, el hijo completa el quiz y gana puntos, y el padre ve el progreso. Probar también el reintento y la cola de errores | L | BE-13, FE-12 |
| X-5 | ambos | Revisión de seguridad: RLS, tokens revocados, acceso cruzado entre familias y el almacenamiento de tokens en el BFF | M | X-4 |
| X-6 | ambos | Actualizar USER_GUIDE y TECHNICAL_GUIDE, y alinear el README del frontend con el producto real | S | X-4 |

### Camino crítico

```
BE-1/FE-1 → FE-3 → BE-2 → X-1 → X-2
  → BE-4 → BE-5 → BE-7 → BE-9 → BE-11 → BE-13 ┐
  → FE-4/FE-5 → FE-6 → FE-7 → FE-8 → FE-10 → FE-11 → FE-12 ┴→ X-4 → X-5/X-6
```

Los tickets del frontend pueden avanzar en paralelo con el backend contra un mock del contrato (X-2). Las dependencias de backend (BE-x) aplican a la integración real, no a empezar a construir las pantallas.

---

## 5. Fuera del MVP

- Metas y recompensas con canje (`GoalEntity`, `RewardEntity`, `ChildReward`).
- Imagen nativa del backend (`Dockerfile.native`).
- SSR en tiempo de ejecución y paso del frontend a zoneless/OnPush.
- Analítica sobre `interaction_events`.
- Subir `@angular/material` y `cdk` de 19 a 22, que hoy están desfasados respecto al resto de Angular 22.

## 6. Riesgos

1. BE-2 puede revelar que el esquema real de `umdevc` difiere del DDL derivado de las entidades, lo que movería la estimación de toda la épica 2.
2. BE-9 y BE-11 definen las reglas de puntos y logros, que nadie ha especificado. Necesitan una decisión de producto antes de empezar.
3. Las dependencias externas de backbone y mercury (verificación de código y correo) pueden bloquear X-1 si fallan en QA.
