# AGENTS.md

## Scope
- These instructions apply to the whole repository.
- If a subdirectory contains its own `AGENTS.md`, the more specific file wins for that subtree.

## Repository Overview
- Java 13 Maven backend using Micronaut.
- Main application code: `src/main/java/org/breedinginsight`
- Tests: `src/test/java/org/breedinginsight`
- DB migrations: `src/main/resources/db/migration`
- API docs and related materials: `docs`
- Build config: `pom.xml`
- Docker and local setup: `README.md`, `docker-compose.yml`

## Working Rules
- Keep changes narrow and task-focused.
- Prefer fixing root causes over adding one-off workarounds.
- Do not edit `.env`, `.env.test`, `.env.template`, or other secret-bearing config files unless explicitly asked.
- Do not modify `target/`, `.idea/`, or the large SQL dump files unless the task is specifically about them.
- Scope searches to relevant directories instead of scanning the entire repository when possible.
- Do not edit historical Flyway migrations unless explicitly requested. Add a new migration instead.

## Files To Ignore By Default
- `target/`
- `.idea/`

## Validation
- Use Maven for validation.
- Source envs from .env when running mvn commands
- Preferred targeted test command:
  `mvn -Dtest=ClassName test --settings settings.xml`
- Full test command:
  `mvn test --settings settings.xml`
- Full build without tests:
  `mvn clean validate install -D maven.test.skip=true --settings settings.xml`
- Tests may require Docker, Testcontainers, and local services. If validation cannot run, say exactly what blocked it.

## API Change Rules
- If endpoint behavior changes, do not update the relevant API docs or spec files in `docs`, they are no longer being maintained.
- If API code changes, add or update endpoint or integration tests.

## Database Rules
- Schema changes belong in a new Flyway migration under `src/main/resources/db/migration`.
- If schema changes affect generated jOOQ code, run the documented generation flow.

## Testing Guidance
- Prefer targeted tests for the changed area before suggesting a full test run.
- For controller or API changes, look first under `src/test/java/org/breedinginsight/api` and `src/test/java/org/breedinginsight/brapi`.
- For importer work, check `src/test/java/org/breedinginsight/brapps/importer`.

## Response Expectations
- Summarize changed files, validation performed, and any remaining risks.
- If tests or docs updates were skipped, explain why.
