FROM maven:3-openjdk-17-slim as build

WORKDIR /app

COPY pom.xml settings.xml ./
COPY src ./src

# use exec shell form to access secret variable as exported env variable
RUN --mount=type=secret,id=PERSONAL_ACCESS_TOKEN \
   export PERSONAL_ACCESS_TOKEN=$(cat /run/secrets/PERSONAL_ACCESS_TOKEN) && \
   mvn -s settings.xml clean install

FROM openjdk:16
WORKDIR /app
COPY --from=build /app/target/account-rest-service-1.0-SNAPSHOT.jar /app/account-rest-service.jar
EXPOSE 8080

ENTRYPOINT [ "java", "-jar", "/app/account-rest-service.jar"]

LABEL org.opencontainers.image.source https://github.com/sonamsamdupkhangsar/account-rest-service