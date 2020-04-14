FROM maven:3.6.3-jdk-13

ARG HOST_USER_ID=1001
ARG HOST_GROUP_ID=1001
ARG CONT_USERNAME="host"
ARG CONT_GROUPNAME="host"

# create container user with same id as the host user
#RUN groupadd -g ${HOST_GROUP_ID} ${CONT_GROUPNAME}
#RUN useradd -l -u ${HOST_USER_ID} -g ${CONT_GROUPNAME} ${CONT_USERNAME}
#RUN install -d -m 0755 -o ${CONT_USERNAME} -g ${CONT_GROUPNAME} /home/${CONT_USERNAME}

# switch to host user
#USER ${CONT_USERNAME}

# Switch to the working directory
# NOTE: be sure to make the working directory explictly. If you let the WORKDIR
# command make it then it will be owned by root
#RUN ["mkdir", "/home/host/biapi"]
WORKDIR /home/${CONT_USERNAME}/biapi

#install bi-api source
COPY pom.xml ./
COPY entrypoint.sh ./
COPY settings.xml ./
COPY ./src ./src/
COPY ./io-micronaut/jar_files/ ./jar_files

ENTRYPOINT ["/bin/bash", "./entrypoint.sh"]
