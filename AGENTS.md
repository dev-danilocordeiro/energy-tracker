# AGENTS.md

Guidance for AI coding agents working in this repository.

## What this is

Home Energy Tracker: Spring Boot 4.1.1 / Java 21 microservices that ingest energy
readings from devices, aggregate them in InfluxDB, email users when they cross a
threshold and generate saving tips with a local LLM (Ollama).

The README is the project pitch; when it disagrees with the code, trust the code.

## Modules

Each module is an independent Maven project with its own `mvnw`. There is no parent
POM and no shared library.

| Module | Port | Responsibility | Talks to |
|---|---|---|---|
| `api-gateway` | 9000 | Spring Cloud Gateway (Server **WebMVC**), one route + circuit breaker per service | all HTTP services |
| `user-service` | 8081 | User CRUD. **Owns the Flyway migrations for the whole schema** | MySQL |
| `device-service` | 8082 | Device CRUD, devices by user | MySQL |
| `ingestion-service` | 8083 | Validates readings, publishes to Kafka. Contains a load simulator | Kafka `energy-usage` |
| `usage-service` | 8084 | Consumes readings, writes/aggregates in InfluxDB, publishes alerts | Kafka, InfluxDB, user/device services |
| `alert-service` | 8085 | Consumes alerts, stores them, sends email. **No HTTP API** | Kafka `energy-alerts`, MySQL, SMTP |
| `insight-service` | 8086 | Saving tips and overviews via Spring AI + Ollama | usage-service, Ollama |

Flow: `ingestion -> [energy-usage] -> usage -> [energy-alerts] -> alert`.

## Commands

```bash
docker compose up -d                  # MySQL, Kafka, Kafka UI, InfluxDB, Mailpit, Keycloak,
                                      # Prometheus, Grafana, Tempo, Loki, Redis
cp .env.example .env                  # first time only

cd <module> && ./mvnw verify          # build + tests for one module
cd <module> && ./mvnw spring-boot:run
load-tests/run.sh smoke               # k6 load test (smoke, load, stress, spike)
```

- There is no root build. Run commands inside the module you changed, and run them
  in every module you touched.
- Each module has a `@SpringBootTest` `contextLoads` test. In modules that use MySQL,
  Kafka or InfluxDB, it needs `docker compose` up to pass.
- Prometheus (`localhost:9090`) scrapes `/actuator/prometheus` of every app on
  the host through `host.docker.internal` (`docker/prometheus/prometheus.yml`).
  Grafana (`localhost:3000`, `admin`/`admin`) gets the Prometheus datasource from
  `docker/grafana/provisioning`. A new app needs actuator +
  `micrometer-registry-prometheus`, `prometheus` in the exposed endpoints, the
  `management.metrics.tags.application` tag and a scrape job.
- Tracing and logs: every app has `spring-boot-starter-opentelemetry` and exports
  spans to Tempo (`localhost:4318`, UI through Grafana) and logs to Loki's native
  OTLP endpoint (`localhost:3100/otlp/v1/logs`). No collector in between. OTLP
  metrics export is disabled; metrics stay on Prometheus. Sampling is 100% locally
  (`TRACING_SAMPLING_PROBABILITY`).
- Logs reach Loki through the Logback appender in `logback-spring.xml`, which
  `config/OpenTelemetryConfig` hands the SDK at startup. Boot does not wire it.
  `opentelemetry-logback-appender-1.0` is not managed by Boot: keep its version on
  the line built against the OpenTelemetry SDK that Boot ships (1.62 -> 2.28.x).
- Trace propagation only works through Boot's `RestClient.Builder` (from
  `spring-boot-starter-restclient`). `RestClient.create(...)` is not instrumented and
  breaks the trace. Kafka needs `observation-enabled: true` on the template (producer)
  and the listener (consumer) to carry `traceparent` in the record headers.
- `http.server.requests` histograms are on: they back the p95 panel and carry the
  trace exemplars Prometheus stores (`--enable-feature=exemplar-storage`).
- Load tests: k6 scenarios in `load-tests/` (smoke, load, stress, spike), run with
  `load-tests/run.sh <scenario>` through the `k6` compose service (profile
  `load-test`, host networking). k6 pushes metrics to Prometheus by remote write
  (`--web.enable-remote-write-receiver`); Grafana dashboard "k6 Load Tests". See
  `load-tests/README.md`. When you add an endpoint worth load testing, add it to
  `lib/api.js` and the weighted mix in `lib/traffic.js`.
- Cache: insight-service keeps Ollama answers in Redis (`localhost:6379`) for
  `INSIGHT_CACHE_TTL` (1h), per user, in `insight:saving-tips::<userId>` and
  `insight:overview::<userId>`. It goes through `service/InsightCache`, not
  `@Cacheable`: with Spring Data Redis, `sync = true` doesn't give per-key locking, so
  concurrent misses would each call Ollama. `InsightCache` makes them wait for one call,
  and falls back to Ollama if Redis fails (500ms timeouts). Users without devices are
  answered before the cache and never cached. CRUD endpoints are not cached: the k6
  baseline has them at 7-23ms p95, so there is little to gain and invalidation to
  get wrong.
- ingestion-service's `ParallelDataSimulator` posts ~200 readings/s. Turn it off with
  `SIMULATION_ENABLED=false` for load tests and for anything where its traffic is noise.
- Ollama is not in compose. insight-service expects it at `localhost:11434`.
- Keycloak: `http://localhost:8091`, admin `admin`/`admin` (created only on the
  first start, when its Postgres volume is empty).

## Conventions

### Code
- Package root is `com.devcordeiro.<module_name>` (underscore), split by layer:
  `controller`, `service`, `repository`, `dto`, `entity`/`model`, `exception`,
  `client`, `config`.
- Lombok is available in the services (`@Slf4j`, `@Builder`, ...). DTOs are records.
- Constructor injection, `final` fields.
- Downstream URLs, ports and credentials live in each module's `application.yaml`
  (YAML, not `.properties`), with `${ENV:default}` where compose needs it.

### Errors
- Each HTTP service has a `RestExceptionHandler` (`@RestControllerAdvice`) returning
  `ProblemDetail` (`application/problem+json`) with `type` =
  `https://energy-tracker.devcordeiro.com/problems/<slug>`, plus `instance` and
  `timestamp`. Follow that pattern for new errors.
- Never put database messages (constraint names, SQL) in the response. Log them at
  WARN and return a generic `detail`.

### Logging
- Keep personal data (e-mail, address, names) out of INFO logs. Log ids instead.
  Method arguments and return values go to DEBUG only.

### Kafka
- Event classes are duplicated in each producer/consumer under the **same** package,
  `com.devcordeiro.kafka.event`, because the JSON type mapping depends on it. If you
  change an event, change every copy.
- Topics: `energy-usage` (ingestion -> usage), `energy-alerts` (usage -> alert).

### Database
- MySQL schema is shared. All migrations are in
  `user-service/src/main/resources/db/migration`, even for `device` and `alert`
  tables.
- Flyway is forward-only: add a new `V<n>__*.sql`, never edit one that is on `main`.
- Constraints that protect data (FKs, unique keys, NOT NULL) belong in the schema.

### API gateway
- One `*ServiceRoutes` class per service in `route/`, each with a route, a
  Resilience4j circuit breaker named `<service>ServiceCircuitBreaker` and a 503
  fallback at `/fallback/<service>`. Fallback paths must be unique.
- A new circuit breaker must also be listed under
  `resilience4j.circuitbreaker.instances` so it exists from startup.
- The default time limit is 1s. Slow backends (insight/Ollama) need their own
  `resilience4j.timelimiter.instances` entry.
- Circuit breakers do not appear in `/actuator/health`: resilience4j-spring-boot3
  2.3.0 targets Boot 3's health package. Use `/actuator/circuitbreakers`.
- Target URLs are hardcoded to `localhost`.
- Every request needs a Keycloak JWT except the paths in `security.excluded.urls`
  (`/actuator/**`). Tokens come from realm `het-security-realm`, client
  `home-energy-tracker-client` (`client_credentials`).
- The realm lives in `keycloak/het-security-realm-realm.json` and is imported on
  startup (`--import-realm`). Keycloak requires the `<realm>-realm.json` file name.
  Import skips realms that already exist, so after changing the realm in the admin
  console, re-export it (`kc.sh export --realm het-security-realm --users realm_file`),
  then replace the client secret with `${HET_CLIENT_SECRET}` and drop the
  `org.keycloak.keys.KeyProvider` components (private keys) before committing.
- The client secret comes from `HET_CLIENT_SECRET` in the root `.env` at import
  time and must match `HET_CLIENT_SECRET` in `bruno/.env`.
- OpenAPI: each HTTP service has springdoc and a `config/OpenApiConfig` whose
  server is the relative URL `/`, so "Try it out" goes through whichever host
  served the page. The gateway serves Swagger UI at `/swagger-ui.html` and proxies
  each service's docs at `/aggregate/<service>-service/v3/api-docs` (a
  `<service>ServiceApiDocsRoute` bean plus an entry in `springdoc.swagger-ui.urls`).
  Those paths are listed in `security.excluded.urls`.
- Don't name the filter chain bean `springSecurityFilterChain`: that name makes
  Boot skip `@EnableWebSecurity`, and startup fails with no `HttpSecurity` bean.

## Testing

- Unit tests use JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), no
  Spring context.
- Test method names describe behaviour as a sentence in camelCase, e.g.
  `publishesAnAlertWhenTheSumOfAUsersDevicesExceedsTheirThreshold`.
- Bug fixes come with a test that fails without the fix.

### Bruno (`bruno/`)
- The API collection for manual and end-to-end checks. One folder per service, plus
  `api-gateway/` which runs the whole flow through the gateway and has an
  `actuator/` subfolder.
- Every request has `tests` asserting status and body, and a `docs` block explaining
  what it shows. Request names, docs and test names are in Portuguese (without
  accents).
- Requests pass ids along through environment variables (`userId`, `deviceId`,
  `userEmail`). Base URLs are in `bruno/environments/local.bru`.
- `userId`, `deviceId` and `userEmail` in `local.bru` change on every run. Don't
  commit those changes.
- `collection.bru` defines an OAuth2 `client_credentials` config with
  `credentials_id: het-token`. Requests through the gateway use `auth: inherit`;
  anywhere else the token is `{{$oauth2.het-token.access_token}}`. Requests that
  call a service directly stay `auth: none`.
- The client secret is read from `bruno/.env` (`HET_CLIENT_SECRET`, gitignored,
  see `bruno/.env.example`). Never write the secret into a `.bru` file.
- When you add or change an endpoint, add or update its Bruno request.

## Git

- Conventional Commits with the module as scope: `feat(usage-service): ...`,
  `fix(user-service,device-service): ...`, `chore(bruno): ...`,
  `test(bruno): ...`. Imperative mood, lowercase.
- Commit bodies explain *why*.
- Work on a branch (`feat/...`, `fix/...`, `chore/...`), open a PR with
  `## Summary` and `## Test plan` (checkboxes; say what was not run), and merge
  with a merge commit.
- No `Co-Authored-By` trailers or any AI attribution in commits, PRs, code or docs.
- `HELP.md` files are Spring Initializr leftovers and are gitignored.
