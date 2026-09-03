FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 qrbot \
    && useradd --system --uid 10001 --gid qrbot --home-dir /app --shell /usr/sbin/nologin qrbot \
    && mkdir -p /var/log/qr-bot \
    && chown qrbot:qrbot /var/log/qr-bot

WORKDIR /app
COPY --chown=qrbot:qrbot app.jar /app/app.jar

USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
