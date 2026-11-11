# Build, CI/CD, and Free Deployment Guide

This guide defines the current zero-cost deployment flow for WorkSphere. It covers local release builds, GitHub Actions CI, Neon PostgreSQL, the Render backend, and the Cloudflare Worker frontend.

> This is a hobby/demo deployment baseline, not a production SLA. Free services can sleep, throttle, change limits, or be discontinued. Do not store sensitive or customer production data until security, backup, recovery, monitoring, and paid-service requirements are approved.

## 1. Recommended hosting flow

```text
Developer workstation
        |
        | feature branch + pull request
        v
GitHub repository
        |
        +--> GitHub Actions CI
        |      - frontend format/lint/typecheck/test/build
        |      - backend format/compile/test/verify
        |
        | merge only after CI succeeds
        v
      main
        |
        +--> Cloudflare Worker Static Assets --> React/Vite frontend
        |
        +--> Render Web Service --> Spring Boot API
                                      |
                                      v
                                Neon PostgreSQL
```

Recommended providers:

| Component | Provider | Reason |
|---|---|---|
| Source control | GitHub | Repository, pull requests, branch protection, Actions |
| CI | GitHub Actions | Existing workflow already validates both applications |
| Frontend | Cloudflare Workers Static Assets | Free static hosting, TLS, GitHub integration, and SPA routing |
| Backend | Render Free Web Service | Supports Docker-based Java services and managed HTTPS |
| Database | Neon Free Postgres | Persistent serverless PostgreSQL without Render's 30-day free database expiry |

Render's free backend sleeps after 15 minutes without inbound traffic and can take about one minute to wake. Its filesystem is ephemeral. Neon can also scale idle compute to zero. Expect cold-start latency.

## 2. Deployment readiness status

Already available:

- reproducible frontend build and test commands
- Maven Wrapper and Java 21 backend build
- PostgreSQL integration testing through Testcontainers
- GitHub Actions CI workflow
- production Spring profile requiring external database credentials
- Actuator health endpoint

Required before the first live deployment:

- create and test the first Flyway migration before adding entities
- decide whether direct or pooled Neon connections are appropriate for Hibernate/HikariCP
- configure secret values only in Render/Neon, never in Git
- verify authentication, cookies, CSRF, trusted proxy, and HTTPS behavior before implementing login

Deployment foundation already included:

- multi-stage Java 21 backend `Dockerfile` running as a non-root user
- host-provided `PORT` support with local port 8080 as the fallback
- environment-specific exact-origin CORS configuration
- frontend `VITE_API_BASE_URL` support
- Cloudflare Workers Static Assets configuration with SPA route fallback
- pinned Wrangler CLI used by Cloudflare's deploy command
- production container build verification in GitHub Actions

Do not deploy the current scaffold as if it were a completed product. At this stage, the deployment proves the delivery pipeline and health connectivity only.

## 3. Build a release locally

Start Docker Desktop before running backend integration tests.

From the repository root:

```powershell
Set-Location frontend
npm ci
npm run format:check
npm run lint
npm run typecheck
npm run test
npm run build

Set-Location ../backend
.\mvnw.cmd spotless:check
.\mvnw.cmd clean verify
```

Build artifacts:

- frontend: `frontend/dist/`
- backend executable JAR: `backend/target/worksphere-backend-0.1.0-SNAPSHOT.jar`

Run the packaged backend locally:

```powershell
Set-Location backend
$env:SPRING_PROFILES_ACTIVE = "local"
java -jar target/worksphere-backend-0.1.0-SNAPSHOT.jar
```

Verify health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 4. Push the repository to GitHub

Create an empty repository in your GitHub account. Do not initialize it with another README, `.gitignore`, or license when the local repository already contains them.

Inspect the local state first:

```powershell
git status
git branch --show-current
git remote -v
```

Never commit `.env`, database passwords, tokens, private keys, IDE metadata, `node_modules`, `target`, or `dist`.

After reviewing the changes:

```powershell
git add .
git diff --cached --check
git diff --cached --stat
git commit -m "chore: establish WorkSphere development foundation"
```

Add your GitHub remote using the URL shown by GitHub:

```powershell
git remote add origin https://github.com/<github-user>/<repository>.git
git push -u origin main
```

If `origin` already exists, inspect it before changing anything:

```powershell
git remote get-url origin
git remote set-url origin https://github.com/<github-user>/<repository>.git
```

For ongoing work:

```powershell
git switch -c feature/<short-description>
git push -u origin feature/<short-description>
```

Open a pull request into `main` and merge only after CI succeeds and the change is reviewed.

## 5. GitHub Actions CI

The repository already contains `.github/workflows/ci.yml`. It runs for pull requests and for pushes to `main`, `develop`, `codex/**`, `feature/**`, and `fix/**`.

Current CI gates:

### Frontend

```text
npm ci
npm run format:check
npm run lint
npm run typecheck
npm run test
npm run build
```

### Backend

```text
./mvnw --batch-mode spotless:check
./mvnw --batch-mode --no-transfer-progress compile
./mvnw --batch-mode --no-transfer-progress test
./mvnw --batch-mode --no-transfer-progress verify -DskipUnitTests=true
```

GitHub-hosted standard runners are free for public repositories. Private repositories have plan-based minute and storage quotas.

After pushing, open the repository's **Actions** tab and confirm both jobs are green. If Actions are disabled, enable them under **Settings > Actions > General**.

Recommended `main` protection after the first successful run:

1. Open **Settings > Branches** or **Settings > Rules > Rulesets**.
2. Protect `main`.
3. Require a pull request before merging.
4. Require the frontend and backend CI status checks.
5. Prevent force pushes and branch deletion.

Provider dashboards should own deployment secrets for the first deployment. If GitHub Actions later performs deployment directly, create a `production` GitHub Environment and store provider tokens as environment secrets, never as workflow text.

## 6. CI versus deployment

Use two separate responsibilities:

- GitHub Actions validates every pull request and push.
- Render and the single active Cloudflare Worker deploy the `main` branch through their GitHub integrations.

This is the simplest free setup because GitHub does not need Cloudflare or Render API tokens. A merge to `main` triggers provider builds automatically.

The limitation is that a provider can start deployment while the `main` CI run is still executing. Branch protection ensures the pull request passed CI before merge, which keeps this acceptable for the initial demo. A later production-grade pipeline should promote an immutable artifact only after CI succeeds.

Cloudflare preview builds are intentionally disabled for now. Pull requests run GitHub CI only; merging to `main` triggers the production Worker deployment. This avoids dynamic preview origins that are not in the backend's credentialed CORS allowlist.

## 7. Create the Neon PostgreSQL database

1. Create a Neon account and a project named `worksphere`.
2. Select a region close to the Render backend region.
3. Create a production database and dedicated application role.
4. Copy the connection details from Neon's **Connect** dialog.
5. Require TLS with `sslmode=require`.

Spring expects separate variables:

```text
DB_URL=jdbc:postgresql://<neon-host>:5432/<database>?sslmode=require
DB_USERNAME=<application-role>
DB_PASSWORD=<generated-password>
```

Do not paste these values into source files, GitHub issues, documentation, screenshots, or CI logs. Store them in Render's environment settings.

Before using a pooled Neon endpoint with Hibernate/HikariCP, test Flyway migration locking, transaction behavior, and connection lifetime. Start with the connection type recommended by Neon for a long-running JVM service, then load-test before launch.

Neon initially provides combined `DATABASE_URL` and `DATABASE_URL_POOLED` values. Do not paste either combined value into Git or Render for this application. Convert the direct hostname into the JDBC `DB_URL` shown above and keep the username/password separate. If a password is pasted into chat, an issue, a log, or source control, reset it in Neon immediately and update Render; an exposed password must never be reused.

## 8. Deploy the Spring Boot backend to Render

The backend deployment uses `backend/Dockerfile`, which builds the Maven application in one image stage and runs it as a non-root user in a smaller Java 21 runtime stage.

After the Dockerfile and production configuration are committed:

1. In Render, select **New > Web Service**.
2. Connect the GitHub account and select the WorkSphere repository.
3. Choose the `main` branch.
4. Select the **Docker** runtime.
5. Set the repository root or Dockerfile path according to the final Docker layout.
6. Select the Free instance.
7. Set health check path to `/actuator/health`.
8. Add these secret environment variables:

```dotenv
SPRING_PROFILES_ACTIVE=production
DB_URL=jdbc:postgresql://<neon-host>:5432/<database>?sslmode=require
DB_USERNAME=<application-role>
DB_PASSWORD=<generated-password>
FRONTEND_ORIGIN=https://temporary.invalid
```

Use Render's **Add from .env** field or add each value separately. Do not add quotes, `DATABASE_URL`, `DATABASE_URL_POOLED`, or `PORT`. The Spring application expects the four names above, Render provides `PORT`, and the real password must exist only in the Neon/Render secret stores. Replace the temporary origin with the exact Worker URL after the frontend deploys.

Spring must bind to Render's provided port. The eventual production configuration should use:

```yaml
server:
  port: ${PORT:8080}
```

Do not define `PORT` yourself unless there is a specific need; Render provides it. The service must be reachable on `0.0.0.0`.

After deployment, verify:

```powershell
Invoke-RestMethod https://worksphere-api-g3t1.onrender.com/actuator/health
```

The response must report `UP`. Inspect Render logs for successful Flyway validation/migration and database connectivity without printing credentials.

The current public backend health endpoint is:

```text
https://worksphere-api-g3t1.onrender.com/actuator/health
```

The service root intentionally returns HTTP `403`. The foundation security policy exposes only `/actuator/health` and `/actuator/info` until public business APIs and authentication are implemented.

## 9. Deploy the Vite frontend to Cloudflare Workers

After the backend URL exists:

1. In Cloudflare, open **Workers & Pages** and create a Worker from GitHub.
2. Connect the GitHub repository.
3. Name the Worker `worksphere-webapp`; this must match `frontend/wrangler.jsonc`.
4. Select `main` as the production branch.
5. After creation, open **Settings > Build** and configure:

```text
Root directory: /frontend
Build command: npm run build
Deploy command: npx wrangler deploy
```

6. Add these build variables:

```text
NODE_VERSION=22.13.0
VITE_API_BASE_URL=/api/v1
```

`VITE_*` variables are embedded into browser JavaScript at build time. They are public configuration and must never contain secrets.

The repository's `frontend/wrangler.jsonc` publishes `dist`, identifies the Worker as `worksphere-webapp`, sets `not_found_handling` to `single-page-application`, and runs the Worker first for `/api/*`. `frontend/worker/index.ts` proxies that path to Render while keeping browser cookies on the Cloudflare application origin. Do not add a Pages `_redirects` file; Workers rejects the old catch-all rule as an infinite loop when SPA handling is already enabled.

`API_ORIGIN` is a non-secret Worker variable containing the fixed Render origin. Keep secrets such as database credentials and future Google client secrets only in Render; never expose them as `VITE_*` variables. If the Render service URL changes, update `API_ORIGIN` in `wrangler.jsonc`, review it, and redeploy.

Deploy and open the generated `workers.dev` URL. Copy the exact origin and update Render:

```dotenv
FRONTEND_ORIGIN=https://worksphere-webapp.<account-subdomain>.workers.dev
```

Do not include a trailing slash or `/api/v1`. When adding a custom domain later, update the exact backend allowlist and redeploy as required.

### GitHub integration and preview policy

Keep only one Cloudflare project connected to this repository: `worksphere-webapp`. Delete or disconnect obsolete failed Workers such as `worksphere` and `worksphere-web`; otherwise every pull request receives duplicate Cloudflare status checks and deployment attempts.

Under **Settings > Build**, disable preview/non-production branch deployments and retain automatic production deployment for `main`. The intended flow is:

```text
pull request -> GitHub Actions CI only
merge to main -> automatic Render and Cloudflare production deploys
```

### Redeploy the frontend

Normal deployment is automatic after merging to `main`. To retry manually:

```text
Cloudflare > Workers & Pages > worksphere-webapp > Deployments
> Deploy latest commit (or Retry deployment)
```

Successful logs include `vite building for production`, asset upload from `frontend/dist`, and a successful Wrangler deployment.

### Cloudflare troubleshooting

| Symptom | Cause | Resolution |
|---|---|---|
| `Could not read package.json` at `/opt/buildhome/repo/package.json` | Root directory is `/` | Set root directory to `/frontend` under **Settings > Build** |
| `Failed to match Worker name` | Wrangler name differs from the Cloudflare project | Keep both names as `worksphere-webapp` |
| `Invalid _redirects configuration` or infinite loop | Legacy Pages `_redirects` file combined with Workers SPA handling | Remove `frontend/public/_redirects`; use `wrangler.jsonc` SPA handling |
| Multiple Cloudflare checks on every PR | Multiple Workers are connected to the same repository | Keep only `worksphere-webapp` and disable preview builds |
| Wrangler rejects the Node version | Cloudflare is using an older Node runtime | Set build variable `NODE_VERSION=22.13.0` |

## 10. End-to-end live verification

Verify in this order:

```powershell
Invoke-RestMethod https://<render-service>.onrender.com/actuator/health
Invoke-WebRequest https://worksphere-webapp.<account-subdomain>.workers.dev -UseBasicParsing
```

Then use browser developer tools:

1. Open the live frontend.
2. Confirm JavaScript and CSS load without console errors.
3. Confirm API requests use HTTPS and the Render hostname.
4. Confirm there are no mixed-content or CORS errors.
5. Confirm expected unauthorized responses do not leak internal details.
6. Wait for the Render free service to sleep, then test the cold-start experience.

Database checks should confirm that Flyway owns the schema history and that no schema was created through Hibernate. `ddl-auto` must remain `validate`.

## 11. Deployment and rollback routine

Normal release flow:

1. Create a feature branch.
2. Run local checks.
3. Push and open a pull request.
4. Wait for GitHub Actions.
5. Review and merge to `main`.
6. Watch Render and Cloudflare deployments.
7. Run live smoke tests.
8. Record any migration or operational change.

Application rollback:

- prefer provider rollback to a known-good deployment or revert the Git commit and merge normally
- never rewrite an applied Flyway migration
- use a new forward migration to repair database state
- verify whether an application rollback remains compatible with the current database schema

Free-tier database recovery is not a backup strategy. Before storing valuable data, define automated backups, restore testing, retention, and recovery objectives on a plan that supports them.

## 12. Cost and operational constraints

As of November 2026:

- Cloudflare Workers Free supports Git-based builds and static-asset deployments within current plan limits.
- Render Free Web Services sleep after 15 idle minutes, use an ephemeral filesystem, and share monthly free instance-hour limits.
- Render's own Free Postgres expires after 30 days, so it is not selected here.
- Neon Free provides persistent PostgreSQL with usage and storage limits and can scale idle compute to zero.
- GitHub Actions standard hosted runners are free for public repositories; private repositories use included quotas.

Provider limits change. Re-check official pricing and limit pages before every launch or architecture commitment.

## 13. Next implementation milestone

Implement deployment readiness as a separate reviewed slice:

1. Add the first approved Flyway migration when the first domain slice is ready.
2. Maintain Neon/Render production secrets and rotate any exposed credential immediately.
3. Verify Render health after each backend deployment.
4. Keep only the `worksphere-webapp` Cloudflare project connected to GitHub.
5. Update Render's `FRONTEND_ORIGIN` whenever the frontend origin changes.
6. Run and document a live frontend/API/database smoke test.

Do not place real provider credentials in a commit while completing this milestone.

## 14. Official references

- [GitHub Actions billing and usage](https://docs.github.com/en/actions/concepts/billing-and-usage)
- [GitHub deployment environments](https://docs.github.com/en/actions/concepts/workflows-and-actions/deployment-environments)
- [Cloudflare Workers Builds configuration](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/)
- [Cloudflare Workers Static Assets](https://developers.cloudflare.com/workers/static-assets/)
- [Cloudflare SPA routing](https://developers.cloudflare.com/workers/static-assets/routing/single-page-application/)
- [Render Web Services](https://render.com/docs/web-services)
- [Render free service limitations](https://render.com/docs/free)
- [Render Docker deployment](https://render.com/docs/docker)
- [Neon documentation](https://neon.com/docs/)

