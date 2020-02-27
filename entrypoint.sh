#!/bin/bash

./src/build/makeApplication-dev.yml.sh

mvn validate flyway:migrate -X

# create a jar file for br-api.
#mvn clean install
mvn clean install -Dmaven.test.skip=true

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
#cd target && java --enable-preview -jar bi-api*.jar
