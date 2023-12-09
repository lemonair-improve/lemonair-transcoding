package com.hanghae.lemonairtranscoding.service;

import static com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandConstants.*;

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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandBuilder;
import com.hanghae.lemonairtranscoding.util.LocalFileCleaner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranscodeService {

	private final AmazonS3 amazonS3;
	private final LocalFileCleaner localFileCleaner;
	private final String SAVED_FILE_LOG_PREFIX = "[hls";
	private final String TEMP_FILE_EXTENSION_POSTFIX = ".tmp";

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

	public Mono<Long> startTranscoding(String email, String streamerName) {
		// TranscodingCompleteMonitorThread transcodingCompleteMonitorThread = new TranscodingCompleteMonitorThread();
		log.info("streaming server 에서 transcoding server에 접근 streamerName : " + streamerName);
		// 오래된 스트림 파일 삭제 스케쥴링
		localFileCleaner.setDeleteOldFileTaskSchedule(streamerName);

		List<String> ffmpegCommand = new FFmpegCommandBuilder(ffmpegPath, inputStreamIp,
			outputPath).setInputStreamPathVariable(email)
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
		runffmepgAsync(processBuilder).subscribe();
		return Mono.just(1L);
	}

	private Mono<Void> runffmepgAsync(ProcessBuilder processBuilder) {
		// Transcoding application이 종료되어야 그제서야 각 시각에 맞게 인코딩된 영상들이 정상적으로 저장되는 오류 발생
		return Mono.fromCallable(() -> {
			Process process = processBuilder.start();

			Flux<String> logLines = Flux.fromStream(
					() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines())
				.filter(line -> line.startsWith(SAVED_FILE_LOG_PREFIX));
			return logLines;
		}).flatMap(lines -> lines.doOnNext(line -> {
			String fileDirectory = line.substring(line.indexOf('\'') + 1, line.lastIndexOf('\''));
			if (fileDirectory.endsWith(TEMP_FILE_EXTENSION_POSTFIX)) {
				log.info("파일 디렉토리에서 .tmp 제거");
				fileDirectory = fileDirectory.substring(0,
					fileDirectory.length() - TEMP_FILE_EXTENSION_POSTFIX.length());
			}
			if (uploadS3) {
				uploadToS3Async(fileDirectory).subscribe();
			}
		}).then());
	}

	private Mono<Void> uploadToS3Async(String fileDirectory) {
		//C:\Users\sbl\Desktop\ffmpegoutput\lyulbyung\videos\lyulbyung-20231208012612.ts
		return Mono.fromCallable(() -> {
			log.info("업로드할 파일 : " + fileDirectory);
			String key = fileDirectory.substring(outputPath.length() + 1, fileDirectory.lastIndexOf('\\'));
			File file = new File(fileDirectory);
			PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, key, file);
			PutObjectResult putObjectResult = amazonS3.putObject(putObjectRequest);
			log.info("putObjectResult : " + putObjectResult);
			log.info("uploadurl : " + amazonS3.getUrl(bucket, key).toString());
			return null;
		});
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