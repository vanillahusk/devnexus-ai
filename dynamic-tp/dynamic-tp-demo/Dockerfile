FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml pom.xml
COPY dynamic-tp-spring-boot-starter/pom.xml dynamic-tp-spring-boot-starter/pom.xml
COPY dynamic-tp-demo/pom.xml dynamic-tp-demo/pom.xml
COPY dynamic-tp-spring-boot-starter/src dynamic-tp-spring-boot-starter/src
COPY dynamic-tp-demo/src dynamic-tp-demo/src

RUN mvn -q -pl dynamic-tp-demo -am clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/dynamic-tp-demo/target/dynamic-tp-demo-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
