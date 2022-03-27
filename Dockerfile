ARG BUILD_HOME=/gradle_build
FROM gradle:7.4.1-jdk17 AS compile
ARG BUILD_HOME
ENV APP_HOME=$BUILD_HOME
WORKDIR $APP_HOME
COPY --chown=gradle:gradle build.gradle settings.gradle $APP_HOME/
COPY --chown=gradle:gradle src $APP_HOME/src
RUN gradle --no-daemon build bootJar

FROM openjdk:17
ARG BUILD_HOME
ENV APP_HOME=$BUILD_HOME
RUN microdnf remove gnutls glib2 gobject-introspection libpeas gnupg2 libdnf microdnf libmodulemd gpgme librepo
COPY --from=compile $APP_HOME/build/libs/Yogbot-1.0-SNAPSHOT.jar /app.jar
ENTRYPOINT java -jar /app.jar
