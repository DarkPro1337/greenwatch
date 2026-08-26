# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build
WORKDIR /src

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon installDist

FROM eclipse-temurin:26-jre
WORKDIR /app

RUN mkdir -p /data && chown -R 1000:1000 /app /data

COPY --from=build --chown=1000:1000 /src/build/install/greenwatch /app

USER 1000
ENV DATABASE_PATH=/data/greenwatch.db
VOLUME /data

LABEL org.opencontainers.image.source="https://github.com/DarkPro1337/greenwatch"
LABEL org.opencontainers.image.description="Telegram bot that watches Greenhouse job boards"

CMD ["/app/bin/greenwatch"]
