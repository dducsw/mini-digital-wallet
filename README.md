# Mini Digital Wallet (core-ledger-service)

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![React 19](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Vite 8](https://img.shields.io/badge/Vite-8-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Auth-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

Modular monolith backend for a mini e-wallet: users, wallets, linked cards, and a double-entry ledger with concurrency safety and idempotency.

![Web UI - Wallet dashboard with top-up, withdraw and P2P transfer](docs/assets/web-ui.png)

## Features

- **Auth + JWT**: register, login, get current user.
- **Wallet**: create, get balance, list by user, change status `ACTIVE / FROZEN / CLOSED` (State Pattern).
- **Payment methods**: link tokenized cards (only masked `**** 4242` stored), list, revoke.
- **Transfers**: top-up, withdraw, P2P, append-only reversals (history is never updated/deleted).
- **Safe ledger**: every transfer writes 2 balanced `DEBIT / CREDIT` lines, money stored as `Long` cents (no float), `SELECT FOR UPDATE` locks in `min -> max` order with deadlock retry.
- **Idempotency**: `Idempotency-Key` header scoped by `(actor, operation, key)`, SHA-256 payload hash, 30s lease anti-stuck, cached replay.

## Tech stack

Java 21, Spring Boot 3.3, Spring Security + JWT, JPA/Hibernate, PostgreSQL 16 + Flyway, Maven. Tests: JUnit 5, Mockito, Testcontainers.

## Project structure

```
com.minidigitalwallet
├── controller/  # REST endpoints
├── service/  # interfaces, logic, LedgerFacade, IdempotencyStorage
├── domain/  # Money, WalletState
├── entity/  # JPA entities
├── repository/  # Spring Data
├── dto/  # request/response records
├── exception/  # errors
├── security/  # JWT
└── resources/db/migration/  # Flyway V1-V5
docker-compose.yml  # Postgres
```

## Quick start

Requirements: JDK 21, Maven 3.9+, Docker.

```bash
# 1. Start Postgres
docker compose up -d

# 2. Run app (Flyway migrates automatically)
mvn spring-boot:run
# App: http://localhost:8080, default DB: localhost:5436/wallet_db
```

## API summary

```
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me

POST /api/v1/wallets
GET  /api/v1/wallets/{id}
GET  /api/v1/wallets/{id}/balance
GET  /api/v1/wallets?user_id=...
PATCH /api/v1/wallets/{id}/status

POST /api/v1/transfers/topup      (header Idempotency-Key)
POST /api/v1/transfers/withdraw
POST /api/v1/transfers/p2p
POST /api/v1/transfers/reversals
```

## Tests

```bash
mvn "-Dtest=TransferServiceTest,WalletServiceTest,PaymentMethodServiceTest,WalletControllerTest,TransferControllerTest,DomainOopTest" test
```

## Load test results (k6)

Scripts: 50 VUs / 30s reads (`GET /api/v1/wallets/{id}/balance`), 20 VUs / 30s writes (`POST /api/v1/transfers/topup`, unique `Idempotency-Key` per iteration). Local run, app + Postgres on one machine.

| Scenario | Reqs | Throughput | Checks passed | Failed | avg | med | p90 | p95 | max |
|---|---|---|---|---|---|---|---|---|---|
| Read balance | 128,572 | 4,284/s | 100% (200) | 0.00% | 11.53ms | 11.18ms | 14.57ms | 17.47ms | 51.15ms |
| Write top-up | 3,397 | 112.6/s | 100% (201) | 0.00% | 176.85ms | 172.58ms | 251.56ms | 280.44ms | 447.30ms |

Reads scale well (4k+ rps, p95 under 20ms). Writes are heavier by design (pessimistic lock + balanced double-entry + idempotency lease in one transaction) at ~113 rps with zero failures and zero duplicates.
