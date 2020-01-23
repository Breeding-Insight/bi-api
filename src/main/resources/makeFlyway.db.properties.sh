#!/bin/bash

# create a flyway.db.properties file with the following params pulled from local
# environment variables:
# DB_SERVER           --- the IP:port of the database server
# DB_NAME             --- the name of the database
# DB_USERNAME         --- the database username
# DB_PASSWORD         --- the database password
cat <<EOF > ./src/main/resources/flyway.db.properties
# This is only needed in order to run flyway through terminal commands.
flyway.url=jdbc:postgresql://$DB_SERVER/$DB_NAME
flyway.user=$DB_USERNAME
flyway.password=$DB_PASSWORD
EOF
