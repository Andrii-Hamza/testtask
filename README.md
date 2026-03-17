# Two Dockerized Spring Boot Apps

## full flow of the application

Two microservices that work together to process text. A user registers and logs in through auth-api, which returns a JWT token. The user then sends text to a protected endpoint in auth-api. Auth-api forwards the text to data-api (an internal service), which transforms it (reverses and uppercases). Auth-api saves a log of each request to Postgres and returns the result to the user.

## Architecture

- **auth-api** (port 8080) — Handles user registration, login (JWT), and a protected `/api/process` endpoint that calls data-api. Stores users and processing logs in Postgres.
- **data-api** (port 8081) — Stateless text transformer. Accepts requests only from auth-api via `X-Internal-Token` header.
- **Postgres** — Stores `users` and `processing_log` tables.

## How to Run

```bash
mvn -f auth-api/pom.xml clean package -DskipTests
mvn -f data-api/pom.xml clean package -DskipTests
docker compose up -d --build
```

## How to Test

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'

# Login (save the token)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'

# Process (use token from login response)
curl -X POST http://localhost:8080/api/process \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'
```
