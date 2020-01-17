#!/bin/bash

mvn flyway:migrate -X

create a jar file for br-api
if [[ -n "$PROFILE" ]]; then
    mvn clean install -P $PROFILE
else
    mvn clean install
fi

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
