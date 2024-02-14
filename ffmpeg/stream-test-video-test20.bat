@echo off
.\ffmpeg -re -i testvideo.mkv -c:v libx264 -preset veryfast -tune zerolatency -c:a aac -strict experimental -f flv rtmp://localhost:1935/test20/b66d404b-5035-4b49-8b38-70238c0ea1f9
