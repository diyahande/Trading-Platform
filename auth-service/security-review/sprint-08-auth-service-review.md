# Security review: auth service

## Header

| Field | Value |
|---|---|
| Service | auth service, Sprint 8 |
| Reviewed by | SE5 Team 4 |
| Date of review | 2026-09-25 |
| Commit reviewed | 697fde8 |
| Version of the OWASP Top Ten used | 2021 |

## Categories

| Category | In scope | Finding | Disposition |
|---|---|---|---|
| A01 Broken access control | Yes | [src/auth/guards/jwt-auth.guard.ts](../src/auth/guards/jwt-auth.guard.ts#L1) correctly uses verified bearer identity, not client parameters. However, [src/auth/token.service.ts](../src/auth/token.service.ts#L16) sets sub to accountId and [src/auth/auth.service.ts](../src/auth/auth.service.ts#L87) uses claims.sub, creating subject ambiguity. | Accepted for Sprint 8. Residual risk: services may treat accountId as principal identity; planned fix is to align sub semantics in Sprint 9. |
| A02 Cryptographic failures | Yes | Password hashing (bcrypt cost 12) and HS256 pinning are implemented in [src/auth/auth.service.ts](../src/auth/auth.service.ts#L43) and [src/auth/token.service.ts](../src/auth/token.service.ts#L30); refresh tokens are stored hashed in [src/auth/auth.repository.ts](../src/auth/auth.repository.ts#L83). Critical issue: signing uses a hard-coded key in [src/auth/token.service.ts](../src/auth/token.service.ts#L23) instead of JWT_SECRET. | Partly mitigated, partly accepted short-term. Residual risk: key exposure and cross-service token verification mismatch; must be fixed before demo sign-off. |
| A03 Injection | Yes | None found. Reviewed queries in [src/auth/auth.repository.ts](../src/auth/auth.repository.ts#L21) to [src/auth/auth.repository.ts](../src/auth/auth.repository.ts#L136); all external values are bound parameters and no SQL concatenation is used. | Mitigated by current repository pattern; team decision is to keep parameterized queries mandatory for new methods. |
| A04 Insecure design | Yes | Refresh replay handling is implemented in [src/auth/auth.service.ts](../src/auth/auth.service.ts#L58) with consume-once and revoke-all-on-replay behavior; registration does not issue tokens in [src/auth/auth.controller.ts](../src/auth/auth.controller.ts#L26). Login throttling is not implemented. | Mitigated for refresh design; accepted for missing throttling. Residual risk: higher credential-stuffing rate until throttle control is added. |
| A05 Security misconfiguration | Yes | Environment checks for secrets/database and normalized validation errors are present in [src/auth/token.service.ts](../src/auth/token.service.ts#L53), [src/auth/auth.repository.ts](../src/auth/auth.repository.ts#L13), and [src/main.ts](../src/main.ts#L11). Hardening gaps remain: root container user in [Dockerfile](../Dockerfile#L1) and no explicit CORS allowlist in [src/main.ts](../src/main.ts#L1). | Accepted for Sprint 8 sequencing. Residual risk: broader runtime/network exposure; scheduled hardening is non-root runtime and explicit CORS policy. |
| A06 Vulnerable and outdated components | Yes | A pinned lock file exists in [package-lock.json](../package-lock.json), but npm audit --omit=dev reported one high vulnerability in jsonwebtoken <=8.5.1. Evidence is recorded in this review. | Accepted temporarily. Residual risk: known JWT dependency exposure until major-version upgrade and compatibility tests are completed. |
| A07 Identification and authentication failures | Yes | Uniform failure path is implemented with DUMMY_PASSWORD_HASH in [src/auth/auth.service.ts](../src/auth/auth.service.ts#L53) and [src/auth/auth.constants.ts](../src/auth/auth.constants.ts#L16), and required guard tests exist in [src/auth/guards/jwt-auth.guard.spec.ts](../src/auth/guards/jwt-auth.guard.spec.ts#L36). Critical mismatch remains: hard-coded signing key vs JWT_SECRET verification. | Mitigated for uniform-failure and guard-test requirements; accepted short-term for key mismatch. Residual risk: auth failures or false confidence during integration; mandatory pre-release fix. |
| A09 Security logging and monitoring failures | Yes | No structured security-event logs were found for login failures, refresh rotation, or replay detection in [src/auth/auth.service.ts](../src/auth/auth.service.ts#L49). No direct credential logging calls were found, but auditability is limited. | Accepted for Sprint 8. Residual risk: weaker incident reconstruction for brute-force/replay; planned mitigation is redacted structured auth-security logs in Sprint 9. |

## Evidence

| Check | How it was performed | Result |
|---|---|---|
| Auth and guard flow review | Manual code review of [src/auth/auth.controller.ts](../src/auth/auth.controller.ts), [src/auth/auth.service.ts](../src/auth/auth.service.ts), [src/auth/guards/jwt-auth.guard.ts](../src/auth/guards/jwt-auth.guard.ts), [src/auth/token.service.ts](../src/auth/token.service.ts) | Confirmed contract route presence, guard usage on /auth/me, replay-revocation behavior, and identified hard-coded signing secret issue |
| Data access safety review | Manual code review of [src/auth/auth.repository.ts](../src/auth/auth.repository.ts) | Confirmed parameterized SQL usage; no user-input string concatenation found |
| Guard test review | Manual review of [src/auth/guards/jwt-auth.guard.spec.ts](../src/auth/guards/jwt-auth.guard.spec.ts) | Confirmed explicit expired-token and wrong-signature coverage |
| Test execution | Ran npm test in auth-service | Failed in local environment due to missing node_modules/jest/bin/jest.js (dependencies not installed in current environment) |
| Build execution | Ran npm run build in auth-service | Failed in local environment with missing dependency/type packages (32 compile errors) |
| Dependency audit | Ran npm audit --omit=dev in auth-service | Found 1 high severity vulnerability affecting jsonwebtoken <=8.5.1 |
| Commit capture | Ran git rev-parse --short HEAD | Commit reviewed: 697fde8 |

## Outstanding items

| Item | Owner | Target date |
|---|---|---|
| Remove hard-coded JWT signing secret and sign with JWT_SECRET from environment | Auth service owners | 2026-09-27 |
| Add login throttling/rate limiting for authentication endpoints | Auth service owners | 2026-10-02 |
| Add structured, redacted security event logs for login fail, refresh replay, and token revocation paths | Auth service owners | 2026-10-02 |
| Upgrade jsonwebtoken to a non-vulnerable major version and re-run test/build | Auth service owners | 2026-09-29 |
| Harden container runtime to non-root user and document CORS allowlist policy | Auth service owners | 2026-10-03 |
