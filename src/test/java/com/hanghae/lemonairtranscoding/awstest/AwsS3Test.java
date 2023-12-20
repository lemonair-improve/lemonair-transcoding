package com.hanghae.lemonairtranscoding.awstest;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
public class AwsS3Test {

	@Autowired
	private AmazonS3 s3SyncClient;
	@Autowired
	private S3AsyncClient s3AsyncClient;

	@Test
	void uploadFileSyncTest(){
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		s3SyncClient.putObject("lemonair-streaming", "sbl/sm2", file);
	}

	@Test
	void uploadFileSyncTest2(){
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		s3SyncClient.putObject("lemonair-streaming", "lyulbyung/videos", file);
	}
	@Test
	void uploadFileAsyncTest(){
		PutObjectRequest.builder()
			.key("lyulbyung/videos")
			.bucket("lemonair-streaming")
			.metadata()
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		s3AsyncClient.putObject("lemonair-streaming", "lyulbyung/videos", file);
	}


	@Test
	void checkFiles(){
		S3Object s3Object = s3SyncClient.getObject("lemonair-streaming", "sbl/sm2");
		System.out.println("s3Object.getObjectContent().toString() = " + s3Object.getObjectContent().toString());
	}

	@Test
	void checkVidoe(){
		S3Object s3Object = s3SyncClient.getObject("lemonair-streaming", "lyulbyung/videos/lyulbyung.m3u8");
		System.out.println("s3Object.getObjectContent().toString() = " + s3Object.getObjectContent().toString());
	}
}
