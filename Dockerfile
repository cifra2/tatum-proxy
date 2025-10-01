# Build Stage
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY tls ./tls
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/tatum-1.0-SNAPSHOT.jar /app/tatum.jar
# Copy the tls certs
COPY --from=build /app/tls /app/tls
# Be aware to change this, when you change listen port for proxy.
EXPOSE 8443

# Spustenie aplikácie pomocou Vert.x Launchera
CMD ["java", "-jar", "tatum.jar"]
