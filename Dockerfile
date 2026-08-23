FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace/telegram-common
COPY telegram-common/pom.xml .
COPY telegram-common/src src
RUN mvn -q -DskipTests install
WORKDIR /workspace/qr-bot
COPY qr-bot/pom.xml .
COPY qr-bot/src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/qr-bot/target/qr-bot-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
