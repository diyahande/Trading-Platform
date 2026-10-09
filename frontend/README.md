# Frontend (Angular Trading UI)

Angular 21 frontend for the capstone trading platform.

This app provides:
- Authentication and session flows (login, signup request, change password)
- Trading views (dashboard, orders, blotter, holdings)
- Admin review UI for signup approval and rejection
- AI chat panel connected to the local AI backend

## Stack

- Angular 21 with standalone components
- TypeScript 5.9
- RxJS 7
- Generated OpenAPI TypeScript client
- Vitest-style unit tests under Angular test runner
- Playwright for end-to-end tests

## Prerequisites

- Node.js 20.19+ (or 22.12+, 24+)
- npm 11+

Check versions:

```bash
node --version
npm --version
```

## Install

```bash
npm ci
```

## Run

```bash
npm start
```


## Build

```bash
npm run build
```

## Test

Unit tests:

```bash
npm test
```

E2E tests:

```bash
npx playwright test
```


## Unique Implementation Details

### 1) Smart API Routing via Interceptor
Relative API calls are routed to the correct backend:

This keeps feature code clean and avoids hardcoding service hosts in every API call.

### 2) Strict Bearer Token Policy
Bearer tokens are attached only for allowed origins and API paths.
This reduces accidental token leakage to third-party endpoints.

### 3) Role-Aware Route Guarding
Routes are protected by a centralized auth guard.
Current temporary rule: admin users are restricted to the admin route only.

### 4) OpenAPI Client as a First-Class Artifact
Generated API client lives in src/app/generated/api-client and is committed.
Generator version is pinned in openapitools.json for repeatable output.

### 5) AI Chat Isolation
AI chat calls are intentionally kept separate from auth/trade traffic and use a dedicated endpoint.

## Project Layout

```text
src/
  app/
    core/        # auth, interceptors, guards, shared infra services
    features/    # pages and feature logic
    shared/      # reusable UI components
    generated/   # OpenAPI-generated client (do not edit manually)
```

## Environment and E2E Notes

Playwright config auto-loads environment values from the repo-root `.env`.

App URLs and the allowed CORS origins are not hardcoded. `scripts/generate-env.mjs` writes `src/app/core/config/app-env.generated.ts` from the repo-root `.env` before `start`, `build`, `watch` and `test` (run it by hand with `npm run generate:env`). That file is git-ignored.

Typical E2E variables include:
- E2E_BASE_URL
- E2E_AUTH_API
- E2E_TRADE_API
- E2E_USERNAME / E2E_PASSWORD
- E2E_ACCOUNT_ID
- E2E_SYMBOL

Do not commit real credentials in shared environments.

## Security Notes

- Do not put server-side secrets in frontend code.
- Frontend must not call Fauxnance directly.
- Keep API keys and signing secrets in backend services only.

## Troubleshooting

### ng serve fails
- Verify Node version compatibility
- Reinstall dependencies: npm ci
- Clear Angular cache: npx ng cache clean

### Login works but data panels fail
- Check browser network tab for /api/* requests

### Admin screens unavailable
- Confirm JWT roles include ADMIN
- Confirm current guard behavior for admin-only access
