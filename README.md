# Mini Digital Wallet (core-ledger-service)

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![React 19](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Vite 8](https://img.shields.io/badge/Vite-8-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Auth-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

Modular monolith backend for a mini digital wallet: identity & users, multi-status wallets, linked payment cards, and an immutable double-entry ledger with concurrency safety, rate-limiting, and database-backed idempotency.

![Web UI - Wallet dashboard with top-up, withdraw and P2P transfer](docs/assets/web-ui.png)

## Features

- **Auth & JWT**: Register, login (rate-limited: 5 fails/15m => HTTP 429), 15-min access token + 7-day rotating refresh token (reuse kills all sessions, `POST /auth/logout` revokes, `POST /auth/logout-all` kills every device). 256-bit HEX HMAC secret. Rate limiters are in-memory single-instance (lost on restart, not shared across replicas — use Redis for multi-replica).
- **Roles & Authorization**: `USER` (default) and `ADMIN` travel as a JWT claim; `/api/v1/admin/**` requires `ROLE_ADMIN` (403 otherwise). Promote an ops account with `UPDATE users SET role = 'ADMIN' WHERE username = '<ops-user>';` — the change applies on the next login/refresh (at most one 15-min token lifetime).
- **Transaction PIN**: 6-digit PIN required on debits (`X-Transaction-Pin`), 15m lockout after 5 fails. Users without a PIN must set one first (403 `pin-setup-required`).
- **Transaction Limits**: Hard caps at $10,000/tx and $50,000/day of cumulative outflow per wallet. The daily check runs inside the ledger transaction while the wallet row lock is held, so concurrent debits cannot race past it; reversals (refunds) and internal SYSTEM accounts are exempt.
- **Saved Contacts**: 12-digit recipient directory with nicknames and favorites for quick P2P.
- **Statements & CSV**: Filter by type/date, pagination, and paged CSV export (1000 rows/page, flushed per page — no full in-memory load).
- **Wallets**: 12-digit account numbers, real-time balance, status state machine (`ACTIVE / FROZEN / CLOSED`).
- **Payment Methods**: PCI-DSS tokenized card linking (masked `**** 4242` only), owner-scoped.
- **Transfers & Reversals**: Top-up, withdraw, P2P, and single-use append-only reversals (`UNIQUE` DB lock).
- **Ownership (BOLA)**: Strict caller-ownership checks across all resources (HTTP 403).
- **Double-Entry Ledger**: Balanced `DEBIT/CREDIT` pairs in `Long` cents, ordered pessimistic locks, deadlock retry.
- **Nightly Reconciliation**: Automated 2 AM audit (`balance == sum(CREDIT) - sum(DEBIT)` + global zero-sum) in 2 set-based queries, independent of wallet count.
- **Idempotency**: Scoped `Idempotency-Key` with SHA-256 payload hash and 30s auto-recovery lease.

## Tech Stack

Java 21, Spring Boot 3.3, Spring Security + JWT, JPA/Hibernate, PostgreSQL 16 + Flyway, Maven, React 19 + Vite 8. Tests: JUnit 5, Mockito, Testcontainers.

## Project Structure

```
com.minidigitalwallet
├── controller/  # REST endpoints (Auth, Wallet, Transfer, Ledger, Contact, Reconciliation)
├── service/     # WalletService, TransferService, LedgerService/Facade, ContactService,
│                # TransactionPinValidator, TransactionLimitValidator (LedgerGuard),
│                # PinRateLimiter, IdempotencyService, CallerProvider, ReconciliationService
├── domain/      # Money, AccountNumber (12-digit account number), WalletState
├── entity/      # JPA entities (User, Wallet, PaymentMethod, LedgerEntry, Contact, ...)
├── repository/  # Spring Data JPA repositories
├── dto/         # Request and response records
├── exception/   # Custom domain errors (RFC 7807 Problem Details)
├── security/    # JWT filters, OwnershipGuard, CurrentUser
└── resources/db/migration/  # Flyway V1-V11 (V8: pin_hash, V9: contacts, V10: refresh tokens, V11: user role)
docker-compose.yml  # Local PostgreSQL 16 setup
```

## Quick Start

Requirements: JDK 21, Maven 3.9+, Docker, Node.js 20+.

```bash
# 0. Set JWT secret (REQUIRED — application fails fast if missing; 64+ hex characters = 256 bits)
# Generate: openssl rand -hex 32
export APP_JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970  # dev only

# 1. Start PostgreSQL
docker compose up -d

# 2. Run backend service (Flyway automatically applies migrations V1-V11)
mvn spring-boot:run
# App runs at: http://localhost:8080, default database: localhost:5436/wallet_db
# Public health endpoints: /actuator/health, /actuator/info

# 3. Run Web Dashboard
cd web && npm install && npm run dev
# Web runs at: http://localhost:5173
```

## API Summary

```
POST /api/v1/auth/register
POST /api/v1/auth/login            (5 failed attempts/15min => 429 too-many-requests)
POST /api/v1/auth/refresh          (rotates refresh token; reuse => 401 + all sessions revoked)
POST /api/v1/auth/logout           (revokes one refresh token)
POST /api/v1/auth/logout-all       (revokes every session, authenticated)
GET  /api/v1/auth/me
POST /api/v1/auth/pin              (setup or change 6-digit transaction PIN)

POST /api/v1/wallets               (owner_id in body ignored — wallet always belongs to caller)
GET  /api/v1/wallets/{id}          (403 if not owned by caller)
GET  /api/v1/wallets/{id}/balance  (403 if not owned by caller)
GET  /api/v1/wallets/account/{accountNumber}   (recipient preview: account_number + currency only, no balances)
GET  /api/v1/wallets?user_id=...   (lists only caller-owned wallets)
PATCH /api/v1/wallets/{id}/status  (403 if not owned by caller)

POST /api/v1/transfers/topup      (header: Idempotency-Key)
POST /api/v1/transfers/withdraw   (headers: X-Transaction-Pin, Idempotency-Key)
POST /api/v1/transfers/p2p        (headers: X-Transaction-Pin, Idempotency-Key)
POST /api/v1/transfers/reversals  (403 if caller not party to transaction; 409 if already reversed)

GET  /api/v1/ledger/transactions/{id}
GET  /api/v1/ledger/transactions/reference/{referenceId}
GET  /api/v1/ledger/wallets/{walletId}/entries
GET  /api/v1/ledger/wallets/{walletId}/statement         (filters: type, from, to; pagination)
GET  /api/v1/ledger/wallets/{walletId}/statement/export  (direct CSV streaming)

GET    /api/v1/contacts            (list saved beneficiaries)
POST   /api/v1/contacts            (save new beneficiary)
DELETE /api/v1/contacts/{id}       (remove beneficiary)
PATCH  /api/v1/contacts/{id}/favorite (toggle favorite flag)

POST /api/v1/admin/reconciliation/run   (ROLE_ADMIN only)
GET  /api/v1/admin/reconciliation/runs (ROLE_ADMIN only; latest 50 reconciliation runs)
```

## Documentation

### Core Business & Architecture
- **Core Business & Banking**: [docs/BUSINESS.md](docs/BUSINESS.md) — Double-entry bookkeeping, 11 financial invariants, and edge case safety.
- **REST API Specification**: [docs/API.md](docs/API.md) — Comprehensive endpoints, conventions, headers, and RFC 7807 error details.
- **Setup & Quickstart**: [docs/SETUP.md](docs/SETUP.md) — 4-step local startup guide, cURL cheat sheet, and troubleshooting.

### Computer Science & Financial Engineering Concepts
- [Technical Foundations](docs/concepts/spring-boot-base.md) — IEEE 754 vs `Long` cents, Java 21 `record`, Hibernate lifecycle, and Deadlock prevention.
- [Double-Entry Bookkeeping](docs/concepts/double-entry-bookkeeping.md) — Pacioli (1494), zero-sum conservation, and append-only event streams.
- [Concurrency & Deadlocks](docs/concepts/concurrency-and-deadlocks.md) — ACID isolation, Coffman's 4 conditions, DAG lock ordering, and Spring AOP proxy.
- [Two-Tier Idempotency](docs/concepts/two-tier-idempotency.md) — IETF draft standard, distributed leases, and SHA-256 cryptographic request fingerprinting.
- [Security & Authorization](docs/concepts/security-and-authorization.md) — BCrypt adaptive cost factor, RFC 6819 token rotation, and OWASP BOLA defense.


### Architectural & Service Diagrams
- [Overview Architecture](docs/diagram/overview.md) — Layered monolith architecture and core transaction flow.
- [Double-Entry Ledger](docs/diagram/ledger.md) — Balanced journal lines, deterministic lock ordering, and AOP retry facade.
- [Wallet Lifecycle](docs/diagram/wallet.md) — State pattern (`ACTIVE`, `FROZEN`, `CLOSED`) and onboarding sequence.
- [Authentication & JWT](docs/diagram/authentication.md) — Stateless JWT filter chain, login/register, and user principal.
- [Payment Methods](docs/diagram/payment-method.md) — PCI-DSS mock tokenization, card lifecycle, and BOLA defense.
- [Saved Contacts](docs/diagram/contact.md) — 12-digit account number recipient directory and favorite prioritization.
- [Two-Tier Idempotency](docs/diagram/idempotency.md) — SHA-256 payload hashing, 30s lease auto-recovery, and replay mechanism.
- [Security Guards](docs/diagram/security-guards.md) — Daily limits under row-lock (anti-TOCTOU), PIN rate limiter, and refresh token rotation.
- [Nightly Reconciliation](docs/diagram/reconciliation.md) — 2 AM automated audit job with set-based $O(1)$ zero-sum verification.

## Tests

```bash
mvn test   # 100 tests: unit tests (H2/Mockito) + concurrency/integration tests (Testcontainers PostgreSQL 16)
```

## Load Test Results (k6)

Scripts: 50 VUs / 30s reads (`GET /api/v1/wallets/{id}/balance`), 20 VUs / 30s writes (`POST /api/v1/transfers/topup`, unique `Idempotency-Key` per iteration). Local run with application and PostgreSQL hosted on the same machine.

| Scenario | Reqs | Throughput | Checks passed | Failed | avg | med | p90 | p95 | max |
|---|---|---|---|---|---|---|---|---|---|
| Read balance | 128,572 | 4,284/s | 100% (200) | 0.00% | 11.53ms | 11.18ms | 14.57ms | 17.47ms | 51.15ms |
| Write top-up | 3,397 | 112.6/s | 100% (201) | 0.00% | 176.85ms | 172.58ms | 251.56ms | 280.44ms | 447.30ms |

Reads scale efficiently (4,000+ rps, p95 under 20ms). Writes are deliberately transactional (pessimistic lock + balanced double-entry + idempotency lease within a single transaction) achieving ~113 rps with zero failures and zero duplicates.
