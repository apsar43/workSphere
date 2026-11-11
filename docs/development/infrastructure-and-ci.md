# Local Infrastructure and CI Guide

This guide documents the PostgreSQL Compose project, configuration variables, complete local startup sequence, and GitHub Actions pipeline.

## 1. Prerequisites

```powershell
java -version
node --version
npm --version
docker --version
docker compose version
docker info
```

`docker --version` only proves that the CLI exists. `docker info` must also succeed to prove the Docker Desktop engine is running.

## 2. PostgreSQL Compose configuration

Files:

- `infrastructure/docker/compose.yml`
- `infrastructure/docker/.env.example`

The Compose project provides:

- pinned `postgres:17.7-alpine` image
- health check using `pg_isready`
- named volume `worksphere-postgres-data`
- environment-based database name, user, password, and host port
- safe local defaults only

## 3. Start PostgreSQL

```powershell
Set-Location infrastructure/docker
Copy-Item .env.example .env
docker compose config
docker compose up -d --wait
docker compose ps
```

Inspect logs:

```powershell
docker compose logs postgres
docker compose logs --follow postgres
```

Connect with `psql` inside the container:

```powershell
docker compose exec postgres psql -U worksphere -d worksphere
```

Inside `psql`:

```sql
SELECT current_database(), version();
```

Exit using `\q`.

### pgAdmin connection

Register a new pgAdmin server with these local-development values:

| pgAdmin field | Value |
|---|---|
| Name | `WorkSphere Local` (or any descriptive name) |
| Host name/address | `localhost` |
| Port | `5432` |
| Maintenance database | `worksphere` |
| Username | `worksphere` |
| Password | `worksphere_local_only` |

These credentials match the Compose defaults and `application-local.yml`. They are local-only credentials and must never be reused in staging or production. If `.env` overrides the Compose values, use the overridden values in pgAdmin and the corresponding backend environment variables.

## 4. Stop or reset PostgreSQL

Stop while retaining data:

```powershell
docker compose down
```

Stop and deliberately delete local database data:

```powershell
docker compose down --volumes
```

The second command is destructive. Use it only when the named local volume is the intended target.

## 5. Configuration variables

Compose variables:

| Variable | Local default |
|---|---|
| `POSTGRES_DB` | `worksphere` |
| `POSTGRES_USER` | `worksphere` |
| `POSTGRES_PASSWORD` | `worksphere_local_only` |
| `POSTGRES_PORT` | `5432` |

Backend variables:

| Variable | Local default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/worksphere` |
| `DB_USERNAME` | `worksphere` |
| `DB_PASSWORD` | `worksphere_local_only` |

If port 5432 is already occupied:

```powershell
Set-Location infrastructure/docker
Copy-Item .env.example .env
# Edit POSTGRES_PORT=5433 in .env
$env:DB_URL = "jdbc:postgresql://localhost:5433/worksphere"
docker compose up -d --wait
```

`.env` is ignored. `.env.example` is committed and contains no production secret.

## 6. Complete local startup

Terminal 1 — database:

```powershell
Set-Location infrastructure/docker
docker compose up -d --wait
```

Terminal 2 — backend:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Terminal 3 — frontend:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Verify:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:5173 -UseBasicParsing
```

## 7. Complete local verification

Frontend:

```powershell
Set-Location frontend
npm ci
npm run format:check
npm run lint
npm run typecheck
npm run test
npm run test:coverage
npm run build
npm audit --registry=https://registry.npmjs.org/
```

Backend:

```powershell
Set-Location ../backend
.\mvnw.cmd spotless:check
.\mvnw.cmd compile
.\mvnw.cmd test
.\mvnw.cmd verify
```

Infrastructure:

```powershell
Set-Location ../infrastructure/docker
docker compose config --quiet
docker compose up -d --wait
docker compose ps
```

## 8. GitHub Actions CI

Workflow: `.github/workflows/ci.yml`.

It runs on:

- pull requests
- pushes to `main`
- `develop`
- `codex/**`
- `feature/**`
- `fix/**`

### Frontend job

1. Check out the repository.
2. Install Node 22.13.0.
3. Restore npm cache.
4. Run `npm ci`.
5. Run formatting, lint, typecheck, tests, and build.

### Backend job

1. Check out the repository.
2. Install Temurin Java 21.
3. Restore Maven cache.
4. Run Spotless check.
5. Compile.
6. Run unit/context/architecture tests.
7. Run package and PostgreSQL Testcontainers integration tests.
8. Build the production backend Docker image.

The integration test is not silently skipped. GitHub-hosted Linux runners provide the Docker environment required by Testcontainers.

The frontend and backend jobs can appear twice for a feature branch with an open pull request: once for the branch `push` event and once for the `pull_request` event. Both executions use the same quality gates. Cloudflare preview builds are disabled separately in its dashboard so pull requests do not deploy the frontend.

## 9. Troubleshooting Docker

If Compose or Testcontainers reports an internal-server error or cannot find a Docker environment:

```powershell
docker version
docker info
docker context ls
docker context show
```

Then:

1. Start or restart Docker Desktop.
2. Wait until the Docker Desktop engine reports running.
3. Confirm Linux containers are selected.
4. Run `docker info` again.
5. Retry `docker compose up -d --wait`.
6. Retry `.\mvnw.cmd verify` from `backend`.

Distinguish failures carefully:

- Java unit tests failing is a code/test problem.
- `Could not find a valid Docker environment` is an environment problem.
- PostgreSQL starts but a SQL assertion fails is an application/schema test problem.

### Confirm PostgreSQL and backend readiness

```powershell
docker compose -f infrastructure/docker/compose.yml ps
docker compose -f infrastructure/docker/compose.yml exec postgres `
  psql -U worksphere -d worksphere -c "SELECT current_database(), current_user, version();"
Invoke-RestMethod http://localhost:8080/actuator/health
```

Compose should show PostgreSQL as `healthy`, the SQL command should report database/user `worksphere`, and Actuator should return status `UP`.

## 10. Before opening a pull request

```powershell
git status --short
git diff --check
```

Then run the complete frontend and backend verification sequences above. Report any Docker limitation instead of claiming the integration test passed.

