package com.hanghae.lemonairtranscoding.service;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsService {
	private final AmazonS3 amazonS3SyncClient;
	private final S3AsyncClient amazonS3AsyncClient;
	private final String noCache = "max-age=0, no-cache, no-store, must-revalidate";
	@Value("${ffmpeg.output.directory}")
	private String outputPath;
	@Value("${aws.s3.bucket}")
	private String bucket;

	/**
	 * filePath에서 파일 확장자가 m3u8인지 확인하고
	 * m3u8파일을 업로드하는 경우 CacheControl 설정을 No Cache로,
	 * 다른 파일의 경우 Cache 설정을 추가로 설정하지 않고 업로드합니다.
	 *
	 * @param filePath 업로드할 파일의 경로
	 */
	public void uploadToS3Async(String filePath) {
		String key = filePath.substring(outputPath.length() + 1).replaceAll("\\\\", "/");
		PutObjectRequest putObjectRequest;
		if (filePath.endsWith("m3u8")) {
			putObjectRequest = buildNoCachePutObjectRequest(key);
		} else {
			putObjectRequest = buildPutObjectRequest(key);
		}
		// 234 ms 정도 걸림
		try {
			Thread.sleep(234);
		} catch (InterruptedException e) {
			log.error("aws 업로드 대신 sleep하는 구문에서 : " + e);
		}
		// upload(putObjectRequest, filePath, key).thenAccept(log::info);
	}

	/**
	 * s3 비동기 클라이언트를 이용하여 S3에 업로드합니다.
	 *
	 * @param putObjectRequest S3 업로드 요청 객체
	 * @param filePath         파일 경로 Path 객체
	 * @param key              S3에 업로드될 경로
	 * @return CompletableFuture<String>
	 */
	private CompletableFuture<String> upload(PutObjectRequest putObjectRequest, String filePath, String key) {
		Path path = Path.of(filePath);
		return amazonS3AsyncClient.putObject(putObjectRequest, path)
			.thenApplyAsync(response -> amazonS3SyncClient.getUrl(bucket, key).toString());
	}

	/**
	 * Cache를 적용하지 않는 설정으로 PutObjectRequest를 build합니다.
	 *
	 * @param key 키
	 * @return PutObjectRequest 객체
	 */
	private PutObjectRequest buildNoCachePutObjectRequest(String key) {
		return PutObjectRequest.builder().key(key).bucket(bucket).cacheControl(noCache).build();
	}

	/**
	 * Cache를 기본 설정으로 PutObjectRequest를 build합니다.
	 *
	 * @param key 키
	 * @return PutObjectRequest 객체
	 */
	private PutObjectRequest buildPutObjectRequest(String key) {
		return PutObjectRequest.builder().key(key).bucket(bucket).build();
	}

}
