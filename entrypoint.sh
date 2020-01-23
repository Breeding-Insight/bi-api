#!/bin/bash

./src/main/resources/makeFlyway.db.properties.sh
./src/main/resources/makeApplication-prod.yml.sh
./src/main/resources/makeJooq.xml.sh

mvn flyway:migrate -X

# create a jar file for br-api.
# set the variable MAVEN_PROFILE to any of the named profiles defined in pom.xml
# to have maven build that profile
if [[ -n "$MAVEN_PROFILE" ]]; then
    mvn clean install -P $MAVEN_PROFILE
else
    mvn clean install
fi

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
