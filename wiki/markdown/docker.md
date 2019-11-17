# Docker

In RemRem-Generate source code a Dockerfile is provided which helps the developer or user to build the local RemRem-Generate source code repository changes to a Docker image.
With the Docker image user can try-out the RemRem-Generate on a Docker Host or in a Kubernetes cluster.

## Requirements
- Docker 


  Linux: https://docs.docker.com/install/linux/docker-ce/ubuntu/

  
  Windows: https://docs.docker.com/docker-for-windows/install/


## Follow these step to build the Docker image.

1. Change to service directory: 
`cd service`

2. Execute maven package command to build the RemRem-Generate war file:
`mvn package -DskipTests`

This will produce a war file in the "target" folder.



3. Build the Docker image with the war file that was produced from previous step: 


`docker build -t remrem-generate --build-arg URL=./target/generate-service-<version>.war -f src/main/docker/Dockerfile .` 


Now docker image is built with tag "remrem-generate"

## Run Docker image on local Docker Host
To run the produced docker image on the local Docker host, execute this command: 


`docker run -p 8081:8080 --expose 8080 -e server.port=8080 -e logging.level.log.level.root=DEBUG -e logging.level.org.springframework.web=DEBUG -e logging.level.com.ericsson.ei=DEBUG remrem-generate`

RabbitMq and other RemRem-Generate required components need to be running and configured via application properties that is provided to the docker command above. See the application.properties file for all available/required properties:
[application.properties](https://github.com/eiffel-community/eiffel-remrem-generate/blob/master/service/src/main/resources/application.properties)

# Some info of the flags to this command


## RemRem-Generate Spring Properties


<B>"-e server.port=8080"</B> - Is the Spring property setting for RemRem-Generate application web port.


<B>"-e logging.level.root=DEBUG -e logging.level.org.springframework.web=DEBUG -e 
logging.level.com.ericsson.ei=DEBUG"</B> - These Spring properties set the logging level for the RemRem-Generate application. 


It is possible to set all Spring available properties via docker envrionment "-e" flag. See the application.properties file for all available RemRem-Generate Spring properties:


[application.properties](https://github.com/eiffel-community/eiffel-remrem-generate/blob/master/service/src/main/resources/application.properties)


## Docker flags


<B>"-p 8081:8080"</B> - this Docker flag is mapping the containers external port 8081 to the internal exposed port 8080. Port 8081 will be allocated outside Docker host and user will be able to access the containers service via port 8081.


When RemRem-Generate container is running on your local Docker host, RemRem-Generate should be reachable with address "localhost:8081/\<Rest End-Point\>" or "\<docker host ip\>:8081/\<Rest End-Point\>"


Another option to configure RemRem-Generate is to provide the application properties file into the container, which can be made in two ways:
1. Put application.properties file in the container's /deployments folder and run RemRem-Generate:

`docker run -p 8081:8080 --volume /path/to/application.properties:/deployments/application.properties remrem-generate`

2. Put application.properties file in a different folder in container and tell RemRem-Generate where the application.properties is located in the container:

`docker run -p 8081:8080 --volume /path/to/application.properties:/tmp/application.properties -e spring.config.location=/tmp/application.properties remrem-generate`

If you need to pass additional flags to the JVM (like setting a custom
heap size) you can include those flags in the JAVA_OPTIONS environment
variable. See the documentation of the
[fabric8/java-centos-openjdk8-jre][docker-image-docs] Docker image for
full details.

[docker-image-docs]: https://hub.docker.com/r/fabric8/java-centos-openjdk8-jre
