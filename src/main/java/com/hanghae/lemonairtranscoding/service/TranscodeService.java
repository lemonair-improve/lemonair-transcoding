package com.hanghae.lemonairtranscoding.service;

import java.io.File;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hanghae.lemonairtranscoding.aws.AwsS3Uploader;

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
	// 동시성 떄문에 사용
	private final ConcurrentHashMap<String, Process> processMap = new ConcurrentHashMap<>();
	private final ScheduledExecutorService deleteFile = Executors.newSingleThreadScheduledExecutor();
	private final ScheduledExecutorService uploadExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean stopSearching = new AtomicBoolean(true);
	private final AtomicBoolean showMessage = new AtomicBoolean(true);
	// @Value("${ffmpeg.command}")
	private String template;
	@Value("${stream.directory}")
	private String outputPath;
	@Value("${ffmpeg.directory.windows}")
	private String ffmpegExeFilePath;
	@Value("${ffmpeg.ip}")
	private String ffmpegIp;
	@Value("${upload.s3}")
	private Boolean uploadS3;

	public Mono<Long> startTranscoding(String owner) {
		log.info("streaming server 에서 transcoding server에 접근 owner : " + owner);

		// 싱글 스레드 하나를 부여받아서 일정 주기마다 파일을 삭제하고있다.
		// s3에 업로드할거면 얘 대신 해야할듯?
		deleteFile.scheduleAtFixedRate(() -> this.deleteOldTsAndJpgFiles(owner), delete_interval, delete_interval,
			TimeUnit.MINUTES);

		// uploadExecutor.scheduleAtFixedRate(() -> this.)
		// isAlive() - 하위 프로세스가 Process활성 상태인지 테스트
		if (processMap.containsKey(owner) && processMap.get(owner).isAlive()) {
			// 하위 프로세스를 종료
			processMap.get(owner).destroyForcibly();
			processMap.remove(owner);
		}

		// 파일탐색을 다시 시작
		stopSearching.set(false);

		// owner를 위한 썸네일, 비디오 디렉토리가 없다면 생성하고 그 경로를 문자열로 반환한다.
		String thumbnailOutputPathAndName = Paths.get(getOrCreateThumbnailPath(owner),
			owner + THUMBNAIL_SERIAL_NUMBER_POSTFIX).toString();
		String videoOutputPath = Paths.get(getOrCreateVideoPath(owner)).toString();

		log.info("thumbnailOutputPathAndName : " + thumbnailOutputPathAndName);

		List<String> splitCommand = getSplitCommand(owner, videoOutputPath, thumbnailOutputPathAndName);
		// 내장된 ffmepg에 명령을 전달할 processBuilder를
		ProcessBuilder processBuilder = getTranscodingProcess(owner, splitCommand);
		return Mono
			.fromCallable(processBuilder::start)
			.flatMap(process -> {
				// onExit() - 프로세스 종료를 위한 CompletableFuture<Process>를 반환
				process.onExit().thenAccept((c) -> {
					// TODO: 2023-12-05 S3와 연동시 방송 종료시에 S3 안의 데이터를 어떻게 처리할지...
					log.info(owner + " exited with code " + c.exitValue());
					if (!processMap.get(owner).isAlive()) {
						processMap.remove(owner);
					}
					// 종료된 프로세스 폴더의 .ts 파일 모두 삭제
					Path ownerDirectory = Paths.get(outputPath, owner);
					deleteAllTsAndJpgFiles(ownerDirectory);
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

	private void uploadThumbnail(String line) {
	}

	private void uploadm3u8(String line) {
	}

	private ProcessBuilder getTranscodingProcess(String owner, List<String> splitCommand) {
		// CreateProcess error=5, 액세스가 거부되었습니다. 오류 발생시 command가 잘못된 경우


		/*
		 * 상대경로를 절대경로로 변경, processBuilder 의 작업 디렉토리를 설정
		 * 이후, 이 객체의 start() 메서드로 시작된 서브 프로세스는 이 디렉토리를 작업 디렉토리로서 사용
		 */
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

	private List<String> getSplitCommand(String owner, String videoOutputPath, String thumbnailOutputPathAndName) {
		// String command = String.format(template, address + "/" + owner, owner + "_%v/data%d.ts", owner + "_%v.m3u8", thumbnailOutputPathAndName);
		//  -i rtmp://localhost:1935: 입력으로 사용될 RTMP 소스의 URL을 지정합니다. 여기서는 localhost의 1935 포트를 사용합니다.
		//  -c:v libx264: 비디오 스트림에 대한 비디오 코덱을 libx264로 설정합니다. libx264는 H.264 비디오 코덱을 나타냅니다.
		// 	-c:a aac: 오디오 스트림에 대한 오디오 코덱을 aac로 설정합니다. AAC는 Advanced Audio Coding의 약자로, 오디오 압축을 위한 코덱입니다.
		// 	-hls_time 10: HLS 세그먼트의 시간을 10초로 설정합니다. 이는 HLS 세그먼트의 길이를 나타냅니다.
		// 	-hls_list_size 6: HLS 재생목록(.m3u8 파일)에 포함될 세그먼트의 최대 개수를 6으로 설정합니다. 새로운 세그먼트가 생성되면, 재생목록에 최대 6개까지만 유지됩니다.
		//  C:\Users\sbl\Desktop\ffmpegoutput\byeongryeol.m3u8: HLS 스트리밍의 출력 디렉토리 및 재생목록 파일의 경로를 지정합니다. 여기서는 byeongryeol.m3u8이라는 재생목록 파일이 생성되며, 세그먼트 파일들은 해당 디렉토리에 저장됩니다.

		// ffmpeg -i rtmp://localhost:1935 -c:v libx264 -c:a aac -hls_time 10 -hls_list_size 6 C:\Users\sbl\Desktop\ffmpegoutput\byeongryeol.m3u8
		String command = String.format("%s "
				+ "-i %s "
				+ "-c:v libx264 "
				+ "-c:a aac "
				+ "-hls_time 10 "
				+ "-hls_list_size 6 "
				+ " -strftime 1 %s/%s.m3u8 "
				// + "-vf fps=1/10 -q:v 2 %s",
				+ "-vf fps=1/10 -strftime 1 -q:v 2 %s",
			ffmpegExeFilePath            // TranscodingApplciation 기준 로컬의 ffmpeg 실행 파일 위치
			, ffmpegIp + "/" + owner + "@gmail.com"    // obs studio 스트리머가 방송 송출 영상을 보내고 있는 url
			, videoOutputPath            // 저장될 위치, 현재는 local -> aws S3
			, owner
			, thumbnailOutputPathAndName
		);

		log.info(command);

		List<String> splitCommand = new ArrayList<>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(command);
		while (regexMatcher.find()) {
			if (regexMatcher.group(1) != null) {
				// 큰따옴표 없이 큰따옴표로 된 문자열을 추가하세요
				splitCommand.add(regexMatcher.group(1));
			} else if (regexMatcher.group(2) != null) {
				// 작은따옴표 없이 작은따옴표로 된 문자열을 추가하세요
				splitCommand.add(regexMatcher.group(2));
			} else {
				// 따옴표 없는 단어를 추가하세요
				splitCommand.add(regexMatcher.group());
			}
		}
		return splitCommand;
	}

	private String getOrCreateThumbnailPath(String owner) {
		Path thumbnailDirectory = Paths.get(outputPath).resolve(owner).resolve("thumbnail");
		if (!Files.exists(thumbnailDirectory)) {
			try {
				Files.createDirectories(thumbnailDirectory);
			} catch (IOException e) {
				log.error("e :" + e);
			}
		}
		return thumbnailDirectory.toAbsolutePath().toString();
	}

	private String getOrCreateVideoPath(String owner) {
		Path videoDirectory = Paths.get(outputPath).resolve(owner).resolve("videos");
		if (!Files.exists(videoDirectory)) {
			try {
				Files.createDirectories(videoDirectory);
			} catch (IOException e) {
				log.error("e :" + e);
			}
		}
		return videoDirectory.toAbsolutePath().toString();
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
			return Flux.fromStream(Files.walk(path)
				.filter(file -> file.toString().endsWith(".ts") || file.toString().endsWith(".jpg")));
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
					return walkFilesFlux(dirPath)
						.doOnNext(file -> {
							try {
								BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
								Instant currentInstant = Instant.now();
								Instant fileCreationInstant = attributes.creationTime().toInstant();
								final long elapsedTime = Duration.between(fileCreationInstant, currentInstant)
									.toMinutes();

								if (elapsedTime >= 1) {
									filesToDelete.add(file);
								}
							} catch (IOException e) {
								log.error("Failed to read attributes of file {}", file, e);
							}
						})
						.doOnComplete(() -> {
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
						})
						.then()
						.subscribeOn(Schedulers.boundedElastic());
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