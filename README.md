# BI API

The BI API powers the BI Web, as well as BrAPI powered applications.

The API is built using Java 12 and the Micronaut framework.  The development guide for Micronaut can be found at: https://docs.micronaut.io/latest/guide/index.html

## Docker support
The API can run inside a Docker container using the Dockerfile for building the
API image and the docker-compose.yml to run the container.
```
docker-compose up -d biapi-dev
```

The docker-compose.yml should contain a service for each environment the API is
to be run in: e.g develop, test, staging, and production.  Each service contains
under the environment key public values for environment variables used as params
for the API configuration.

Private values used in each environment are stored in Lastpass and are never
placed in docker-compose.yml and never committed to the repo.  At the root level
of the repo locally create a file called .env and save the Lastpass contents for
"bi-api secrets" in this file.

## Pull Request Criteria

When evaluating a pull request for merge acceptance, verify that the following criteria are met:

* Any changes to API code have a corresponding change to the openAPI specification in the docs folder
* Any changes to the API code have corresponding endpoint tests

## Development Guide

### Prerequisites

1. Java 12 SDK installed
1. Maven installed (or via IDE)

### Project setup
The micronaut-security code is included as a git submodule.  To include the contents of this submodule use the --recurse-submodules flag when cloning the bi-api repo:
```
git clone --recurse-submodules https://<user>@bitbucket.org/breedinginsight/bi-api.git
```
```
mvn clean install
```

### Developer config

In `src/main/resources/`, make a copy of `application-prod.yml` as `application-dev.yml` (this file is ignored from git) and replace placeholder values.  If you need to override any value that's in `application.yml`, you can do so by specifying the identical structure in your `application-dev.yml` file.

### Create the database

Run this docker command in terminal to start up a postgres docker container with the empty bi_db database in it. 

```
docker container run --name bidb -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=bidb -p 5432:5432 -d postgres:11.4
```

You will need to create your user in the database table in order to log in successfully. Under resources/db/migration open the V0.11_create-users.sql file and add another insert line for your user. Alternatively, you can execute the same sql insert statement in the sql executor of your choice. 

```
insert into bi_user (orcid, name) values ('xxxx-xxxx-xxxx-xxxx', '<name>');
```

Flyway will run when you run your bi-api project for the first time. Alternatively, you can run flyway through Maven. 

```
mvn flyway:migrate -X
```

The database with your user data will persist until the docker container is stopped. But, saving your username in the V0.11__create-users.sql file will create your user again when the project is run. 

#### Test database

Run this docker command in terminal to start up a postgres docker container with the empty test database in it. 

```
docker container run --name bitest -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=bitest -p 5433:5432 -d postgres:11.4
```

Run migrate using the test profile to apply the migrates to the test database.

```
mvn flyway:migrate -X -Dtest=true
```

#### Updating Database

If you have run the project and the database once already, you will need to clear your database with the flyway java module and recreate it. 

```
mvn flyway:clean -X
mvn flyway:migrate -X
```

##### Test Database

Clean and migrate using the test profile.

```
mvn flyway:clean -X -Dtest=true
mvn flyway:migrate -X -Dtest=true
```

### Run the app

If running in an IDE (like IntelliJ):

- Create an application run config with the main class being `org.breedinginsight.api.Application`
- Pass VM options of: `--enable-preview --illegal-access=warn -Dmicronaut.environments=dev`

If running as a packaged JAR:
```
java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
```

### Run tests

Tests can be run with the following command:
```
mvn test
```

They are also run as part of the install profile. In IntelliJ you can create test profiles for the tests to get eaily readable output.


