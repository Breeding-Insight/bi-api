#!/bin/bash

cd micronaut-security && ./gradlew publishToMavenLocal && cd ..
mvn flyway:migrate -X
mvn clean install

# create a jar file for br-api
if [ -n "$PROFILE"]; then
    mvn package -P $PROFILE
else
    mvn package
fi

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
