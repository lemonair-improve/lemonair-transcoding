package com.hanghae.lemonairtranscoding.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.springframework.stereotype.Service;

import com.hanghae.lemonairtranscoding.aws.AwsS3Uploader;
import com.hanghae.lemonairtranscoding.transcoding.FFmpegCommandBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranscodeService {

	private static final long delete_interval = 1L;
	private final AwsS3Uploader s3Uploader;
	//썸네일에 일련번호를 부여하도록 하는 obs studio의 썸네일 생성 옵션을 이용하기 위해 _thumbnail_%04d.jpg 와 같이 정의
	private final String THUMBNAIL_SERIAL_NUMBER_POSTFIX = "_thumbnail_%04d.jpg";
	private final String THUMBNAIL_DATETIME_POSTFIX = "_thumbnail_%Y%m%d_%H%M%S.jpg";
	private final String DATETIME_POSTFIX = "%Y%m%d_%H%M%S";
	// 동시성 떄문에 사용
	private final ConcurrentHashMap<String, Process> processMap = new ConcurrentHashMap<>();
	private final ScheduledExecutorService deleteFile = Executors.newSingleThreadScheduledExecutor();
	// private final ScheduledExecutorService uploadExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean stopSearching = new AtomicBoolean(true);
	private final AtomicBoolean showMessage = new AtomicBoolean(true);

	// @Value("${ffmpeg.command}")
	private String template;
	@Value("${ffmpeg.output.directory}")
	private String outputPath;

	@Value("${ffmpeg.exe}")
	private String ffmpegExeFilePath;
	@Value("${ffmpeg.ip}")
	private String inputStreamIp;

	@Value("${upload.s3}")
	private Boolean uploadS3;

	public Mono<Long> startTranscoding(String email, String owner) {
		log.info("streaming server 에서 transcoding server에 접근 owner : " + owner);

		// 싱글 스레드 하나를 부여받아서 일정 주기마다 파일을 삭제하고있다.
		deleteFile.scheduleAtFixedRate(() -> this.deleteOldTsAndJpgFiles(owner), delete_interval, delete_interval,
			TimeUnit.MINUTES);

		// isAlive() - 하위 프로세스가 Process활성 상태인지 테스트
		if (processMap.containsKey(owner) && processMap.get(owner).isAlive()) {
			// 하위 프로세스를 종료
			processMap.get(owner).destroyForcibly();
			processMap.remove(owner);
		}

		// 파일탐색을 다시 시작
		stopSearching.set(false);
		// owner를 위한 썸네일, 비디오 디렉토리가 없다면 생성하고 그 경로를 문자열로 반환한다.

		// String thumbnailOutputPathAndName = Paths.get(getOrCreateThumbnailPath(owner),
		// 	owner + THUMBNAIL_DATETIME_POSTFIX).toString();
		// String videoOutputPath = Paths.get(getOrCreateVideoPath(owner)).toString();
		// log.info("thumbnailOutputPathAndName : " + thumbnailOutputPathAndName);

		// List<String> ffmpegCommand =  new FFmpegCommandBuilder(ffmpegExeFilePath, inputStreamIp, outputPath).getDefault(email, owner);
		FFmpegCommandBuilder b = new FFmpegCommandBuilder(ffmpegExeFilePath, inputStreamIp, outputPath);
		List<String> ffmpegCommand = b.getSplitCommand(owner, b.getOrCreateThumbnailPath(owner));
		// 내장된 ffmepg에 명령을 전달할 processBuilder를
		ProcessBuilder processBuilder = getTranscodingProcess(owner, ffmpegCommand);

		return Mono.fromCallable(processBuilder::start).flatMap(process -> {
			try {
				runOutputWatcherThread(process);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			// onExit() - 프로세스 종료를 위한 CompletableFuture<Process>를 반환
			process.onExit().thenAccept((c) -> {
				// TODO: 2023-12-05 S3와 연동시 방송 종료시에 S3 안의 데이터를 어떻게 처리할지...
				log.info(owner + " exited with code " + c.exitValue());

				if (!processMap.get(owner).isAlive()) {
					processMap.remove(owner);
				}

				// 종료된 프로세스 폴더의 .ts 파일 모두 삭제
				Path ownerDirectory = Paths.get(outputPath, owner);

				// deleteAllTsAndJpgFiles(ownerDirectory);
				// 파일탐색을 중지
				stopSearching.set(true);
			});

			processMap.put(owner, process);
			// 프로세스의 기본 프로세스 ID를 반환합니다. 기본 프로세스 ID는 운영 체제가 프로세스에 할당하는 식별 번호
			return Mono.just(process.pid());
			// 블로킹 IO 태스크와 같은 생명주기가 긴 태스크들에 적합하다.
			// boundedElastic 은 요청 할때마다 스레드 생성 단, 스레드 수 제한
		}).subscribeOn(Schedulers.boundedElastic()); // subscribeOn은 구독이 어느 스레드에서 이루어질지를 선택한다.

	}



	private static void runS3UploadThread(String outputPath){
		// outputPath.
	}
	private static void runOutputWatcherThread(Process process) throws InterruptedException {

		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		// 특정 단어를 감시하고자 하는 문자열
		String prefix = "[hls";

		// 프로세스의 출력을 읽어들이는 스레드 실행
		Thread thread = new Thread(() -> {
			try {
				String line;
				while (true) {
					line = reader.readLine();
					// 더 이상 읽을 로그가 없을 때 대기
					if (line == null) {
						Thread.sleep(1000); // 1초간 대기 후 다시 확인
						continue;
					}
					// 감시하고자 하는 특정 단어가 포함된지 확인

					if (line.contains(prefix)) {
						System.out.println("특정 단어를 발견했습니다: " + line);
						runS3UploadThread(line);
						// 원하는 동작 수행
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});

		// 스레드 시작
		thread.start();
		// 외부 프로세스가 종료될 때까지 대기
		int exitCode = process.waitFor();
		// 스레드가 종료될 때까지 대기
		thread.join();
	}

	private void uploadThumbnail(String line) {
	}

	private void uploadm3u8(String line) {
	}

	private ProcessBuilder getTranscodingProcess(String owner, List<String> splitCommand) {
		/*
		 * ProcessBuilder 클래스의 인스턴스에 정의 된 속성으로 새 프로세스를 만들 수 있다
		 * ProcessBuilder 의 속성을 사용해 새로운 프로세스를 시작합니다.
		 * 새로운 프로세스는 directory() 로 지정된 작업 디렉토리의, environment() 로
		 * 지정된 프로세스 환경을 가지는 command() 로 지정된 커멘드와 인수를 호출
		 */
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
		processBuilder.inheritIO();


		processBuilder.directory(new File(directory.toAbsolutePath().toString()));
		return processBuilder;
	}





	// private void uploadThumbNailS3(String owner){
	// 	Path thumbNailDirectory = Paths.get(outputPath).resolve(owner).resolve("thumbnail");
	// 	String targetThumbNailName = owner
	// 	if(!Files.exists(thumbNailDirectory)){
	// 		log.error(String.format("%s 의 업로드할 썸네일이 존재하지 않습니다.", owner));
	// 		return;
	// 	}
	//
	// }

	private void uploadVideoS3(String owner) {
		Path videoDirectory = Paths.get(outputPath).resolve(owner).resolve("video");
	}

	private Flux<Path> walkFilesFlux(Path path) {
		try {
			return Flux.fromStream(
				Files.walk(path).filter(file -> file.toString().endsWith(".ts") || file.toString().endsWith(".jpg")));
		} catch (IOException e) {
			log.error("Failed to walk files", e);
			return Flux.empty();
		}
	}

	private void deleteOldTsAndJpgFiles(String owner) {
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

	// 방송종료 시 남아있는 모든 .ts 파일삭제
	private void deleteAllTsAndJpgFiles(Path dirPath) {
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