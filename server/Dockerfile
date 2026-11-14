# Stage 1: Build fat JAR
FROM amazoncorretto:25 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :server:buildFatJar --no-daemon

# Stage 2: Runtime image
FROM amazoncorretto:25-alpine-jre
WORKDIR /app
COPY --from=builder /app/server/build/libs/*all.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
