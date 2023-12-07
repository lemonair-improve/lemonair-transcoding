package com.hanghae.lemonairtranscoding.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hanghae.lemonairtranscoding.aws.AwsS3Uploader;
import com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandBuilder;
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

	// private final ScheduledExecutorService uploadExecutor = Executors.newSingleThreadScheduledExecutor();

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

		// 오래된 스트림 파일 삭제 스케쥴링
		localFileCleaner.setDeleteOldFileTaskSchedule(owner);

		List<String> ffmpegCommand = new FFmpegCommandBuilder(
			ffmpegExeFilePath,
			inputStreamIp,
			outputPath).getDefaultCommand(email, owner);

		// 내장된 ffmepg에 명령을 전달할 processBuilder를 생성
		ProcessBuilder processBuilder = getTranscodingProcess(owner, ffmpegCommand);

		return Mono.fromCallable(processBuilder::start).flatMap(process -> {

			// onExit() - 프로세스 종료를 위한 CompletableFuture<Process>를 반환
			process.onExit().thenAccept((c) -> {
				localFileCleaner.runWhenFFmpegProcessExit(owner, c);
			});
			try {
				runOutputWatcherThread(process);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			localFileCleaner.processMap.put(owner, process);
			// 프로세스의 기본 프로세스 ID를 반환합니다. 기본 프로세스 ID는 운영 체제가 프로세스에 할당하는 식별 번호
			return Mono.just(process.pid());
			// 블로킹 IO 태스크와 같은 생명주기가 긴 태스크들에 적합하다.
			// boundedElastic 은 요청 할때마다 스레드 생성 단, 스레드 수 제한

		}).subscribeOn(Schedulers.boundedElastic()); // subscribeOn은 구독이 어느 스레드에서 이루어질지를 선택한다.
	}

	private String uploadToS3(String fileDirectory) {
		// 경로.파일명.ts or 경로\파일명.m3u8 파일
		String fileName = fileDirectory.substring(fileDirectory.lastIndexOf('\\') + 1);
		File uploadFile = new File(fileDirectory);
		return s3Uploader.upload(uploadFile, fileName, true);
	}

	private void runOutputWatcherThread(Process process) throws InterruptedException {

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
						// [hls @ 000002045e81f800] Opening 'C:\Users\sbl\Desktop\ffmpegoutput\lyulbyung\videos/lyulbyung-20231208012612.ts.tmp' for writing
						// 위의 출력을
						//'C:\Users\sbl\Desktop\ffmpegoutput\lyulbyung\videos/lyulbyung-20231208012612.ts
						// 로 변경합니다.
						String fileDirectory = line.substring(line.indexOf('\''), line.lastIndexOf('.'));
						// String uploadedUrl = uploadToS3(fileDirectory);
						// System.out.println("s3에 업로드 성공" + uploadedUrl);
						// 원하는 동작 수행
					} else {
						System.out.println("[FFMPEG]" + line);
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
		// processBuilder.inheritIO();

		processBuilder.directory(new File(directory.toAbsolutePath().toString()));
		return processBuilder;
	}

}