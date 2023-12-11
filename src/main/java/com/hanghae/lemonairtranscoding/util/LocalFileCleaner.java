package com.hanghae.lemonairtranscoding.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class LocalFileCleaner {

	private static final long delete_interval = 1L;
	@Value("${ffmpeg.output.directory}")
	private String outputPath;
	// 동시성 떄문에 사용
	public final ConcurrentHashMap<String, Process> processMap = new ConcurrentHashMap<>();
	private final ScheduledExecutorService deleteFile = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean stopSearching = new AtomicBoolean(true);
	private final AtomicBoolean showMessage = new AtomicBoolean(true);


	public Flux<Path> walkFilesFlux(Path path) {
		try {
			return Flux.fromStream(
				Files.walk(path).filter(file -> file.toString().endsWith(".ts") || file.toString().endsWith(".jpg")));
		} catch (IOException e) {
			log.error("Failed to walk files", e);
			return Flux.empty();
		}
	}


	public void deleteOldTsAndJpgFiles(String owner) {
		if (stopSearching.get()) {
			if (showMessage.getAndSet(false)) {
				log.info("No longer searching for files.");
			}
			return;
		}

		Path directoryPath = Paths.get(outputPath);

		try {
			Flux<Path> dirPaths = Flux.fromStream(Files.list(directoryPath));
			dirPaths.filter(Files::isDirectory)
				.filter(dirPath -> dirPath.toFile().getName().equals(owner))
				.flatMap(dirPath -> {
					List<Path> filesToDelete = new ArrayList<>();
					return walkFilesFlux(dirPath).doOnNext(file -> {
						try {
							BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
							Instant currentInstant = Instant.now();
							Instant fileCreationInstant = attributes.creationTime().toInstant();
							final long elapsedTime = Duration.between(fileCreationInstant, currentInstant).toMinutes();

							if (elapsedTime >= 1) {
								filesToDelete.add(file);
							}
						} catch (IOException e) {
							log.error("Failed to read attributes of file {}", file, e);
						}
					}).doOnComplete(() -> {
						for (Path file : filesToDelete) {
							AtomicBoolean hasFiles = new AtomicBoolean(false);
							try {
								if (file.toString().endsWith(".ts") || file.toString().endsWith(".jpg")) {
									Files.deleteIfExists(file);
									hasFiles.set(true);
								}
							} catch (IOException e) {
								log.error("Failed to delete file {}", file, e);
							}
							if (!hasFiles.get()) {
								stopSearching.set(true);
							}
						}
					}).then().subscribeOn(Schedulers.boundedElastic());
				})
				.subscribe();
		} catch (IOException e) {
			log.error("Failed to list directories", e);
		}
	}

	private void killSubProcess(String owner) {
		// isAlive() - 하위 프로세스가 Process활성 상태인지 테스트
		if (processMap.containsKey(owner) && processMap.get(owner).isAlive()) {
			// 하위 프로세스를 종료
			processMap.get(owner).destroyForcibly();
			processMap.remove(owner);
		}

		stopSearching.set(false);
	}


	public void runWhenFFmpegProcessExit(String owner, Process c) {
		// TODO: 2023-12-05 S3와 연동시 방송 종료시에 S3 안의 데이터를 어떻게 처리할지...
		log.info(owner + " exited with code " + c.exitValue());

		// 아래 코드가 원래 정상적으로 실행되는 환경이였으나 현재는 주석처리하지 않으면 문제가 발생한다.
		// isAlive메서드에서 IllegalThreadStateException 이 발생함 학습이 필요함
		// if (!processMap.get(owner).isAlive()) {
		// 	processMap.remove(owner);
		// }

		// 종료된 프로세스 폴더의 .ts 파일 모두 삭제
		Path ownerDirectory = Paths.get(outputPath, owner);
		log.info(ownerDirectory.toString());
		deleteAllTsAndJpgFiles(ownerDirectory);
		//파일탐색을 중지
		stopSearching.set(true);
	}

	public void setDeleteOldFileTaskSchedule(String owner) {
		deleteFile.scheduleAtFixedRate(() -> deleteOldTsAndJpgFiles(owner), delete_interval, delete_interval,
			TimeUnit.MINUTES);

		killSubProcess(owner);
	}


	// 방송종료 시 남아있는 모든 .ts 파일삭제
	private void deleteAllTsAndJpgFiles(Path dirPath) {
		log.info("deleteAllTsAndJpaFiles 실행");
		try {
			Files.walk(dirPath)
				.filter(file -> file.toString().endsWith(".ts") || file.toString().endsWith(".jpg") || file.toString()
					.endsWith(".m3u8"))
				.forEach(file -> {
					try {
						Files.deleteIfExists(file);
						log.info("File deleted: {}", file);
					} catch (IOException e) {
						log.error("Failed to delete file {}", file, e);
					}
				});
		} catch (IOException e) {
			log.error("Failed to walk files", e);
		}
	}
}
