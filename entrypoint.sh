#!/bin/bash

mvn flyway:migrate -X

# create a jar file for br-api
# jar cfm bi-api.jar Manifest.txt ./src/main

# start up the api server
# java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
sleep infinity
