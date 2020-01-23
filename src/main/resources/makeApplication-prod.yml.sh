#!/bin/bash

# create a application-prod.xml file with the following params pulled from local environment
# variables:
# API_INTERNAL_PORT   --- the port exposed inside the api docker container
# OAUTH_CLIENT_ID     --- bi client id registered with Orcid
# OAUTH_CLIENT_SECRET --- bi client secret
# JWT_SECRET          --- the single sign-on JWT secret
# JWT_DOMAIN          --- the domain associate with the JWT
# DB_SERVER           --- the IP:port of the database server
# DB_NAME             --- the name of the database
# DB_USERNAME         --- the database username and password
# DB_PASSWORD         --- the database password
cat <<EOF > ./src/main/resources/application-prod.yml
oauth:
  clientId: $OAUTH_CLIENT_ID
  clientSecret: $OAUTH_CLIENT_SECRET

jwt:
  secret: $JWT_SECRET
  cookie:
    secure: true
    domain: $JWT_DOMAIN

server:
  port: $API_INTERNAL_PORT

datasources:
  default:
    url: jdbc:postgresql://$DB_SERVER/$DB_NAME
    username: $DB_USERNAME
    password: $DB_PASSWORD
EOF
