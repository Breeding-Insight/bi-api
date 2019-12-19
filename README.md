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

### Developer config

In `src/main/resources/`, make a copy of `application-prod.yml` as `application-dev.yml` (this file is ignored from git) and replace placeholder values.  If you need to override any value that's in `application.yml`, you can do so by specifying the identical structure in your `application-dev.yml` file. 

### Run the app

If running in an IDE (like IntelliJ):

- Create an application run config with the main class being `org.breedinginsight.api.Application`
- Pass VM options of: `--enable-preview --illegal-access=warn -Dmicronaut.environments=dev`

If running as a packaged JAR:
```
java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
```