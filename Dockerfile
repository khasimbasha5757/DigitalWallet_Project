FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG SERVICE_MODULE

WORKDIR /app

COPY pom.xml .
COPY admin-service/pom.xml admin-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY rewards-service/pom.xml rewards-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY wallet-service/pom.xml wallet-service/pom.xml

RUN mvn -pl ${SERVICE_MODULE} -am dependency:go-offline

COPY . .

RUN mvn -pl ${SERVICE_MODULE} -am clean package -DskipTests && \
    find "/app/${SERVICE_MODULE}/target" -maxdepth 1 -type f -name "*.jar" ! -name "*.jar.original" -exec cp {} /app/app.jar \;

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
