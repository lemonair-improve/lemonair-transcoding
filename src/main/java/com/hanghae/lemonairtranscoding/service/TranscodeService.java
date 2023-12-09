package com.hanghae.lemonairtranscoding.service;

import static com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandConstants.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hanghae.lemonairtranscoding.aws.AwsS3Uploader;
import com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandBuilder;
import com.hanghae.lemonairtranscoding.threads.TranscodingCompleteMonitorThread;
import com.hanghae.lemonairtranscoding.util.LocalFileCleaner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranscodeService {

	private final AwsS3Uploader s3Uploader;
	private final LocalFileCleaner localFileCleaner;

	// @Value("${ffmpeg.command}")
	private String template;
	@Value("${ffmpeg.output.directory}")
	private String outputPath;

	@Value("${ffmpeg.exe}")
	private String ffmpegPath;
	@Value("${ffmpeg.ip}")
	private String inputStreamIp;

	@Value("${upload.s3}")
	private Boolean uploadS3;

	public Mono<Long> startTranscoding(String email, String streamerName) {
		TranscodingCompleteMonitorThread transcodingCompleteMonitorThread = new TranscodingCompleteMonitorThread(s3Uploader);
		log.info("streaming server 에서 transcoding server에 접근 streamerName : " + streamerName);
		// 오래된 스트림 파일 삭제 스케쥴링
		localFileCleaner.setDeleteOldFileTaskSchedule(streamerName);

		List<String> ffmpegCommand = new FFmpegCommandBuilder(ffmpegPath, inputStreamIp, outputPath)
			.setInputStreamPathVariable(email)
			.printFFmpegBanner(false)
			.setLoggingLevel(LOGGING_LEVEL_INFO)
			.printStatistic(false)
			.setVideoCodec(VIDEO_H264)
			.setAudioCodec(AUDIO_AAC)
			.useTempFileWriting(false)
			.setSegmentUnitTime(2)
			.setSegmentListSize(15)
			.timeStampFileNaming(true)
			.setOutputType(OUTPUT_TYPE_HLS)
			.createVTTFile(false)
			.setM3U8FileName(streamerName)
			.createThumbnailBySeconds(10)
			.setThumbnailQuality(2)
			.setThumbnailCreatePath(streamerName)
			.build();

		ProcessBuilder processBuilder = getTranscodingProcess(streamerName, ffmpegCommand);

		return Mono.fromCallable(processBuilder::start).flatMap(process -> {

			localFileCleaner.processMap.put(streamerName, process);

			process.onExit().thenAccept((c) -> {
				localFileCleaner.runWhenFFmpegProcessExit(streamerName, c);
			});
			try {
				transcodingCompleteMonitorThread.monitor(process);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}


			// 프로세스의 기본 프로세스 ID를 반환합니다. 기본 프로세스 ID는 운영 체제가 프로세스에 할당하는 식별 번호
			return Mono.just(process.pid());
		}).subscribeOn(Schedulers.boundedElastic()); // subscribeOn은 구독이 어느 스레드에서 이루어질지를 선택한다.
	}

	private ProcessBuilder getTranscodingProcess(String owner, List<String> splitCommand) {
		Path directory = Paths.get(outputPath).resolve(owner);
		// 경로가 폴더인지 확인
		if (!Files.exists(Paths.get(outputPath))) {
			try {
				Files.createDirectory(Paths.get(outputPath));
			} catch (IOException e) {
				log.error("e :" + e);
			}
		}
		ProcessBuilder processBuilder = new ProcessBuilder();
		// processBuilder 의 OS 프로그램과 인수를 설정합니다.
		processBuilder.command(splitCommand);
		// processBuilder 의 redirectErrorStream 속성을 설정
		processBuilder.redirectErrorStream(true);
		// 하위 프로세스 표준 I/O의 소스 및 대상을 현재 Java 프로세스와 동일하게 설정
		// processBuilder.inheritIO();

		processBuilder.directory(new File(directory.toAbsolutePath().toString()));
		return processBuilder;
	}

}