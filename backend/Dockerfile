# --- Build stage -------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -o dependency:go-offline || ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q clean package -DskipTests

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system chronobeat && useradd --system --gid chronobeat --home /app chronobeat
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R chronobeat:chronobeat /app
USER chronobeat

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
