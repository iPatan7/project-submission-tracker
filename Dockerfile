# Project Submission Tracker - runtime image
# The jar is built and tested by Maven first (mvn verify), the image only packages
# that tested jar, so the same artefact goes from the pipeline to every environment.
FROM eclipse-temurin:21-jre-alpine

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="project-submission-tracker" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/iPatan7/project-submission-tracker"

# run as a normal user, not root
RUN addgroup -S tracker && adduser -S -G tracker tracker

WORKDIR /app
COPY --chown=tracker:tracker target/submission-tracker.jar app.jar

ENV APP_PORT=8080 \
    APP_VERSION=${APP_VERSION} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

USER tracker
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
