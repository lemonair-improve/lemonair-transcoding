package com.hanghae.lemonairtranscoding.awstest;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
public class AwsS3Test {

	@Autowired
	private AmazonS3 s3SyncClient;
	@Autowired
	private S3AsyncClient s3AsyncClient;

	@Value("${aws.s3.bucket}")
	private String bucket;

	@Test
	void uploadFileSyncTest() {
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		s3SyncClient.putObject(bucket, "sbl/sm2", file);
	}

	@Test
	void uploadFileSyncTest2() {
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		s3SyncClient.putObject(bucket, "lyulbyung/videos", file);
	}

	/**
	 * cache-control 값은 PutObjectRequest.Builder에서 build 메서드를 제공하고있기 때문에
	 * metadata로 직접 지정하지 않아도 된다.
	 */
	@Test
	void uploadFileAsyncTest() {
		String fileName = "uploadtest.txt";
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.key("uploadtest/" + fileName)
			.bucket(bucket)
			.cacheControl("max-age=0, no-cache, no-store, must-revalidate")
			.build();
		Path path = Path.of("C:\\Users\\sbl\\Desktop\\" + fileName);
		s3AsyncClient.putObject(putObjectRequest, path).join();
	}

	@Test
	void getObjectMetadataTest() {
		ObjectMetadata metadata = s3SyncClient.getObjectMetadata(bucket, "uploadtest/uploadtest.txt");
		// 표준 메타데이터 출력
		System.out.println("Cache-Control: " + metadata.getCacheControl());
		System.out.println("Content-Disposition: " + metadata.getContentDisposition());
		System.out.println("Content-Encoding: " + metadata.getContentEncoding());
		System.out.println("Content-Length: " + metadata.getContentLength());
		System.out.println("Content-Type: " + metadata.getContentType());
		System.out.println("ETag: " + metadata.getETag());
		System.out.println(metadata.getUserMetadata());
	}

	@Test
	void checkFiles() {
		S3Object s3Object = s3SyncClient.getObject(bucket, "sbl/sm2");
		System.out.println("s3Object.getObjectContent().toString() = " + s3Object.getObjectContent().toString());
	}

	@Test
	void checkVideo() {
		S3Object s3Object = s3SyncClient.getObject(bucket, "lyulbyung/videos/lyulbyung.m3u8");
		System.out.println("s3Object.getObjectContent().toString() = " + s3Object.getObjectContent().toString());
	}

	@Test
	void uploadFileWithNoCacheMetadata() {
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setCacheControl("max-age=0, no-cache, no-store, must-revalidate");
		// PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, "test.txt",
		// 	new File("C:/Users/sbl/Desktop/test.txt")).withMetadata(metadata);
		// amazonS3.putObject(putObjectRequest);
	}
}
