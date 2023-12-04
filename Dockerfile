FROM ubuntu:22.04


RUN sudo add-apt-repository ppa:savoury1/ffmpeg4 && sudo add-apt-repository ppa:savoury1/ffmpeg5 && sudo apt-get update && sudo apt-get upgrade && sudo apt-get dist-upgrade && sudo apt-get install ffmpeg

ARG JAR_FILE_PATH=build/libs/*.jar

COPY $JAR_FILE_PATH app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]