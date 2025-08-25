FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
# Debug wrapper presence:
RUN ls -la .mvn/wrapper && ls -la mvnw || true
RUN chmod +x mvnw && ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
