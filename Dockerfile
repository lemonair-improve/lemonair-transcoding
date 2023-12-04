FROM ubuntu:22.04


RUN apt update && apt install -y ffmpeg

ARG JAR_FILE_PATH=build/libs/*.jar

COPY $JAR_FILE_PATH app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]