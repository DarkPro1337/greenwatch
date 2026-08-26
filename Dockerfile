# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build
WORKDIR /src

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon installDist

FROM eclipse-temurin:26-jre
WORKDIR /app

RUN useradd --system --uid 1000 --create-home greenwatch \
    && mkdir -p /data \
    && chown -R greenwatch:greenwatch /app /data

COPY --from=build --chown=greenwatch:greenwatch /src/build/install/greenwatch /app

USER greenwatch
ENV DATABASE_PATH=/data/greenwatch.db
VOLUME /data

LABEL org.opencontainers.image.source="https://github.com/DarkPro1337/greenwatch"
LABEL org.opencontainers.image.description="Telegram bot that watches Greenhouse job boards"

CMD ["/app/bin/greenwatch"]
