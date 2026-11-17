# Build stage: compile and package the service.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core core
COPY sim sim
COPY service service
RUN ./gradlew --no-daemon :service:bootJar -x test

# Run stage: JRE only, non-root.
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home feedlab
WORKDIR /app
COPY --from=build /src/service/build/libs/service-*.jar app.jar
USER feedlab
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
