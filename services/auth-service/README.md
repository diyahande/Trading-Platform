# Auth Service (NestJS)

Authentication and account-onboarding backend for the capstone platform.

The service owns:
- User login and JWT issuance
- Refresh-token rotation and replay protection
- Password change flow with token revocation
- Signup request workflow with admin approval/rejection
- Email notifications for onboarding outcomes

## Stack

- NestJS 12
- TypeScript (strict mode)
- PostgreSQL via node-postgres
- bcryptjs password hashing
- jsonwebtoken (HS256)
- Swagger/OpenAPI docs

## Key Endpoints

Base path: /auth

- POST /register
- POST /signup-request
- GET /admin/signup-requests
- PATCH /admin/signup-requests/:id/approve-and-register
- PATCH /admin/signup-requests/:id/reject
- POST /login
- POST /refresh
- POST /change-password
- GET /me


## Security Highlights

### 1) JWT + Role Claims
Access tokens include sub/accountId and roles claims and are verified with issuer and algorithm checks.

### 2) Refresh Token Safety
Refresh tokens are generated as opaque random values, stored hashed, consumed once, and rotated.
Replay attempts trigger revocation of all active refresh tokens for that user.

### 3) Login Throttling
Login endpoint is guarded with in-memory, per-IP throttling.
Rate limits are configurable by environment variables.

### 4) Input Validation
Global ValidationPipe is enabled with:
- whitelist
- forbidNonWhitelisted
- transform

### 5) Redacted Structured Logging
Custom logger and request middleware redact sensitive keys (password, token, authorization, secret, apiKey, and similar variants).

## Signup Approval Workflow (Unique Feature)

Unlike a plain auth service, this project includes an admin review workflow:

1. User submits signup request
2. Admin lists pending requests
3. Admin approves (creates auth user + temp password email) or rejects (reason email)
4. Request state is tracked in auth_signup_requests

This allows account vetting before credentials become active.

## Environment Variables

All values live in the repo-root `.env` (template: repo-root `.env.example`).

Required core values:
- AUTH_SERVICE_PORT
- JWT_SECRET
- JWT_ISSUER
- DATABASE_URL

Login throttle tuning:
- LOGIN_THROTTLE_MAX_ATTEMPTS (default 5)
- LOGIN_THROTTLE_WINDOW_MS (default 60000)
- LOGIN_THROTTLE_BLOCK_MS (default 300000)

Email integration:
- MAIL_HOST
- MAIL_PORT
- MAIL_USER
- MAIL_PASSWORD
- MAIL_FROM

Frontend CORS origin (optional):
- FRONTEND_ORIGIN

## Database Setup

Auth schema SQL lives in auth-service/auth-db.

Apply schema:

```bash
cd auth-service/auth-db
bash apply.sh
```

The script uses DATABASE_URL when available. Otherwise it builds a connection from DB_* values.

## Local Development

Install dependencies:

```bash
npm ci
```

Run service:

```bash
npm run start:dev
```

Production build/start:

```bash
npm run build
npm run start:prod
```

## Tests and Quality

Run tests:

```bash
npm run test
npm run test:e2e
npm run test:cov
```

Run lint:

```bash
npm run lint
```

## Docker

The Dockerfile uses a multi-stage Node 20 Alpine build:
- Builder stage compiles TypeScript
- Runner stage installs production deps only

## Important Notes for Integrations

- Trade API and frontend rely on JWT role/account claims from this service.
- Changing token claim names or auth error shapes can break cross-service flows.

## Security Review Artifact

Sprint security review notes are tracked in:
- security-review/sprint-08-auth-service-review.md

Treat this document as an audit artifact and update it when auth controls change.
