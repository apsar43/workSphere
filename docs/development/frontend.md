# Frontend Development Guide

This guide documents the WorkSphere frontend foundation, why each tool is present, how it is configured, and the commands needed for normal development and troubleshooting.

## 1. Technology and versions

| Tool | Version | Purpose |
|---|---:|---|
| Node.js | 22.13.0 recommended | JavaScript runtime used in CI |
| npm | 10+ | Package installation and scripts |
| React / React DOM | 19.3.1 | UI framework |
| React Router | 7.18.5 | Client-side routing |
| TanStack Query | 5.107.1 | Server-state and request lifecycle management |
| Vite | 6.4.6 | Development server and production bundler |
| TypeScript | 5.8.3 | Static type checking |
| Vitest | 5.1.0 | Unit/component test runner |
| React Testing Library | 16.3.3 | User-focused component testing |
| User Event | 14.6.9 | Realistic test interactions |
| ESLint | 9.39.5 | Static analysis |
| Prettier | 3.9.10 | Consistent formatting |

The complete dependency tree is pinned by `frontend/package-lock.json`. Use `npm ci` in CI and when reproducing the lockfile exactly.

## 2. Directory structure

```text
frontend/
├── src/
│   ├── app/          Application providers and layout
│   ├── assets/       Static application assets when needed
│   ├── components/   Reusable presentation components
│   ├── features/     Feature-owned pages and behavior
│   ├── hooks/        Shared React hooks when needed
│   ├── lib/          Configured libraries such as QueryClient
│   ├── routes/       Router and route-level pages
│   ├── services/     API/environment integration
│   ├── styles/       Global styles and future design tokens
│   ├── test/         Shared test setup
│   └── types/        Shared TypeScript types when needed
├── eslint.config.js
├── vite.config.ts
├── tsconfig*.json
├── package.json
└── package-lock.json
```

Do not create empty feature layers. Add a directory when it has a real responsibility.

## 3. Initial installation

From the repository root in PowerShell:

```powershell
Set-Location frontend
node --version
npm --version
npm ci
```

Required Node version: 22.12 or newer. CI uses 22.13.0.

If the machine-level npm registry is incorrectly configured with HTTP:

```powershell
npm config get registry
npm install --registry=https://registry.npmjs.org/
npm audit --registry=https://registry.npmjs.org/
```

Prefer correcting the user-level npm configuration rather than adding registry configuration to the project.

## 4. Start and preview the frontend

Start the Vite development server:

```powershell
Set-Location frontend
npm run dev
```

The default URL is normally `http://localhost:5173`.

Start on a specific host and port:

```powershell
npx vite --host=127.0.0.1 --port=4173
```

Create and preview the production bundle:

```powershell
npm run build
npm run preview
```

`npm run build` performs TypeScript checking before Vite creates `frontend/dist/`.

## 5. Available npm commands

| Command | Result |
|---|---|
| `npm run dev` | Start the Vite development server |
| `npm run build` | Typecheck and create the production bundle |
| `npm run preview` | Serve the production bundle locally |
| `npm run lint` | Run ESLint with zero warnings allowed |
| `npm run lint:fix` | Apply safe ESLint fixes |
| `npm run format` | Format supported files with Prettier |
| `npm run format:check` | Verify formatting without changing files |
| `npm run typecheck` | Run the TypeScript project build in no-emit mode |
| `npm run test` | Run all tests once with Vitest |
| `npm run test:watch` | Start Vitest in watch mode |
| `npm run test:coverage` | Run all tests and create V8 coverage output |

There is no `npm run vitest` script. Use `npm run test`, `npm run test:watch`, or call `npx vitest` directly when advanced Vitest options are needed.

## 6. Running frontend tests

Run every test once:

```powershell
npm run test
```

Run tests continuously while editing:

```powershell
npm run test:watch
```

Run one test file:

```powershell
npm run test -- src/components/Button/Button.test.tsx
```

Run one test by its test name:

```powershell
npm run test -- -t "invokes its click handler"
```

Run one file in watch mode:

```powershell
npm run test:watch -- src/features/home/HomePage.test.tsx
```

Run coverage:

```powershell
npm run test:coverage
```

Coverage is written to `frontend/coverage/` and is ignored by Git. The current foundation deliberately has no global coverage threshold; add one when the product has enough meaningful behavior for the threshold to be useful.

## 7. Test configuration

`vite.config.ts` contains the Vitest configuration:

- `jsdom` simulates a browser DOM.
- `globals: true` provides `describe`, `it`, `expect`, and `vi`.
- `src/test/setup.ts` loads Testing Library DOM matchers.
- V8 creates text and HTML coverage reports.
- Application source is included in coverage; the bootstrap entry point and generated Vite types are excluded.

Tests should validate behavior visible to a user. Prefer accessible queries such as `getByRole` over implementation details or CSS selectors.

Example:

```tsx
render(<Button onClick={onClick}>Continue</Button>);
await user.click(screen.getByRole('button', { name: 'Continue' }));
expect(onClick).toHaveBeenCalledOnce();
```

## 8. TypeScript configuration

Strict checking is enabled through `tsconfig.app.json`:

- `strict`
- unused locals and parameters
- unchecked indexed-access protection
- implicit override checks
- no emitted JavaScript during type checking
- bundler-style module resolution

The `@/*` alias maps to `frontend/src/*` in both TypeScript and Vite.

Example import:

```ts
import { Button } from '@/components/Button/Button';
```

Run TypeScript independently:

```powershell
npm run typecheck
```

## 9. ESLint configuration

`eslint.config.js` uses ESLint's flat configuration format. It enables:

- ESLint recommended rules
- type-aware TypeScript rules
- TypeScript stylistic rules
- React Hooks rules
- React Fast Refresh rules
- consistent type-only imports
- unused-variable checking

Type-aware linting is limited to `.ts` and `.tsx` files so configuration JavaScript is not incorrectly parsed as a TypeScript project.

Run ESLint:

```powershell
npm run lint
```

Apply supported fixes and then review the diff:

```powershell
npm run lint:fix
git diff
```

## 10. Prettier configuration

`.prettierrc.json` selects:

- single quotes
- semicolons
- trailing commas
- 100-character print width

Format all supported frontend files:

```powershell
npm run format
```

CI uses the non-mutating command:

```powershell
npm run format:check
```

## 11. Environment variables

Copy the example only when an override is needed:

```powershell
Copy-Item .env.example .env.local
```

Current variable:

```dotenv
VITE_API_BASE_URL=/api/v1
```

All `VITE_*` variables are compiled into browser code. Never place passwords, API secrets, session identifiers, private keys, or database credentials in them.

Read environment configuration through `src/services/environment.ts` rather than accessing `import.meta.env` throughout the application.

## 12. Full local frontend verification

Run the same quality sequence used by CI:

```powershell
Set-Location frontend
npm ci
npm run format:check
npm run lint
npm run typecheck
npm run test
npm run build
npm audit --registry=https://registry.npmjs.org/
```

## 13. Adding a frontend feature later

For a real feature:

1. Create `src/features/<feature-name>/`.
2. Keep feature-specific components, hooks, API calls, schemas, and tests together.
3. Add the route deliberately in `src/routes/router.tsx`.
4. Use TanStack Query for remote server state, not duplicated component state.
5. Add accessible component tests.
6. Run format, lint, typecheck, tests, and build before review.

