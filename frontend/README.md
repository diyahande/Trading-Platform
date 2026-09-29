# Sprint 9 Frontend — Trading UI

Angular frontend for the Sprint 9 trading application.

## Technology

* Angular 21
* Supported Node.js versions:

  * 20.19+
  * 22.12+
  * 24+
* Standalone Angular components
* Angular Signals
* TypeScript
* REST API clients generated from the committed API contracts

## Prerequisites

Verify the installed versions:

```bash
node --version
npm --version
ng version
```

The project must use Angular 21 and a supported Node.js version.

## Install Dependencies

For a clean and reproducible installation:

```bash
npm ci
```

The committed `package-lock.json` must be used to ensure reproducible dependency installation.

## Development Server

Start the Angular development server with:

```bash
ng serve
```

The application is available at:

```text
http://localhost:4200/
```

## Build

Build the application with:

```bash
npm run build
```

The production build is generated under:

```text
dist/
```

Build output must not be committed to Git.

## Tests

Run the unit tests with:

```bash
npm test
```

Tests use `*.spec.ts` files and should be colocated with the code they test.

Example:

```text
src/app/features/order-ticket/
├── order-ticket.component.ts
├── order-ticket.component.html
└── order-ticket.component.spec.ts
```

## Application Structure

The frontend follows a feature-oriented structure:

```text
src/
├── app/
│   ├── core/
│   │   ├── auth/
│   │   ├── errors/
│   │   ├── guards/
│   │   └── http/
│   │
│   ├── features/
│   │   ├── sign-in/
│   │   ├── dashboard/
│   │   ├── order-ticket/
│   │   └── blotter/
│   │
│   ├── shared/
│   │
│   ├── app.ts
│   ├── app.html
│   ├── app.css
│   ├── app.config.ts
│   └── app.routes.ts
│
├── generated/
│   ├── auth-api/
│   └── trade-api/
│
├── main.ts
├── index.html
└── styles.css
```

### `core/`

Contains application-wide infrastructure and cross-cutting concerns.

Examples include:

* Authentication/session handling
* HTTP interceptors
* Route guards
* Error handling

### `features/`

Contains user-facing application features.

The planned Sprint 9 features are:

* `sign-in/`
* `dashboard/`
* `order-ticket/`
* `blotter/`

Feature-specific components and logic should remain inside their respective feature directories.

### `shared/`

Contains reusable components or utilities that are genuinely shared by multiple features.

### `generated/`

Contains generated TypeScript API clients.

Generated clients are created from:

```text
contracts/trade-api.yaml
contracts/auth-api.yaml
```

Generated output must be committed to the repository and must not be manually edited.

## Angular Engineering Rules

### Standalone Components

The application uses Angular standalone components.

Do not introduce `NgModule`-based application architecture.

### Signals

Angular Signals are used for frontend application state.

### HTTP and Observables

Observables stop at the `HttpClient` boundary.

Do not build the application around Observable-based state management.

### Routing

Routes are defined in:

```text
src/app/app.routes.ts
```

Routes other than sign-in must be protected by the appropriate authentication guard.

### Authentication

The frontend communicates with the real Auth Service.

JWT authentication is handled by the frontend authentication infrastructure.

Bearer tokens must only be sent to configured platform API origins.

The frontend must never send bearer tokens to third-party services or Fauxnance.

### API Communication

Angular communicates with the platform APIs through the configured API clients.

Angular must **never call Fauxnance directly**.

Fauxnance and market-data credentials remain server-side.

## API Client Generation

API clients are generated from the committed contracts:

```text
contracts/trade-api.yaml
contracts/auth-api.yaml
```

The API generator version must be pinned and committed.

Generated client output must be committed to the repository.

Generated files must not be manually edited.

## Secrets and Production Bundle

The frontend must not contain:

* API keys
* JWT secrets
* Passwords
* Market-data credentials
* Other server-side secrets

The production bundle must not contain:

* API keys
* JWT secrets
* Fauxnance/market-data host information that should remain server-side

Angular must never call Fauxnance directly.

## End-to-End Tests

Sprint 9 uses Playwright for the required end-to-end journeys.

The following environment variables are used by the E2E tests:

```text
E2E_BASE_URL
E2E_TRADE_API
E2E_AUTH_API
E2E_USERNAME
E2E_PASSWORD
E2E_ACCOUNT_ID
E2E_SYMBOL
```

The required journeys are:

1. Sign in successfully.
2. Place an order and verify the resulting order flow.

The tests must run against the real application stack rather than replacing the core authentication and order flow with mocks.

## Clean-Machine Verification

A machine that has not previously installed the project must be able to run:

```bash
npm ci
npm run build
npm test
```

successfully using the committed lock file.

## Sprint 9 Development Rules

1. Use Angular 21.
2. Use supported Node.js versions.
3. Use standalone components.
4. Use Angular Signals for application state.
5. Keep Observables at the `HttpClient` boundary.
6. Keep tests beside the code they test.
7. Keep application infrastructure under `core/`.
8. Keep user-facing functionality under `features/`.
9. Keep genuinely reusable code under `shared/`.
10. Keep generated API clients under `src/generated/`.
11. Do not manually edit generated API clients.
12. Protect authenticated routes with route guards.
13. Send bearer tokens only to configured platform API origins.
14. Never send bearer tokens to third-party services or Fauxnance.
15. Angular must never call Fauxnance directly.
16. Do not place secrets in the frontend or production bundle.
17. Use the committed API contracts to generate typed clients.
18. Ensure `npm ci`, `npm run build`, and `npm test` succeed on a clean machine.
