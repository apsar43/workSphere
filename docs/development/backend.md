# Backend Development Guide

This guide documents the Spring Boot foundation, module structure, configuration profiles, test strategy, and commands needed to build and operate the backend locally.

## 1. Technology and versions

| Tool | Version | Purpose |
|---|---:|---|
| Java | 21 | Backend runtime and language |
| Maven Wrapper | 3.9.11 | Reproducible Maven execution |
| Spring Boot | 4.1.2 | Application framework and dependency management |
| PostgreSQL image | 17.7-alpine | Local and integration-test database family |
| ArchUnit | 1.4.1 | Architecture-boundary tests |
| Spotless Maven plugin | 2.46.1 | Java/YAML/XML formatting |

Spring Boot manages the compatible versions of Spring Framework, Spring Security, Hibernate, Flyway, PostgreSQL JDBC, JUnit, Mockito, AssertJ, and Testcontainers.

## 2. Included dependencies

Production dependencies:

- Spring Web MVC
- Spring Security, available for the approved future identity implementation
- Spring Data JPA and Hibernate
- PostgreSQL JDBC driver
- Spring Boot Flyway support and PostgreSQL Flyway database support
- Jakarta Validation
- Spring Boot Actuator

Test dependencies:

- Spring Boot test modules
- JUnit
- Mockito
- AssertJ
- Testcontainers JUnit integration
- Testcontainers PostgreSQL
- ArchUnit

H2 is intentionally not included.

## 3. Package and module structure

```text
backend/src/main/java/com/worksphere/
├── shared/
│   ├── application/
│   ├── configuration/
│   └── web/
├── identity/
├── organization/
├── authorization/
├── project/
├── task/
└── audit/
```

Each business module may add these packages when implementation requires them:

```text
<module>/
├── domain/
├── application/
├── infrastructure/
└── presentation/
```

Do not create empty classes or interfaces just to reproduce this shape. Domain and application abstractions must have a concrete business or isolation benefit.

## 4. Foundation components

The current backend contains:

- `WorkSphereApplication`: Spring Boot entry point.
- `UtcClockService` and `TimeConfiguration`: injectable UTC time for deterministic tests.
- `ApiErrorResponse`: consistent API error structure.
- `GlobalExceptionHandler`: validation and illegal-argument response foundation.
- `SecurityConfiguration` plus typed `WorkSphereSecurityProperties`/`WorkSphereCorsProperties`: defines the reviewed session, CSRF, CORS, and endpoint policy.
- Identity application services depend on the `IdentityStore` application port; the JDBC `IdentityRepository` is its infrastructure adapter.
- `AuthenticationController` and `SessionAuthenticationFilter`: browser authentication endpoints and opaque-session authentication boundary.
- Actuator health and info exposure without sensitive details.
- Flyway V1 identity migration and V2 distributed login-throttling migration with PostgreSQL integration coverage.

Registration, local login, logout, current-user lookup, and opaque database-backed sessions are implemented. Organization, project, task, role/RBAC, recovery email delivery, and Google OIDC remain future vertical slices. WorkSphere intentionally does not use browser JWT sessions.

## 5. Maven Wrapper

Use the checked-in wrapper rather than relying on the machine's global Maven version.

PowerShell:

```powershell
Set-Location backend
.\mvnw.cmd --version
```

Git Bash/Linux/macOS:

```bash
./mvnw --version
```

## 6. Configuration profiles

### Base configuration

`application.yml` defines:

- application name
- default `local` profile
- Hibernate `ddl-auto: validate`
- disabled Open Session in View
- UTC JDBC/Hibernate handling
- Flyway migration location
- Actuator health/info exposure
- restricted error details
- safe logging defaults

### Local profile

`application-local.yml` has local-only defaults:

```text
DB_URL=jdbc:postgresql://localhost:5432/worksphere
DB_USERNAME=worksphere
DB_PASSWORD=worksphere_local_only
```

Override them in PowerShell when needed:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/worksphere"
$env:DB_USERNAME = "worksphere"
$env:DB_PASSWORD = "your-local-password"
```

Do not place production credentials in these files.

### Production profile

`application-production.yml` intentionally provides no credential defaults. Start production mode only after supplying configuration through an approved secret-management mechanism:

```powershell
$env:SPRING_PROFILES_ACTIVE = "production"
$env:DB_URL = "jdbc:postgresql://database-host:5432/worksphere"
$env:DB_USERNAME = "worksphere_app"
$env:DB_PASSWORD = "retrieved-from-secret-manager"
.\mvnw.cmd spring-boot:run
```

Missing required production configuration must cause startup to fail clearly.

### Test profile

`src/test/resources/application-test.yml` disables datasource, JPA, and Flyway auto-configuration for context/unit tests that do not require a database. Database integration tests use PostgreSQL Testcontainers separately.

## 7. Start the backend

Start local PostgreSQL first. From the repository root:

```powershell
Set-Location infrastructure/docker
docker compose up -d --wait

Set-Location ../../backend
.\mvnw.cmd spring-boot:run
```

Check application health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected status:

```json
{"status":"UP"}
```

Stop the application with `Ctrl+C`.

Use a different port:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"
```

PowerShell note: quote Maven `-D...` options. Without quotes, PowerShell can incorrectly pass `.property=value` as a Maven lifecycle phase.

### Run from IntelliJ IDEA

1. Open the repository root, then right-click `backend/pom.xml` and select **Add as Maven Project**. If it is already imported, use the Maven tool window's **Reload All Maven Projects** action.
2. In **File > Project Structure**, configure the project SDK and the `worksphere-backend` module SDK as Java 21.
3. Open `backend/src/main/java/com/worksphere/WorkSphereApplication.java` and run its `main` method.
4. In the generated run configuration, confirm:
   - main class: `com.worksphere.WorkSphereApplication`
   - module/classpath: `worksphere-backend`
   - JRE: Java 21
   - working directory: the repository's `backend` directory

If IntelliJ reports that Spring packages do not exist while Maven compiles successfully, the IDE is compiling the repository as a plain Java module. Reimport `backend/pom.xml` as Maven, retain the `worksphere-backend` Maven module, and remove only the duplicate plain IDE module from **Project Structure > Modules**. Removing an IDE module does not delete source files. Optionally enable **Settings > Build, Execution, Deployment > Build Tools > Maven > Runner > Delegate IDE build/run actions to Maven**.

Validate the repaired IDE configuration from the Maven tool window by running `clean`, `compile`, and `test`. A successful test run currently reports six tests with zero failures.

### Run from VS Code or a terminal

Open a terminal at the repository root:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

VS Code must use a Java 21 JDK. Install the Java extension pack, open the repository, allow Maven import to finish, and use **Run Java** above `WorkSphereApplication.main`, or use the wrapper command above in its integrated terminal.

### Port 8080 is already in use

Only one backend instance can listen on port 8080. First stop the earlier IntelliJ run with the red **Stop** button or stop the terminal process with `Ctrl+C`.

Find the listener in PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
  Select-Object LocalAddress, LocalPort, OwningProcess
Get-Process -Id <PID>
```

After confirming it is the stale backend Java process, stop that exact PID:

```powershell
Stop-Process -Id <PID>
```

Do not terminate an unidentified process. Alternatively, keep the existing service and start this instance on another port:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
Invoke-RestMethod http://localhost:8081/actuator/health
```

## 8. Build and format commands

| Command | Result |
|---|---|
| `.\mvnw.cmd clean` | Remove Maven build output |
| `.\mvnw.cmd compile` | Compile production Java sources |
| `.\mvnw.cmd test` | Run unit, context, health, validation, and architecture tests |
| `.\mvnw.cmd verify` | Run unit tests, package, and execute `*IT` integration tests |
| `.\mvnw.cmd package` | Test and build the executable JAR |
| `.\mvnw.cmd spotless:check` | Verify Java/YAML/XML formatting |
| `.\mvnw.cmd spotless:apply` | Apply configured formatting |
| `.\mvnw.cmd spring-boot:run` | Start the backend locally |

CI-friendly output:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

## 9. Running backend tests

Run all non-integration tests:

```powershell
.\mvnw.cmd test
```

Run one test class:

```powershell
.\mvnw.cmd "-Dtest=HealthEndpointTest" test
```

Run several test classes:

```powershell
.\mvnw.cmd "-Dtest=UtcClockServiceTest,GlobalExceptionHandlerTest" test
```

Run one test method:

```powershell
.\mvnw.cmd "-Dtest=UtcClockServiceTest#returnsTimeFromInjectedClock" test
```

Run only architecture tests:

```powershell
.\mvnw.cmd "-Dtest=ModuleArchitectureTest" test
```

Run the complete build including PostgreSQL Testcontainers:

```powershell
.\mvnw.cmd verify
```

`verify` also generates the JaCoCo backend coverage report at `backend/target/site/jacoco/index.html` and enforces global minimums of 60% lines and 50% branches. Authentication tests cover registration enumeration behavior, concurrent duplicate handling, generic login failures, opaque-session creation, expiry values, token hashing, controller cookie behavior, Google-disabled behavior, session-filter authentication, and logout revocation.

Run only the PostgreSQL integration test while skipping unit-test execution:

```powershell
.\mvnw.cmd "-DskipUnitTests=true" "-Dit.test=PostgreSqlConnectionIT" verify
```

Docker Desktop must be running for `PostgreSqlConnectionIT`. The test deliberately fails rather than silently skipping when Docker is unavailable.

## 10. Existing backend tests

| Test | Responsibility |
|---|---|
| `WorkSphereApplicationTest` | Proves the Spring context starts |
| `HealthEndpointTest` | Proves Actuator health returns `UP` |
| `UtcClockServiceTest` | Demonstrates JUnit, Mockito, and AssertJ |
| `GlobalExceptionHandlerTest` | Proves validation errors use the common response structure |
| `ModuleArchitectureTest` | Protects module and domain dependency rules |
| `PostgreSqlConnectionIT` | Starts PostgreSQL with Testcontainers and runs a real query |

Naming convention:

- `*Test`: Surefire unit/context/architecture tests during `mvn test`.
- `*IT`: Failsafe integration tests during `mvn verify`.

## 11. Architecture-test behavior

ArchUnit currently enforces:

- domain packages must not depend on Spring or Jakarta Persistence
- one business module must not depend directly on another module's internals

Architecture tests must evolve with real packages. Do not weaken a rule merely to make an implementation pass; first decide whether the dependency violates the documented architecture or whether an explicit public module contract is needed.

## 12. Database and Flyway

The migration directory is:

```text
backend/src/main/resources/db/migration/
```

It contains `V1__identity_authentication_foundation.sql`, which creates users, optional password credentials, external identities, opaque sessions, verification/reset tokens, and OAuth authorization transactions. `V2__login_rate_limiting.sql` adds the shared account/source failure counters used across backend instances.

Rules:

- Flyway is the only schema migration mechanism.
- Hibernate remains on `ddl-auto: validate`.
- Never use `update`, `create`, or `create-drop` for production schema management.
- Never edit a migration already applied to a shared environment.
- Integration tests must use PostgreSQL, not H2.
- Create each subsequent migration only after the relevant ADR and ERD slice is approved.

## 13. Error response foundation

The common response contains:

- timestamp
- HTTP status
- error name
- safe message
- request path
- optional field violations

Do not expose stack traces, SQL errors, internal class names, authorization details, cookies, or secrets.

## 14. Identity security foundation

Spring Security now implements the first local browser-authentication slice from ADR-011.

Current behavior:

- `/actuator/health` and `/actuator/info` are public.
- registration, login, logout, CSRF bootstrap, and `/api/v1/users/me` are implemented.
- passwords use Argon2id and raw opaque session tokens are represented in PostgreSQL only by SHA-256 hashes.
- browser sessions use `HttpOnly`, host-only cookies; state-changing requests require the separate CSRF cookie/header pair.
- local registration auto-verifies accounts only in the `local` profile. Production keeps new accounts pending until the email-verification workflow is completed.
- PostgreSQL-backed account/source login throttling and sliding idle-session renewal are implemented. Google OIDC, password recovery, audit events, the concurrent-session cap, and organization RBAC remain later slices; the Google UI entry point intentionally reports unavailable until provider support is configured and implemented.
- all routes not explicitly allowed or authenticated remain denied.

Do not replace the opaque browser session with JWTs in browser storage. Extend this foundation incrementally under ADR-011.

## 15. Full local backend verification

With Docker Desktop and PostgreSQL available:

```powershell
Set-Location backend
.\mvnw.cmd spotless:check
.\mvnw.cmd compile
.\mvnw.cmd test
.\mvnw.cmd verify
```

Then start the real local application and verify health:

```powershell
.\mvnw.cmd spring-boot:run
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected startup evidence includes a successful PostgreSQL connection, Flyway migrations through V2, an initialized JPA entity manager, and Tomcat listening on port 8080.

## 16. Adding a backend capability later

For each vertical slice:

1. Confirm the ADR, ERD, API, and authorization rules are approved.
2. Add the smallest required module packages.
3. Keep controllers thin and do not expose persistence entities.
4. Put transactions in application workflows.
5. Constrain tenant queries by authorized organization ID.
6. Add unit, PostgreSQL integration, authorization, and cross-tenant tests.
7. Update ArchUnit rules when adding an intentional public module contract.
8. Run Spotless, compile, test, and verify before review.

