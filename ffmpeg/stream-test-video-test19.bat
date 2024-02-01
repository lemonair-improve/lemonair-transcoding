@echo off
ffmpeg -re -i testvideo.mkv -c:v libx264 -preset veryfast -tune zerolatency -c:a aac -strict experimental -f flv rtmp://localhost:1935/test19/2a333392-8470-430a-acfc-6b0f9047e8db
