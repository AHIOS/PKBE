# Multi-stage build for Render / any container host.
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
