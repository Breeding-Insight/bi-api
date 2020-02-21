#!/bin/bash

./src/main/resources/makeApplication-prod.yml.sh

mvn validate flyway:migrate -X

# create a jar file for br-api.
mvn clean install

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=prod -jar bi-api*.jar
