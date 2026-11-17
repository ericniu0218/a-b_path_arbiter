# Build stage: compile the service and generate a small synthetic ITCH file for demos.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core core
COPY sim sim
COPY service service
RUN ./gradlew --no-daemon :service:bootJar :sim:installDist -x test
# Ten minutes of made-up Add Order traffic starting at 09:30:00, so a hosted copy has something to replay.
# Real Nasdaq files are not in the image; mount them and set FEEDLAB_DATA_DIR to use them.
RUN mkdir /demo-data \
    && sim/build/install/sim/bin/sim generate /demo-data/synthetic-10min.itch --seconds 600 --rate 500 --seed 7 \
    && gzip -9 /demo-data/synthetic-10min.itch

# Run stage: JRE only, non-root.
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home feedlab
WORKDIR /app
COPY --from=build /src/service/build/libs/service-*.jar app.jar
COPY --from=build --chown=feedlab /demo-data /app/data
ENV FEEDLAB_DATA_DIR=/app/data
USER feedlab
EXPOSE 8080
# Size the heap from the container's memory limit; small hosted plans are often 512 MB.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
