# Multi-stage build for Render / any container host.
# Gradle distribution download can be slow on Render — wrapper networkTimeout is 300s.
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Warm the wrapper/deps layer before copying sources (better cache + clearer failures).
RUN set -eux; \
    for i in 1 2 3; do \
      ./gradlew --version --no-daemon && break; \
      echo "Gradle wrapper download attempt $i failed; retrying..."; \
      sleep $((i * 5)); \
    done

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
