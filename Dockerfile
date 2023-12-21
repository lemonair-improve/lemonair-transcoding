FROM ubuntu:latest

RUN apt-get update && \
    apt-get install -y openjdk-17-jre-headless && \
    apt-get -y update && \
    apt-get install -y ffmpeg

ARG JAR_FILE_PATH=build/libs/*.jar

COPY $JAR_FILE_PATH app.jar

ENTRYPOINT ["java","-Dspring.profiles.active=common,secret,deploy", "-jar", "app.jar"]
