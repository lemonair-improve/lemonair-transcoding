package com.hanghae.lemonairtranscoding.service;

import static com.hanghae.lemonairtranscoding.util.ThreadSchedulers.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hanghae.lemonairtranscoding.exception.ErrorCode;
import com.hanghae.lemonairtranscoding.exception.ExpectedException;
import com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandBuilder;
import com.hanghae.lemonairtranscoding.util.FutureUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranscodeService {

	private final FFmpegCommandBuilder fFmpegCommandBuilder;
	private final AwsService awsService;
	private final String PREFIX_SAVED_FILE_LOG = "[hls";
	private final String POSTFIX_TEMP_FILE_EXTENSION = ".tmp";
	private final ScheduledExecutorService thumbnailUploadExecutorService = Executors.newSingleThreadScheduledExecutor();
	private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

	@Value("${ffmpeg.output.directory}")
	private String outputPath;

	@Value("${ffmpeg.debug}")
	private boolean ffmpegDebugMode;

	@Value("${ffmpeg.thumbnail.upload-cycle}")
	private int thumbnailUploadCycle;

	public Mono<Boolean> startTranscoding(String userId) {
		// localFileCleaner.setDeleteOldFileTaskSchedule(userId);
		return Mono.just(userId)
			.flatMap(this::startFFmpegProcess)
			.doOnNext(this::filterFileSavedLog)
			.then(scheduleThumbnailUploadTask(userId))
			.log("리턴하는거?")
			.thenReturn(true);
	}

	private Mono<Process> startFFmpegProcess(String userId) {
		return buildTranscodingProcess(userId, fFmpegCommandBuilder.getDefaultCommand(userId)).log("ffmpeg 프로세스 시작")
			.publishOn(IO.scheduler())
			.handle((processBuilder, sink) -> {
				try {
					sink.next(processBuilder.start());
				} catch (IOException e) {
					sink.error(new ExpectedException(ErrorCode.TranscodingProcessNotStarted));
				}
			});
	}

	private Disposable filterFileSavedLog(Process process) {
		return Flux.fromStream(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines())
			.subscribeOn(IO.scheduler())
			.publishOn(COMPUTE.scheduler())
			.log("로그들")
			.filter(line -> line.startsWith(PREFIX_SAVED_FILE_LOG))
			.map(this::extractSavedFilePathInLog)
			.map(this::removeTmpInFilename)
			.publishOn(IO.scheduler())
			.doOnNext(
				filePath -> FutureUtil.setUpFuture(() -> awsService.uploadToS3Async(filePath), "청크 업로드 성공" + filePath,
					"청크 업로드 실패 " + filePath, 3, 100))
			.subscribe();
	}

	private Mono<Void> scheduleThumbnailUploadTask(String userId) {
		String filePath = String.format("%s/%s/thumbnail/%s_thumbnail.jpg", outputPath, userId, userId);
		return Mono.fromRunnable(() -> {
			ScheduledFuture<?> scheduledThumbnailTask = thumbnailUploadExecutorService.scheduleAtFixedRate(
				() -> FutureUtil.setUpFuture(() -> awsService.uploadToS3Async(filePath), "썸네일 업로드 성공" + filePath,
					"썸네일 업로드 실패 " + filePath, 5, 1000), 4, thumbnailUploadCycle, TimeUnit.SECONDS);
			scheduledTasks.put(userId, scheduledThumbnailTask);
		});
	}

	private String extractSavedFilePathInLog(String log) {
		return log.substring(log.indexOf('\'') + 1, log.lastIndexOf('\''));
	}

	private String removeTmpInFilename(String filename) {
		return filename.endsWith(POSTFIX_TEMP_FILE_EXTENSION) ?
			filename.substring(0, filename.length() - POSTFIX_TEMP_FILE_EXTENSION.length()) : filename;
	}

	private Mono<ProcessBuilder> buildTranscodingProcess(String owner, List<String> splitCommand) {
		return Mono.just(Paths.get(outputPath).resolve(owner)).subscribeOn(IO.scheduler()).flatMap(path -> {
			if (!Files.exists(path)) {
				try {
					Files.createDirectory(path);
				} catch (IOException e) {
					return Mono.error(new ExpectedException(ErrorCode.CannotCreateStreamerDirectory));
				}
			}
			return Mono.just(path);
		}).log("processbuilder").flatMap(directory -> Mono.just(new ProcessBuilder()).doOnNext(processBuilder -> {
			processBuilder.command(splitCommand);
			processBuilder.redirectErrorStream(true);
			processBuilder.directory(new File(directory.toAbsolutePath().toString()));
			if (ffmpegDebugMode) {
				processBuilder.inheritIO();
			}
		}));
	}

	public Mono<Boolean> endBroadcast(String userId) {
		return Mono.fromCallable(() -> {
			if (!scheduledTasks.containsKey(userId)) {
				return true;
			}
			ScheduledFuture<?> scheduledFuture = scheduledTasks.get(userId);
			if (scheduledFuture != null) {
				scheduledFuture.cancel(false);
			}
			scheduledTasks.remove(userId);
			log.info(userId + "의 썸네일 업로드 task 종료");
			return true;
		});
	}
}