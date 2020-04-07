#!/bin/bash

mvn validate flyway:migrate -X

# create a jar file for br-api.
#mvn clean install
mvn clean install -Dmaven.test.skip=true --settings settings.xml -X

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=prod -jar bi-api*.jar
#cd target && java --enable-preview -jar bi-api*.jar
