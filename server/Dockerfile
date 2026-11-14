FROM amazoncorretto:25-alpine
WORKDIR /app
COPY server/build/libs/*-all.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
