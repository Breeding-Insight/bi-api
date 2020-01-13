#FROM maven:3.6.3-jdk-14
FROM maven:3.6.2-jdk-12

WORKDIR /biapi

#install bi-api source
COPY pom.xml ./
COPY entrypoint.sh ./
COPY ./src ./src/
COPY Manifest.txt ./
COPY ./io-micronaut/jar_files/ ./jar_files

# patch the security module for micronaut
COPY ./micronaut-security ./micronaut-security/
RUN cd micronaut-security && ./gradlew publishToMavenLocal

#ENTRYPOINT ["./entrypoint.sh"]
ENTRYPOINT ["sleep", "infinity"]
