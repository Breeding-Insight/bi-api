# BI API

The BI API powers the BI Web, as well as BrAPI powered applications.

The API is built using Java 12 and the Micronaut framework.  The development guide for Micronaut can be found at: https://docs.micronaut.io/latest/guide/index.html

## Docker support
The API can run inside a Docker container using the Dockerfile for building the
API image and the `docker-compose.yml` to run the container.
```
docker-compose up -d biapi-dev
```

The `docker-compose.yml` should contain a service for each environment the API is
to be run in: e.g develop, test, staging, and production.  Each service contains
under the environment key public values for environment variables used as params
for the API configuration.

Private values used in each environment are stored in Lastpass and are never
placed in docker-compose.yml and never committed to the repo.  At the root level
of the repo locally create a file called .env and save the Lastpass contents for
"bi-api secrets" in this file.

### Running tests in docker container

If you have the docker socket (/var/run/docker.sock) and docker executable folder (/usr/bin/docker)
mounted in your docker-compose.yml file, you will be able to run the biapi tests from within the docker container. 
The default docker-compose file has these mounted. 

The biapi tests use the hosts docker instance to spin up test database containers for integration tests. 

NOTE: Do not run a production container with the docker socket mounted. While useful for testing and
development, mounting the docker socket into a container is a large security risk. The application will
still work fine without the docker socket mounted, but tests will fail. 

To run the tests, use the following command:

```docker exec -it biapi mvn test```

## Pull Request Criteria

When evaluating a pull request for merge acceptance, verify that the following criteria are met:

* Any changes to API code have a corresponding change to the openAPI specification in the `docs` folder
* Any changes to the API code have corresponding endpoint tests

## Development Guide
The following sections provide instructions to get the application running in your local development environment. 
This section is provided as an alternative to running the application in Docker. 

### Prerequisites

1. Java 13 SDK installed
1. Maven installed (or via IDE)

### Project setup
The micronaut-security code is included as a git submodule.  To include the contents of this submodule use the --recurse-submodules flag when cloning the bi-api repo:

```
git clone --recurse-submodules https://<user>@bitbucket.org/breedinginsight/bi-api.git
```

#### Postgres

Run this docker command in terminal to start up a postgres docker container with the empty bi_db database in it. 

```
docker-compose up -d bidb
```

### Building the project

Once you have the project pulled down and your database running, follow these steps to build the project.

#### Setting your environment variables

You will need to specify variables specific to your environment in order to run the project. 

Environment specific project variables are specified in the following files:
- src/build/build.config.properties
- src/main/resources/application-prod.yml
- src/test/application-test.yml
- settings.xml

There are three options to specify these project variables: 
1) Specify environmental variables in your system or terminal session (this is what our docker build does)
2) Specify environmental variables in your IntelliJ run configuration. 
3) Edit the files above directly and replace the placeholders with actual values. 

See the .env file in the project's root directory for a list of environmental variables you will need to specify. 
NOTES: 
1) See the section `Creating admin user` on information of how to set the `ADMIN_ORCID` variable. 
2) If using option 2 (IntelliJ) to specify environment variables, you will need to run all commands through a run configuration in IntelliJ. Running commands in terminal will not exposed the environment variables you set in your run configuration. 

#### Setting environmental variables in your terminal session

If you want to run your mvn or java commands through terminal, you will need to set your environmental variables
in your terminal session or for the system. To set your environment variables for the terminal session
you can use the following command at the beginning of your terminal session:

```source .env && export $(grep --regexp ^[A-Z] .env | cut -d= -f1)```

This will set your variables from your .env file while ignoring comments in the file. 

#### Setting environmental variables in IntelliJ

You can set your environment variables in IntelliJ in two ways: 
1) Go to the run configuration of focus and navigate to the 'Runner' tab, enter environment variables 
in the 'Environment Variables' field.
2) Use the EnvFile plugin, https://plugins.jetbrains.com/plugin/7861-envfile, and use your .env file. 

NOTE: The EnvFile plugin does not work for maven run configurations. 


#### Creating admin user

The project is created with an admin user that is then able to setup the system for end users through the UI. 
The admin user will need to have a valid ORCID account. Once you retrieve the ORCID (through the ORCID site) 
for the admin user, set the `ADMIN_ORCID` variable in the environmental variables (see section above).

#### Install project dependencies

After setting the values for your environment, run:

```
mvn clean validate install -D maven.test.skip=true --settings settings.xml
```

This process will pull down all the application's dependencies to your local machine, 
create the application's database structure (with appropriate data), generate required source files (via JOOQ), 
and finally build the final JAR file. 

The command above will not run unit tests. If you want to run unit tests during the project's dependency installation
remove the `-D maven.test.skip=true` command. Alternatively, you can run the tests separately, see the testing
section below. 

### Run the project

Once the project dependencies are installed successfully, you can run the project. 

#### Creating application.yml

If you chose one of the options to specify your environment variables, either in your system, or in IntelliJ you can skip this step.

Otherwise, If you chose to manually specify your environment variables, you will need to manually specify the values in your application-dev.yml file. 

The How: 

Create a application variable configuration file for the environment you plan to run the project under (```Ex.  application-dev.yml```).
You can reference the `application-prod.yml` file to see what information is needed in the
variable configuration.

#### Start the run

*If running in an IDE (like IntelliJ)*:

- Create an `Application` run config with the main class being `org.breedinginsight.api.Application`
- Pass VM options: `--enable-preview --illegal-access=warn -Dmicronaut.environments=prod`

*If running as a packaged JAR*:

```
java --enable-preview -Dmicronaut.environments=prod -jar bi-api*.jar
```

### Ongoing Development

#### Updating the database

If you have run the project and the database once already, you will need to make sure your database is up to date by running: 

```
mvn validate flyway:migrate -X
```


If you don't care about losing any of the data in the database, run:

```
mvn validate flyway:clean flyway:migrate -X
```

### Generating Java Classes via JOOQ

As structural database changes are made, you will need to re-generate Java classes via JOOQ (data model, base DAOs).  To do so, run:

```
mvn clean generate-sources
```

NOTE: This step is not necessary if a `mvn clean install` is run (see section Install Project Dependencies above).

### Testing

#### Test database

The test database is started as part of the tests in a separate docker container. The JOOQ classes for the tests
are generated from the main database, so this will have to be up and running. The reference to the main database
can be found in `src/build/build.config.properties`. 

### Run tests

If you chose one of the options to specify your environment variables in your system, or IntelliJ you can skip this step.

If you chose to manually specify your environment variables, you will need to manually specify the values in your application-dev.yml file. 

The How: 

Change the values in `src/test/resources/application-test.yml` to be the values for your system. 

Tests can be run with the following command:

```
mvn test

```
They are also run as part of the install profile (if specified). 
In IntelliJ, you can create test profiles for the tests to get easily readable output.

If not manually specifying values in application-test.yml, you will need to set all of the environment variables 
found in the .env file for running the tests as well. 



### Troubleshooting

If you are having errors to the effect of `invalid source release 12 with --enable-preview` and are using IntelliJ, change the jdk to 13 in the following places and it may help:

1. File -> Project Structure -> Project Settings
2. File -> Project Structure -> Modules -> Tab: Sources: Language Level
3. File -> Project Structure -> Modules -> Tab: Dependencies: Module SDK
4. Main -> Preferences -> Compiler -> Java Compiler -> Target bytecode version
5. Run/Debug Configurations (Your run profiles) -> JRE select box

Connection issues during build: 

1. Check that you all of the environment variables in the src/build/build.config.properties file specified. 
2. Make sure you are able to connect to your database outside of the project. 

JOOQ class errors during build: 

1. Make sure your database is up to date. 
2. See troubleshooting on connection issues. 

Micronaut error, variable XXX is not specified: 

1. Make sure your application-dev.yml file exists. 

Installation error for bi-jooq-codegen during build. 

1. Make sure your build command includes `--settings settings.xml`
2. Make sure you specified environment variables for `GITHUB_ACTOR` and `GITHUB_TOKEN`
3. Make sure your `GITHUB_TOKEN` is valid and that you have access to the bi-jooq-codegen repo. 
4. See bi-jooq-codegen to make sure everything is setup correctly for pulling the repository, https://github.com/Breeding-Insight/bi-jooq-codegen. 

Value ${SOME_VARIABLE} is not specified/found/valid. 
1. Check you have the mentioned variable specified in your IntelliJ run configuration or system variables. 
2. Make sure you are running your commands through IntelliJ run configuration if using that option. 

Placeholder ${SOME_VALUE} is not specified/found/valid.
1. Make sure your application-dev.yml and application-test.yml files are up to date with makeApplication-dev.yml.sh and makeApplication-test.yml.sh files. 
