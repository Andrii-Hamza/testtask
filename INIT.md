# INIT — Implementation Blueprint

## 1. Critical Decisions & Rationale

### Build tool: Maven (not Gradle)
The task spec explicitly requires `mvn -f auth-api/pom.xml clean package -DskipTests`.
The existing Gradle scaffold will be replaced. Each sub-project is a standalone Maven project (no parent POM aggregator needed — keeps it simple).

### Spring Boot version: 4.0.3 (Java 21)
Matches the existing `build.gradle`. Spring Boot 4.x uses:
- `SecurityFilterChain` (no more `WebSecurityConfigurerAdapter`)
- Jakarta EE 11 namespace (`jakarta.persistence.*`, `jakarta.servlet.*`)
- `RestClient` (preferred over RestTemplate for synchronous calls)

### JWT library: io.jsonwebtoken:jjwt 0.12.x
Mature, widely used, supports compact JWS. Three artifacts: `jjwt-api`, `jjwt-impl`, `jjwt-jackson`.

### Database init: Flyway migrations
More robust than `init.sql` — tracks schema versions. Single migration file per service keeps it simple.
Fallback: if Flyway adds too much weight, use `schema.sql` with `spring.sql.init.mode=always`.
**Decision: use Flyway.** It's a single extra dependency and one file per service. Worth it.

### Docker: Multi-stage builds
Stage 1: copy pre-built JAR. No Maven build inside Docker (avoids downloading the internet inside the image).
Base image: `eclipse-temurin:21-jre-alpine` for runtime (small footprint).

### Inter-service auth: shared INTERNAL_TOKEN env var
Service A sends `X-Internal-Token: ${INTERNAL_TOKEN}` header.
Service B validates it via a servlet filter. Simple, effective for internal communication.

### Password hashing: BCryptPasswordEncoder (Spring Security built-in)
No external library needed. Strength 10 (default) is fine.

### UUID strategy
Use `@GeneratedValue(strategy = GenerationType.UUID)` — Hibernate 6+ natively supports this.

---

## 2. Final Repo Structure

```
/testtask
├── auth-api/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/testtask/auth/
│       │   ├── AuthApiApplication.java
│       │   ├── config/
│       │   │   └── SecurityConfig.java
│       │   ├── controller/
│       │   │   ├── AuthController.java
│       │   │   └── ProcessController.java
│       │   ├── dto/
│       │   │   ├── AuthRequest.java          (email, password)
│       │   │   ├── AuthResponse.java         (token)
│       │   │   ├── ProcessRequest.java       (text)
│       │   │   └── ProcessResponse.java      (result)
│       │   ├── entity/
│       │   │   ├── User.java
│       │   │   └── ProcessingLog.java
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   └── ProcessingLogRepository.java
│       │   ├── security/
│       │   │   ├── JwtTokenProvider.java
│       │   │   └── JwtAuthenticationFilter.java
│       │   └── service/
│       │       ├── AuthService.java
│       │       └── ProcessService.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│               └── V1__init.sql
├── data-api/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/testtask/data/
│       │   ├── DataApiApplication.java
│       │   ├── controller/
│       │   │   └── TransformController.java
│       │   ├── dto/
│       │   │   ├── TransformRequest.java     (text)
│       │   │   └── TransformResponse.java    (result)
│       │   └── filter/
│       │       └── InternalTokenFilter.java
│       └── resources/
│           └── application.yml
├── docker-compose.yml
└── README.md
```

---

## 3. Dependency Matrix

### auth-api pom.xml
```xml
spring-boot-starter-web          — REST controllers, embedded Tomcat
spring-boot-starter-security     — SecurityFilterChain, BCrypt
spring-boot-starter-data-jpa     — Hibernate, repositories
spring-boot-starter-validation   — @Valid, @Email, @NotBlank
postgresql                        — JDBC driver (runtime)
flyway-core + flyway-database-postgresql — migrations
jjwt-api / jjwt-impl / jjwt-jackson     — JWT create/parse
lombok                            — reduce boilerplate (optional but helpful)
```

### data-api pom.xml
```xml
spring-boot-starter-web          — REST controllers
spring-boot-starter-validation   — @NotBlank
lombok                            — (optional)
```
Data-api is intentionally minimal: no JPA, no Security, no Postgres driver.

---

## 4. Implementation Details — File by File

### 4.1 auth-api

#### `AuthApiApplication.java`
Standard `@SpringBootApplication` main class. Nothing special.

#### `SecurityConfig.java`
```
Key points:
- @Bean SecurityFilterChain
- Permit: POST /api/auth/register, POST /api/auth/login
- All other requests: authenticated
- Stateless session (SessionCreationPolicy.STATELESS)
- Disable CSRF (stateless API)
- Add JwtAuthenticationFilter BEFORE UsernamePasswordAuthenticationFilter
- @Bean PasswordEncoder → BCryptPasswordEncoder
- @Bean RestClient.Builder for calling Service B
```

#### `JwtTokenProvider.java`
```
- Generate token: subject=userId, claims={email}, expiration=24h
- Parse/validate token: extract userId from subject
- Secret key from env: JWT_SECRET (minimum 256-bit for HS256)
- Use Keys.hmacShaKeyFor(secret.getBytes()) for signing
```

#### `JwtAuthenticationFilter.java`
```
- Extends OncePerRequestFilter
- Extract "Bearer <token>" from Authorization header
- If valid: create UsernamePasswordAuthenticationToken, set in SecurityContextHolder
- If invalid/missing: continue chain (Spring Security will reject if endpoint is protected)
- IMPORTANT: do NOT throw exceptions here — let the filter chain handle 401
```

#### `AuthController.java`
```
POST /api/auth/register
- @RequestBody @Valid AuthRequest (email, password)
- Check if email already exists → 409 Conflict
- Hash password, save User, return 201 Created

POST /api/auth/login
- @RequestBody @Valid AuthRequest
- Find user by email → 401 if not found
- Check password with BCrypt → 401 if mismatch
- Generate JWT, return 200 { "token": "..." }
```

#### `ProcessController.java`
```
POST /api/process
- @RequestBody @Valid ProcessRequest (text)
- Extract userId from SecurityContext (set by JwtAuthenticationFilter)
- Delegate to ProcessService
- Return ProcessResponse
```

#### `ProcessService.java`
```
- Inject RestClient, ProcessingLogRepository, INTERNAL_TOKEN from env
- Call Service B: POST http://data-api:8081/api/transform
  - Header: X-Internal-Token = ${INTERNAL_TOKEN}
  - Header: Content-Type = application/json
  - Body: { "text": "..." }
- Parse response → extract "result"
- Save ProcessingLog (userId, inputText, outputText, createdAt=now)
- Return result
```

#### `User.java` (Entity)
```
@Entity @Table(name = "users")
- id: UUID @Id @GeneratedValue(strategy = UUID)
- email: String @Column(unique = true, nullable = false)
- passwordHash: String @Column(name = "password_hash", nullable = false)
```

#### `ProcessingLog.java` (Entity)
```
@Entity @Table(name = "processing_log")
- id: UUID @Id @GeneratedValue(strategy = UUID)
- userId: UUID @Column(name = "user_id", nullable = false)
- inputText: String @Column(name = "input_text", nullable = false)
- outputText: String @Column(name = "output_text", nullable = false)
- createdAt: Instant @Column(name = "created_at", nullable = false)
- @PrePersist → createdAt = Instant.now()
```

#### `V1__init.sql` (Flyway migration)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE processing_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    input_text TEXT NOT NULL,
    output_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_processing_log_user_id ON processing_log(user_id);
```

#### `application.yml`
```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/testtask}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway handles schema; Hibernate only validates
    open-in-view: false
  flyway:
    enabled: true

app:
  jwt:
    secret: ${JWT_SECRET:default-secret-key-for-dev-only-change-in-prod-min-32-chars!!}
    expiration-ms: 86400000   # 24 hours
  internal-token: ${INTERNAL_TOKEN:dev-internal-token}
  data-api-url: ${DATA_API_URL:http://data-api:8081}
```

### 4.2 data-api

#### `DataApiApplication.java`
Standard `@SpringBootApplication`.

#### `TransformController.java`
```
POST /api/transform
- @RequestBody @Valid TransformRequest (text)
- Transform: reverse the string and uppercase it
  - Example: "hello" → "OLLEH"
- Return TransformResponse { "result": "OLLEH" }
```

#### `InternalTokenFilter.java`
```
- Implements jakarta.servlet.Filter (registered as @Component)
- Only applies to /api/transform
- Read X-Internal-Token header
- Compare against env INTERNAL_TOKEN
- If missing/invalid → 403 Forbidden (JSON error body)
- If valid → chain.doFilter()
```

#### `application.yml`
```yaml
server:
  port: 8081

app:
  internal-token: ${INTERNAL_TOKEN:dev-internal-token}
```

### 4.3 DTOs (all are simple records)
```java
// Auth
record AuthRequest(@NotBlank @Email String email, @NotBlank String password) {}
record AuthResponse(String token) {}

// Process
record ProcessRequest(@NotBlank String text) {}
record ProcessResponse(String result) {}

// Transform (data-api)
record TransformRequest(@NotBlank String text) {}
record TransformResponse(String result) {}
```
Using Java records: immutable, concise, no Lombok needed for DTOs.

---

## 5. Docker Setup

### auth-api/Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### data-api/Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml
```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: testtask
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  data-api:
    build: ./data-api
    ports:
      - "8081:8081"
    environment:
      INTERNAL_TOKEN: super-secret-internal-token

  auth-api:
    build: ./auth-api
    ports:
      - "8080:8080"
    environment:
      POSTGRES_URL: jdbc:postgresql://postgres:5432/testtask
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      JWT_SECRET: my-super-secret-jwt-key-that-is-at-least-32-characters-long
      INTERNAL_TOKEN: super-secret-internal-token
      DATA_API_URL: http://data-api:8081
    depends_on:
      postgres:
        condition: service_healthy
      data-api:
        condition: service_started

volumes:
  pgdata:
```

Key Docker decisions:
- `depends_on` with healthcheck ensures Postgres is ready before auth-api starts
- auth-api depends on data-api so data-api starts first
- Same default Docker network (compose creates one automatically)
- Named volume `pgdata` for persistence across restarts

---

## 6. Potential Pitfalls & Mitigations

| Pitfall | Mitigation |
|---------|------------|
| Spring Security blocks everything by default | Explicitly permit auth endpoints in SecurityFilterChain |
| JWT filter throws exception → ugly 500 | Filter catches exceptions, sets 401 response manually |
| Flyway fails if tables already exist from JPA auto-create | Set `ddl-auto: validate` (never `create` or `update`) |
| RestClient throws on 4xx/5xx from Service B | Wrap in try-catch, return meaningful error to client |
| `gen_random_uuid()` requires Postgres 13+ | We use Postgres 17 — no issue |
| Spring Boot 4.x changed security defaults | CSRF disabled explicitly; no `authorizeRequests()` (use `authorizeHttpRequests()`) |
| Docker build fails if JAR not pre-built | README clearly states to run `mvn package` first |
| JSON parse error returns stack trace | Add `@RestControllerAdvice` with basic error handler |
| CORS blocks browser requests | Not required by task (curl-only), skip CORS config |
| Hibernate lazy loading outside transaction | `open-in-view: false` + fetch in service layer |

---

## 7. Implementation Order

Execute in this exact order to minimize backtracking:

```
Phase 1: Project scaffolding
  1. Clean up existing Gradle files (remove src/, build.gradle, settings.gradle, etc.)
  2. Create auth-api/pom.xml with all dependencies
  3. Create data-api/pom.xml with minimal dependencies
  4. Create directory structures for both projects

Phase 2: Data-api (simpler, no dependencies on auth-api)
  5. DataApiApplication.java
  6. TransformRequest.java + TransformResponse.java (records)
  7. TransformController.java
  8. InternalTokenFilter.java
  9. application.yml

Phase 3: Auth-api entities & database
  10. AuthApiApplication.java
  11. V1__init.sql (Flyway migration)
  12. User.java entity
  13. ProcessingLog.java entity
  14. UserRepository.java
  15. ProcessingLogRepository.java
  16. application.yml

Phase 4: Auth-api security
  17. JwtTokenProvider.java
  18. JwtAuthenticationFilter.java
  19. SecurityConfig.java

Phase 5: Auth-api business logic
  20. DTOs: AuthRequest, AuthResponse, ProcessRequest, ProcessResponse
  21. AuthService.java
  22. AuthController.java
  23. ProcessService.java
  24. ProcessController.java

Phase 6: Error handling
  25. GlobalExceptionHandler.java (@RestControllerAdvice) in auth-api

Phase 7: Docker
  26. auth-api/Dockerfile
  27. data-api/Dockerfile
  28. docker-compose.yml

Phase 8: Documentation
  29. README.md
```

---

## 8. Validation Checklist (Post-Implementation)

Run these curl commands after `docker compose up -d`:

```bash
# 1. Register — expect 201
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'

# 2. Register duplicate — expect 409
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'

# 3. Login — expect 200 + token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}' | jq -r '.token')

# 4. Process without auth — expect 401
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/process \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'

# 5. Process with auth — expect 200 + transformed text
curl -s -X POST http://localhost:8080/api/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'

# 6. Direct call to data-api without token — expect 403
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/transform \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'

# 7. Direct call to data-api with token — expect 200
curl -s -X POST http://localhost:8081/api/transform \
  -H "X-Internal-Token: super-secret-internal-token" \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'

# 8. Check processing_log has a row
docker compose exec postgres psql -U postgres -d testtask -c "SELECT * FROM processing_log;"
```

All 8 checks must pass before submission.
