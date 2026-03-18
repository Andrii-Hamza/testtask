# Project Overview

Two Spring Boot microservices with PostgreSQL and Docker, implementing auth + text transformation.

## Architecture

- **auth-api** (port 8080): Spring Boot 4.0.3 + Security + JPA. Handles registration, login (JWT), and a protected `/api/process` endpoint that calls data-api internally.
- **data-api** (port 8081): Spring Boot 4.0.3 (Web only, no DB). Exposes `/api/transform` — reverses and uppercases text. Validates requests via `X-Internal-Token` header.
- **PostgreSQL 16**: Stores `users` and `processing_log` tables. Schema managed by JPA `ddl-auto: update` (no Flyway).

## Repo Structure

```
/auth-api          — Spring Boot (Web + Security + JPA + JWT)
/data-api          — Spring Boot (Web only, stateless)
docker-compose.yml — Postgres + auth-api + data-api
```

## Build & Run

```bash
mvn -f auth-api/pom.xml clean package -DskipTests
mvn -f data-api/pom.xml clean package -DskipTests
docker compose up -d --build
```

## Key Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/register` | none | `{ email, password }` → 201 |
| POST | `/api/auth/login` | none | `{ email, password }` → `{ token }` |
| POST | `/api/process` | Bearer JWT | `{ text }` → `{ result }` (calls data-api, logs to DB) |
| POST | `/api/transform` | X-Internal-Token | `{ text }` → `{ result }` (internal only) |

## Data Model

- **users**: `id` (UUID), `email` (unique), `password_hash`
- **processing_log**: `id` (UUID), `user_id`, `input_text`, `output_text`, `created_at`

## Security

- Passwords hashed with BCrypt (strength 10)
- JWT tokens (HMAC-SHA, 76s expiry) for auth-api endpoints
- `X-Internal-Token` header for service-to-service auth (data-api filter)
- Spring Security: `/api/auth/*` permitAll, everything else requires authentication
- Session policy: STATELESS

## Environment Variables

| Variable | Default | Used By |
|----------|---------|---------|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/auth_db` | auth-api |
| `POSTGRES_USER` | `root` | auth-api |
| `POSTGRES_PASSWORD` | `root` | auth-api |
| `JWT_SECRET` | hardcoded dev key | auth-api |
| `INTERNAL_TOKEN` | `dev-internal-token` | both services |
| `DATA_API_URL` | `http://data-api:8081` | auth-api |

## Key Tech Decisions

- Java 21, Spring Boot 4.0.3
- No Flyway — JPA `ddl-auto: update` manages schema
- `UserRepository.findByEmail()` returns nullable `User` (not `Optional<User>`)
- data-api is stateless (no database dependency)
- Multi-stage Docker builds (Maven + JRE 21)
- Transform logic: reverse string + uppercase

## Request Flow

```
Client → POST /api/process (JWT) → auth-api
  → JwtFilter validates token, loads user
  → ProcessService calls POST http://data-api:8081/api/transform (X-Internal-Token)
  → data-api reverses + uppercases text
  → auth-api saves ProcessingLog to Postgres
  → returns { result } to client
```
