# Build Stage
FROM gradle:8.6-jdk17 AS build
WORKDIR /app
COPY . .
# We use shadowJar to create a fat-jar containing all dependencies
RUN gradle build shadowJar --no-daemon -x test

# Runtime Stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
RUN mkdir -p /app/extensions

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
