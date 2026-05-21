FROM docker.io/library/eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY . .
RUN chmod +x gradlew
RUN ./gradlew -Dorg.gradle.wrapper.networkTimeout=120000 clean bootJar -x test

FROM docker.io/library/eclipse-temurin:21-jre-jammy AS runner

ARG USER_NAME=tk-adpro
ARG USER_UID=1000
ARG USER_GID=${USER_UID}

RUN groupadd -g ${USER_GID} ${USER_NAME} \
    && useradd -m -d /opt/tk-adpro -u ${USER_UID} -g ${USER_GID} ${USER_NAME}

USER ${USER_NAME}
WORKDIR /opt/tk-adpro
COPY --from=builder --chown=${USER_UID}:${USER_GID} /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java"]
CMD ["-jar", "app.jar"]
