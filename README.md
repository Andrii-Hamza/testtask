# 🔐 Auth JWT Security + Dockerized Transform Microservices

> Two Spring Boot apps walk into a Docker container...
> One checks your ID, the other flips your words upside down.

## What's Inside

```
                                          X-Internal-Token
┌─────────────┐    JWT     ┌───────────┐ ──────────────────▶ ┌───────────┐
│   Client    │ ─────────▶ │ auth-api  │                     │ data-api  │
│             │ ◀───result─│  :8080    │ ◀──── "OLLEH" ────  │  :8081    │
└─────────────┘            └─────┬─────┘                     └───────────┘
                                 │ save log                "hello" → "OLLEH"
                           ┌─────▼─────┐
                           │ Postgres  │
                           │  :5432    │
                           └───────────┘
```

**auth-api** — register, login, get a JWT, send text for processing
**data-api** — reverses & uppercases your text (that's the "transform")
**Postgres 16** — stores users + processing logs

## 🚀 Run

```bash
mvn -f auth-api/pom.xml clean package -DskipTests
mvn -f data-api/pom.xml clean package -DskipTests
docker compose up -d --build
```

## 🧪 Try It

```bash
# 1. Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"neo@matrix.io","password":"redpill"}'
# → 201

# 2. Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"neo@matrix.io","password":"redpill"}'
# → {"token":"eyJhbG..."}

# 3. Process (paste your token below)
curl -s -X POST http://localhost:8080/api/process \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"text":"hello world"}'
# → {"result":"DLROW OLLEH"}
```

## 📦 Tech Stack

| | |
|---|---|
| Java 21 | Spring Boot 4.0.3 |
| Spring Security + JWT | BCrypt passwords |
| PostgreSQL 16 | JPA (Hibernate) |
| Docker + Compose | Multi-stage builds |

## 🔑 Environment

| Variable | Default |
|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/auth_db` |
| `POSTGRES_USER` / `PASSWORD` | `root` / `root` |
| `JWT_SECRET` | dev key (change in prod!) |
| `INTERNAL_TOKEN` | `dev-internal-token` |

## 📂 Structure

```
auth-api/       ← brains (auth + orchestration)
data-api/       ← muscle (text transformation)
docker-compose.yml
```

