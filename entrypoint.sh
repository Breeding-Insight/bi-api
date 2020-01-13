#!/bin/bash

#mvn clean install
#mvn flyway:migrate -X

# write the jooq.xml file using the IP address of the docker
# contianer(POSTGRES_DB) for the BI database
cat <<EOF > ./src/resources/jooq.xml
<configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.12.0.xsd">
    <jdbc>
        <driver>org.postgresql.Driver</driver>
        <url>jdbc:postgresql://$POSTGRES_DB:5432/bidb</url>
        <user>postgres</user>
        <password>postgres</password>
    </jdbc>
    <generator>
        <database>
            <name>org.jooq.meta.postgres.PostgresDatabase</name>
            <includes>.*</includes>
            <inputSchema>public</inputSchema>
            <includeTables>true</includeTables>
            <includeUDTs>true</includeUDTs>
            <includeRoutines>false</includeRoutines>
            <includePrimaryKeys>false</includePrimaryKeys>
            <includeUniqueKeys>false</includeUniqueKeys>
            <includeForeignKeys>false</includeForeignKeys>
            <includeCheckConstraints>true</includeCheckConstraints>
            <includeIndexes>false</includeIndexes>
            <excludes>
                flyway_schema_history
            </excludes>
        </database>

        <strategy>
            <matchers>
                <tables>
                    <table>
                        <pojoClass>
                            <transform>PASCAL</transform>
                            <expression>JOOQ_$0</expression>
                        </pojoClass>
                    </table>
                </tables>
            </matchers>
        </strategy>

        <target>
            <packageName>org.breedinginsight.dao.db</packageName>
            <directory>target/generated-sources/jooq</directory>
        </target>
        <generate>
            <pojos>true</pojos>
            <fluentSetters>true</fluentSetters>
            <javaTimeTypes>true</javaTimeTypes>
        </generate>
    </generator>
</configuration>
EOF

# create a jar file for br-api
# jar cfm bi-api.jar Manifest.txt ./src/main
#mvn package

# start up the api server
# java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
#sleep infinity
