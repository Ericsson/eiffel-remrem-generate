A: Build RemRem-Generate Docker image based on RemRem-Generate artifact from an Artifactory, e.g. Jitpack:
cd (git root dir)/service
docker build -t remrem-generate:<version> --build-arg URL=https://jitpack.io/com/github/eiffel-community/eiffel-remrem-generate/generate-service/<version>/generate-service-<version>.war -f src/main/docker/Dockerfile .



B: Build RemRem-Generate based on local RemRem-Generate source code changes
1. Build RemRem-Generate service artifact:
cd (git root dir)/service
mvn package -DskipTests

2. Build RemRem-Generate Docker image:
cd (git root dir)/service
docker build -t remrem-generate --build-arg URL=./target/generate-service-<version>.war -f src/main/docker/Dockerfile .


