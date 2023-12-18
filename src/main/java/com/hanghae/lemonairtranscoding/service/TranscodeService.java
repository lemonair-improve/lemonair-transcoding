package com.hanghae.lemonairtranscoding.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

	private final LocalFileCleaner localFileCleaner;
	private final FFmpegCommandBuilder fFmpegCommandBuilder;
	private final AwsService awsService;
	private final String PREFIX_SAVED_FILE_LOG = "[hls";
	private final String POSTFIX_TEMP_FILE_EXTENSION = ".tmp";

	private final ScheduledExecutorService thumbnailUploadExecutorService = Executors.newSingleThreadScheduledExecutor();
	Scheduler ffmpegLogReaderScheduler;
	Scheduler awsUploadScheduler;
	Scheduler ffmpegProcessScheduler;

	private Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

	@Value("${ffmpeg.output.directory}")
	private String outputPath;

	@Value("${ffmpeg.exe}")
	private String ffmpegPath;

	@Value("${ffmpeg.ip}")
	private String inputStreamIp;

	@Value("${ffmpeg.debug}")
	private boolean ffmpegDebugMode;

	@Value("${ffmpeg.thumbnail.creation-cycle}")
	private int thumbnailCreationCycle;

	@PostConstruct
	void init() {
		ffmpegLogReaderScheduler = Schedulers.newBoundedElastic(1, 10, "ffmpeg 로그 감시");
		awsUploadScheduler = Schedulers.newBoundedElastic(10, 10, "aws upload");
		ffmpegProcessScheduler = Schedulers.newBoundedElastic(1, 10, "FFmpeg 커맨드 실행");
	}

	public Mono<Long> startTranscoding(String userId) {
		List<String> uploadedFiles = new ArrayList<>();
		// localFileCleaner.setDeleteOldFileTaskSchedule(userId);
		Process process = null;
		try {
			process = startFFmpegProcess(userId);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		UploadToAws(filterFileSavedLog(process));
		scheduleThumbnailUploadTask(userId);
		return Mono.just(1L);
	}

	private Process startFFmpegProcess(String userId) throws IOException {
		return getDefaultFFmpegProcessBuilder(userId).start();
	}

	private Flux<String> filterFileSavedLog(Process process) {
		return Flux.fromStream(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines())
			.publishOn(ffmpegLogReaderScheduler)
			.filter(line -> line.startsWith(PREFIX_SAVED_FILE_LOG))
			.map(this::extractSavedFilePathInLog)
			.map(this::removeTmpInFilename)
			.log();
	}

	private void UploadToAws(Flux<String> logLines) {
		logLines.publishOn(awsUploadScheduler).map(this::uploadVideoFilesToS3).log().subscribe();
	}

	private String uploadVideoFilesToS3(String filePath) {
		try {
			Thread.sleep(100L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return awsService.uploadToS3(filePath);
	}

	private String extractSavedFilePathInLog(String log) {
		return log.substring(log.indexOf('\'') + 1, log.lastIndexOf('\''));
	}

	private String removeTmpInFilename(String filename) {
		return filename.endsWith(POSTFIX_TEMP_FILE_EXTENSION) ?
			filename.substring(0, filename.length() - POSTFIX_TEMP_FILE_EXTENSION.length()) : filename;
	}

	private ProcessBuilder getDefaultFFmpegProcessBuilder(String userId) {
		List<String> ffmpegCommand = fFmpegCommandBuilder.getDefaultCommand(userId);
		return getTranscodingProcess(userId, ffmpegCommand);
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
		processBuilder.directory(new File(directory.toAbsolutePath().toString()));

		if (ffmpegDebugMode) {
			processBuilder.inheritIO();
		}

		return processBuilder;
	}

	private void scheduleThumbnailUploadTask(String userId) {
		ScheduledFuture<?> scheduledThumbnailTask = thumbnailUploadExecutorService.scheduleAtFixedRate(
			() -> uploadThumbnailFileToS3(userId),
			thumbnailCreationCycle + 1, thumbnailCreationCycle, TimeUnit.SECONDS
		);

		scheduledTasks.put(userId, scheduledThumbnailTask);
	}

	private void uploadThumbnailFileToS3(String userId) {
		String filePath =
			outputPath + "/" + userId + "/thumbnail" + "/" + userId + "_thumbnail.jpg";
		if (!Files.exists(Paths.get(filePath))) {
			log.info(filePath + " 해당 썸네일 파일 없음");
			return;
		}

		awsService.uploadToS3(filePath);
	}

	public Mono<Boolean> endBroadcast(String userId) {
		ScheduledFuture<?> scheduledThumbnailTask = scheduledTasks.get(userId);
		if (scheduledThumbnailTask != null && !scheduledThumbnailTask.isDone()) {
			scheduledThumbnailTask.cancel(false);
			scheduledTasks.remove(userId);
		}
		return Mono.just(true);
	}
}