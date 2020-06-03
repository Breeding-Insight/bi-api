#!/bin/bash

#    See the NOTICE file distributed with
#    this work for additional information regarding copyright ownership.
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.

mvn validate flyway:migrate -X

# create a jar file for br-api.
#mvn clean install
mvn clean install -Dmaven.test.skip=true --settings settings.xml -X

# start up the api server
cd target && java --enable-preview -Dmicronaut.environments=prod -jar bi-api*.jar
#cd target && java --enable-preview -jar bi-api*.jar
