# ---- Stage de construccion ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests clean package

# ---- Stage de ejecucion ----
FROM eclipse-temurin:21-jre-alpine
RUN apk --no-cache upgrade
RUN addgroup -S servas && adduser -S servas -G servas
WORKDIR /app
COPY --from=build /app/target/*.jar ./app.jar
USER servas
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]