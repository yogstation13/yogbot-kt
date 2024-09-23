FROM alpine:3.20.3 as compiler

RUN apk add --no-cache openjdk21 gradle

WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src src

RUN gradle --no-daemon --debug bootJar


FROM alpine:3.20.3

RUN apk add --no-cache openjdk21-jre-headless

COPY --from=compiler /app/build/libs/Yogbot-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
