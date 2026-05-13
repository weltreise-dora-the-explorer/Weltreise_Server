# Weltreise Server

Spring Boot WebSocket Server für das Weltreise Spiel – AAU SE2 Gruppe 1.

## Tech Stack

- Java 21 + Spring Boot 3.x
- WebSocket + STOMP (`spring-boot-starter-websocket`)
- Maven
- JUnit 5 + JaCoCo
- SonarCloud Quality Gates

## Setup

```bash
./mvnw package
./mvnw test
./mvnw spring-boot:run
```

## CI/CD

GitHub Actions führt bei jedem Push/PR auf develop automatisch aus:

- Build + Docker Image
- Tests + JaCoCo Coverage Report
- SonarCloud Scan
- Deployment auf se2-demo.aau.at (bei Merge auf main)

## Branch-Workflow

- Feature-Branches: `feature/<beschreibung>`
- Commit-Convention: Conventional Commits
- Merges nur via Pull Request (kein Squash/Rebase)
- `develop` ist der aktive Entwicklungsbranch
- `main` wird nur beim Sprint Review mit `develop` gemergt
