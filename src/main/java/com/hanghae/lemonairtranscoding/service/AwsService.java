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
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsService {
	private final S3AsyncClient amazonS3AsyncClient;
	private final String noCache = "max-age=0, no-cache, no-store, must-revalidate";
	@Value("${ffmpeg.output.directory}")
	private String outputPath;
	@Value("${aws.s3.bucket}")
	private String bucket;

	public CompletableFuture<PutObjectResponse> uploadToS3Async(String filePath) {
		String key = filePath.substring(outputPath.length() + 1).replaceAll("\\\\", "/");
		PutObjectRequest putObjectRequest = filePath.endsWith("m3u8") ? buildNoCachePutObjectRequest(key) : buildPutObjectRequest(key);
		return upload(putObjectRequest, filePath);
	}

	private CompletableFuture<PutObjectResponse> upload(PutObjectRequest putObjectRequest, String filePath) {
		return amazonS3AsyncClient.putObject(putObjectRequest, Path.of(filePath));
	}

	private PutObjectRequest buildNoCachePutObjectRequest(String key) {
		return PutObjectRequest.builder().key(key).bucket(bucket).cacheControl(noCache).build();
	}

	private PutObjectRequest buildPutObjectRequest(String key) {
		return PutObjectRequest.builder().key(key).bucket(bucket).build();
	}

}
