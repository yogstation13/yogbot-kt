FROM alpine:3.18.2 as compiler

RUN apk add --no-cache openjdk17 gradle

WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src src

RUN gradle --no-daemon bootJar


FROM alpine:3.18.2

RUN apk add --no-cache openjdk17-jre-headless

COPY --from=compiler /app/build/libs/Yogbot-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
