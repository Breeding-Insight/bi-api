# BI API

The BI API powers the BI Web, as well as BrAPI powered applications.

The API is built using Java 12 and the Micronaut framework.  The development guide for Micronaut can be found at: https://docs.micronaut.io/latest/guide/index.html

## Development Guide

### Prerequisites

1. Java 12 SDK installed
1. Maven installed (or via IDE)

### Project setup

```
mvn clean install
```

#### Get latest micronaut-security code

Micronaut-security 1.2.2 has a bug in the JWT validation code so the latest code from github must be used to fix this problem.

```
git clone https://github.com/micronaut-projects/micronaut-security.git
```

Build micronaut-security using:

```
./gradlew publishToMavenLocal
```

If using IntelliJ, press Ctrl+Shigt+A to open actions and type reimport to find the option to reimport all maven projects. Execute a maven run configuration that does a clean install.

### Developer config

In `src/main/resources/`, make a copy of `application-prod.yml` as `application-dev.yml` (this file is ignored from git) and replace placeholder values.  If you need to override any value that's in `application.yml`, you can do so by specifying the identical structure in your `application-dev.yml` file.

### Create the database

Run this docker command to start up a postgres docker container with the empty bi_db database in it. 

```
docker container run --name bidb -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=bidb -p 5432:5432 -d postgres:11.4
```

Then run this flyway command to create the database (You will have to have maven installed for this)

```
mvn flyway:migrate -X
```

### Run the app

If running in an IDE (like IntelliJ):

- Create an application run config with the main class being `org.breedinginsight.api.Application`
- Pass VM options of: `--enable-preview --illegal-access=warn -Dmicronaut.environments=dev`

If running as a packaged JAR:
```
java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
```
