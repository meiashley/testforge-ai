# TestForge AI

AI-driven API testing platform that automatically generates functional, boundary, exception, and security test cases from OpenAPI/Swagger specs using Anthropic Claude.

## Architecture

```
testforge-ai/
├── core/               # Domain models and service interfaces
├── ai-engine/          # Claude API integration and test generation
├── swagger-parser/     # OpenAPI spec parsing
├── test-runner/        # BDD test execution engine (JUnit 5 + Cucumber)
├── mock-banking-api/   # Demo target: mock financial payment API
└── api-gateway/        # REST API entrypoint for the platform
```

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### Run the Mock Banking API

```bash
cd mock-banking-api
mvn spring-boot:run
```

Swagger UI: http://localhost:8081/swagger-ui.html
OpenAPI JSON: http://localhost:8081/v3/api-docs

### Run with Docker Compose

```bash
docker-compose up
```

## Mock Banking API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/payments | Create a payment |
| GET | /api/payments/{id} | Get payment by ID |
| POST | /api/payments/{id}/refund | Refund a payment |

## Tech Stack

- **Java 21** + Spring Boot 3.2
- **JUnit 5** + RestAssured + Cucumber-JVM (BDD)
- **springdoc-openapi** for OpenAPI 3.0 spec generation
- **swagger-parser** for spec ingestion
- **Anthropic Claude API** for AI test generation
- **PostgreSQL** + Spring Data JPA
- **Docker** + Docker Compose

## Roadmap

- [x] Mock Banking API with OpenAPI 3.0 spec
- [ ] OpenAPI parser module
- [ ] Claude AI test case generator
- [ ] BDD test runner
- [ ] Platform API gateway
- [ ] Web UI dashboard
