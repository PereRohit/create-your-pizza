# Multi-stage build for catalog-service (Spring Boot 4.1.1 / Java 26)
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests package && \
	cp target/catalog-service-*.jar /workspace/app.jar

FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
