package com.hanghae.lemonairtranscoding.service;

import static com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandConstants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandBuilder;
import com.hanghae.lemonairtranscoding.util.LocalFileCleaner;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranscodeService {

	private final AmazonS3 amazonS3;
	private final LocalFileCleaner localFileCleaner;
	private final String SAVED_FILE_LOG_PREFIX = "[hls";
	private final String TEMP_FILE_EXTENSION_POSTFIX = ".tmp";
	Scheduler ffmpegLogReaderScheduler;
	Scheduler awsUploadScheduler;
	Scheduler ffmpegProcessScheduler;
	@Value("${ffmpeg.output.directory}")
	private String outputPath;
	@Value("${ffmpeg.exe}")
	private String ffmpegPath;
	@Value("${ffmpeg.ip}")
	private String inputStreamIp;
	@Value("${upload.s3}")
	private Boolean uploadS3;
	@Value("${aws.s3.bucket}")
	private String bucket;

	@PostConstruct
	void init() {
		ffmpegLogReaderScheduler = Schedulers.newBoundedElastic(1, 10, "ffmpeg 로그 감시");
		awsUploadScheduler = Schedulers.newBoundedElastic(10, 10, "aws upload");
		ffmpegProcessScheduler = Schedulers.newBoundedElastic(1, 10, "FFmpeg 커맨드 실행");
	}

	public Mono<Long> startTranscoding(String email, String streamerName) {
		List<String> uploadedFiles = new ArrayList<>();
		// log.info("streaming server 에서 transcoding server에 접근 streamerName : " + streamerName);
		localFileCleaner.setDeleteOldFileTaskSchedule(streamerName);

		Mono.fromCallable(() -> {
				Process process = getDefaultFFmpegProcessBuilder(email, streamerName).start();
				return Flux.fromStream(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines())
					.publishOn(ffmpegLogReaderScheduler)
					.filter(line -> line.startsWith(SAVED_FILE_LOG_PREFIX))
					.map(this::getFilePathInLog)
					.map(this::removeTmpInFilename)
					.log()
					.publishOn(awsUploadScheduler)
					.map(this::uploadS3)
					.log()
					.subscribe(line -> uploadFile(line, uploadedFiles));
				}).subscribeOn(ffmpegProcessScheduler).log().subscribe();
		return Mono.just(1L);
	}

	private String uploadS3(String line) {
		try {
			Thread.sleep(100L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		String key = line.substring(outputPath.length() + 1).replaceAll("\\\\", "/");
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, key, new File(line));
		return amazonS3.getUrl(bucket, key).toString();
	}

	private String getFilePathInLog(String log) {
		return log.substring(log.indexOf('\'') + 1, log.lastIndexOf('\''));
	}

	private String removeTmpInFilename(String filename) {
		return filename.endsWith(TEMP_FILE_EXTENSION_POSTFIX) ?
			filename.substring(0, filename.length() - TEMP_FILE_EXTENSION_POSTFIX.length()) : filename;
	}

	private ProcessBuilder getDefaultFFmpegProcessBuilder(String email, String streamerName) {
		List<String> ffmpegCommand = new FFmpegCommandBuilder(ffmpegPath, inputStreamIp,
			outputPath).setInputStreamPathVariable(email)
			.printFFmpegBanner(false)
			.setLoggingLevel(LOGGING_LEVEL_INFO)
			.printStatistic(false)
			.setVideoCodec(VIDEO_H264)
			.setAudioCodec(AUDIO_AAC)
			.useTempFileWriting(false)
			.setSegmentUnitTime(2)
			.setSegmentListSize(SEGMENTLIST_ALL)
			.timeStampFileNaming(true)
			.setOutputType(OUTPUT_TYPE_HLS)
			.createVTTFile(false)
			.setM3U8FileName(streamerName)
			.createThumbnailBySeconds(10)
			.setThumbnailQuality(2)
			.setThumbnailCreatePath(streamerName)
			.build();

		ProcessBuilder processBuilder = getTranscodingProcess(streamerName, ffmpegCommand);
		return processBuilder;
	}

	void uploadFile(String filePath, List<String> uploadedFileList) {
		uploadedFileList.add(filePath);
		uploadedFileList.stream().forEach(System.out::println);
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
		processBuilder.command(splitCommand);
		processBuilder.redirectErrorStream(true);
		// processBuilder.inheritIO();
		processBuilder.directory(new File(directory.toAbsolutePath().toString()));
		return processBuilder;
	}

}