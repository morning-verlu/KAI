FROM bellsoft/liberica-openjre-alpine:17

ARG KAIOS_VERSION=0.3.1
ARG KAIOS_RELEASE_URL=https://github.com/morning-verlu/KAI/releases/download/v${KAIOS_VERSION}/kaios-${KAIOS_VERSION}.zip

RUN apk add --no-cache ca-certificates curl git unzip \
    && curl -fsSL "$KAIOS_RELEASE_URL" -o /tmp/kaios.zip \
    && unzip -q /tmp/kaios.zip -d /opt \
    && ln -s "/opt/kaios-${KAIOS_VERSION}" /opt/kaios \
    && rm /tmp/kaios.zip

WORKDIR /workspace
ENV KAIOS_MODEL_PROVIDER=mock
ENV KAIOS_RUNS_DIR=/workspace/.kaios/runs
ENV KAIOS_ARTIFACTS_DIR=/workspace/artifacts
ENV PATH=/opt/kaios/bin:$PATH

COPY examples /workspace/examples
COPY README.md START_HERE.md LICENSE /workspace/

ENTRYPOINT ["kaios"]
CMD ["tour"]
