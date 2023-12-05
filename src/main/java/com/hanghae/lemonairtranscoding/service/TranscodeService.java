package com.hanghae.lemonairtranscoding.service;

import java.awt.*;
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

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class TranscodeService {

	// @Value("${ffmpeg.command}")
	private String template;

	// @Value("${rtmp.server}")
	private String address;

	@Value("${stream.directory}")
	private String outputPath;

	@Value("${ffmpeg.directory.exe}")
	private String ffmpegExeFilePath;

	@Value("${ffmpeg.ip}")
	private String ffmpegIp;


	// 동시성 떄문에 사용
	private final ConcurrentHashMap<String, Process> processMap = new ConcurrentHashMap<>(); // 스트림에 대한 FFmpeg 프로세스 관리
	private static final long delete_interval = 1L; // 파일을 삭제하기 위한 간격 설정
	private final ScheduledExecutorService deleteFile = Executors.newSingleThreadScheduledExecutor(); // 파일 삭제 작업 스케줄 관리
	private final AtomicBoolean stopSearching = new AtomicBoolean(true); // 상태 추적 변수
	private final AtomicBoolean showMessage = new AtomicBoolean(true); // 상태 추적 변수



	// 리액티브 프로그래밍을 위해 사용
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

		Path directoryPath = Paths.get(outputPath); // 경로에 대한 Path 객체 생성

		try {
			Flux<Path> dirPaths = Flux.fromStream(Files.list(directoryPath)); // directoryPath로부터 디렉토리에 있는 파일과 디렉토리를 스트림으로 받아오는 과정
			dirPaths.filter(Files::isDirectory) // 받아온 파일과 디렉토리 중 디렉토리만 불러오는 과정
				.filter(dirPath -> dirPath.toFile().getName().equals(owner)) // 디렉토리 이름이 owner와 같은 것만 추출
				.flatMap(dirPath -> { // 각 디렉토리에 대해 비동기적으로 작업 수행
					List<Path> filesToDelete = new ArrayList<>(); // 삭제할 목록을 저장하기 위한 리스트 생성
					return walkFilesFlux(dirPath) // 디렉토리 내의 파일에 대한 Flux 반환
						.doOnNext(file -> { // flux에 포함된 파일에 대해 수행할 작업 정의
							try {
								BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class); // 파일의 속성을 읽어옴
								Instant currentInstant = Instant.now(); // 현재 시간을 얻어와서 Instant 객체로 저장
								Instant fileCreationInstant = attributes.creationTime().toInstant(); // 파일 생성 시간을 가져와 객체로 저장
								final long elapsedTime = Duration.between(fileCreationInstant, currentInstant).toMinutes(); // 두 시간의 차이를 계산하고 분단위로 저장

								if (elapsedTime >= 1) { // 1분 이상 경과한 파일들을 filesToDelete 리스트에 저장
									filesToDelete.add(file);
								}
							} catch (IOException e) {
								log.error("Failed to read attributes of file {}", file, e);
							}
						})
						.doOnComplete(() -> { // Flux의 모든 요소에 대한 처리가 끝났을 때 실행할 작업 정의
							for (Path file : filesToDelete) {
								AtomicBoolean hasFiles = new AtomicBoolean(false); // 파일이 삭제되었는지 추적하는 데 사용할 변수 초기화
								try {
									if (file.toString().endsWith(".ts") || file.toString().endsWith(".jpg")) { // .ts와 .jpg 파일 삭제
										Files.deleteIfExists(file);
										hasFiles.set(true); // 파일 삭제 추적값 true
									}
								} catch (IOException e) {
									log.error("Failed to delete file {}", file, e);
								}
								if (!hasFiles.get()) {
									stopSearching.set(true); // 만약 삭제된 파일이 없었다면 stopSearching을 true로 하여 파일 탐색을 중지
								}
							}
						})
						.then()// 반환할 Mono 생성 (아무 값도 반환하지 않음)
						.subscribeOn(Schedulers.boundedElastic()); // 비동기적으로 실행되도록 스케줄러 지정(별도의 스레드에서 실행하도록)
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
				.filter(file -> file.toString().endsWith(".ts") || file.toString().endsWith(".jpg") || file.toString().endsWith(".m3u8"))
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

	private String createThumbnailPath(String owner) {
		Path directory = Paths.get(outputPath).resolve(owner);

		// "thumbnail" 폴더 생성
		Path thumbnailDirectory = directory.resolve("thumbnail");
		if (!Files.exists(thumbnailDirectory)) {
			try {
				Files.createDirectories(thumbnailDirectory);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return thumbnailDirectory.toAbsolutePath().toString();
	}

	public Mono<Long> start(String owner){
		log.info("streaming server 에서 transcoding server에 접근");
		deleteFile.scheduleAtFixedRate(() -> this.deleteOldTsAndJpgFiles(owner), delete_interval, delete_interval, TimeUnit.MINUTES); // deleteOldTsAndJpgFiles 메서드를 일정한 간격으로 호출
		// isAlive() - 하위 프로세스가 Process활성 상태인지 테스트
		if(processMap.containsKey(owner) && processMap.get(owner).isAlive()){ // 현재 작업중인 프로세스가 있는지 확인 만약 owner에 해당하는 프로세스가 있고 활성화 되어있다면 프로세스 종료
			// 하위 프로세스를 종료
			processMap.get(owner).destroyForcibly();
			processMap.remove(owner);
		}

		// 파일탐색을 다시 시작
		stopSearching.set(false);
		String thumbnailOutputPath = Paths.get(createThumbnailPath(owner), owner + "_thumbnail_%04d.jpg").toString();
		// String command = String.format(template, address + "/" + owner, owner + "_%v/data%d.ts", owner + "_%v.m3u8", thumbnailOutputPath);

		// -i rtmp://localhost:1935: 입력으로 사용될 RTMP 소스의 URL을 지정합니다. 여기서는 localhost의 1935 포트를 사용합니다.
		//
		// -c:v libx264: 비디오 스트림에 대한 비디오 코덱을 libx264로 설정합니다. libx264는 H.264 비디오 코덱을 나타냅니다.
		//
		// 	-c:a aac: 오디오 스트림에 대한 오디오 코덱을 aac로 설정합니다. AAC는 Advanced Audio Coding의 약자로, 오디오 압축을 위한 코덱입니다.
		//
		// 	-hls_time 10: HLS 세그먼트의 시간을 10초로 설정합니다. 이는 HLS 세그먼트의 길이를 나타냅니다.
		//
		// 	-hls_list_size 6: HLS 재생목록(.m3u8 파일)에 포함될 세그먼트의 최대 개수를 6으로 설정합니다. 새로운 세그먼트가 생성되면, 재생목록에 최대 6개까지만 유지됩니다.
		//
		//  C:\Users\sbl\Desktop\ffmpegoutput\byeongryeol.m3u8: HLS 스트리밍의 출력 디렉토리 및 재생목록 파일의 경로를 지정합니다. 여기서는 byeongryeol.m3u8이라는 재생목록 파일이 생성되며, 세그먼트 파일들은 해당 디렉토리에 저장됩니다.

		// ffmpeg -i rtmp://localhost:1935 -c:v libx264 -c:a aac -hls_time 10 -hls_list_size 6 C:\Users\sbl\Desktop\ffmpegoutput\byeongryeol.m3u8
		String command = String.format("%s -i %s -c:v libx264 -c:a aac -hls_time 10 -hls_list_size 6 %s/%s.m3u8",
			ffmpegExeFilePath
			, ffmpegIp
			, outputPath
		,owner );


		
		ProcessBuilder processBuilder = new ProcessBuilder();
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

		// processBuilder 의 OS 프로그램과 인수를 설정합니다.
		processBuilder.command(splitCommand);
		// processBuilder 의 redirectErrorStream 속성을 설정
		processBuilder.redirectErrorStream(true);
		// 하위 프로세스 표준 I/O의 소스 및 대상을 현재 Java 프로세스와 동일하게 설정
		processBuilder.inheritIO();

		// end of Input 에러
		return Mono
			.fromCallable(() -> { // Callable 객체로부터 Mono 생성
				Path directory = Paths.get(outputPath).resolve(owner); // outputPath와 owner를 합쳐 경로 생성
				// 경로가 폴더인지 확인
				if (!Files.exists(Paths.get(outputPath))) {
					try {
						Files.createDirectory(Paths.get(outputPath));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				/*
				 * 상대경로를 절대경로로 변경, processBuilder 의 작업 디렉토리를 설정
				 * 이후, 이 객체의 start() 메서드로 시작된 서브 프로세스는 이 디렉토리를 작업 디렉토리로서 사용
				 */
				processBuilder.directory(new File(directory.toAbsolutePath().toString()));
				/*
				 * ProcessBuilder 클래스의 인스턴스에 정의 된 속성으로 새 프로세스를 만들 수 있다
				 * ProcessBuilder 의 속성을 사용해 새로운 프로세스를 시작합니다.
				 * 새로운 프로세스는 directory() 로 지정된 작업 디렉토리의, environment() 로
				 * 지정된 프로세스 환경을 가지는 command() 로 지정된 커멘드와 인수를 호출
				 */
				return processBuilder.start(); // CreateProcess error=5, 액세스가 거부되었습니다.
			})
			.log("비동기 로그 실험")
			.flatMap(process -> {
				log.info(process.info().toString());
				log.info(String.valueOf(process.pid()));
				// onExit() - 프로세스 종료를 위한 CompletableFuture<Process>를 반환
				process.onExit().thenAccept((c) -> { // 프로세스의 종료를 감지하고 해당 프로세스가 종료되면 실행될 작업들 정의
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
				return Mono.just(process.pid()); // 정상적으로 종료되지 않은 process.pid 반환
				// 블로킹 IO 태스크와 같은 생명주기가 긴 태스크들에 적합하다.
				// boundedElastic 은 요청 할때마다 스레드 생성 단, 스레드 수 제한
			}).subscribeOn(Schedulers.boundedElastic());
	}
}