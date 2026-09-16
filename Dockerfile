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
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 755 /app/docker-entrypoint.sh

ENV DATABASE_PATH=/data/greenwatch.db
VOLUME /data

LABEL org.opencontainers.image.source="https://github.com/DarkPro1337/greenwatch"
LABEL org.opencontainers.image.description="Telegram bot that watches Greenhouse job boards"

# Root only to chown the bind-mounted /data, then drop to uid 1000.
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["/app/bin/greenwatch"]
