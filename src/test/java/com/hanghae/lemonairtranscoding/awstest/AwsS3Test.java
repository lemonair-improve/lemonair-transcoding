package com.hanghae.lemonairtranscoding.awstest;

import java.awt.*;
import java.io.File;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;


@SpringBootTest
public class AwsS3Test {


	@Autowired
	private AmazonS3 amazonS3;

	@Test
	void uploadFileTest(){
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		amazonS3.putObject("lemonair-streaming", "sbl/sm2", file);
	}

	@Test
	void uploadFileTest2(){
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		amazonS3.putObject("lemonair-streaming", "lyulbyung/videos", file);
	}


	@Test
	void checkFiles(){
		S3Object s3Object = amazonS3.getObject("lemonair-streaming", "sbl/sm2");
		System.out.println("s3Object.getObjectContent().toString() = " + s3Object.getObjectContent().toString());
	}

	@Test
	void checkVidoe(){
		S3Object s3Object = amazonS3.getObject("lemonair-streaming", "lyulbyung/videos/lyulbyung.m3u8");
		System.out.println("s3Object.getObjectContent().toString() = " + s3Object.getObjectContent().toString());
	}

	@Test
	void uploadFileWithNoCacheMetadata(){
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setCacheControl("max-age=0, no-cache, no-store, must-revalidate");
		PutObjectRequest putObjectRequest = new PutObjectRequest("lemonair-streaming", "test.txt", new File("C:/Users/sbl/Desktop/test.txt")).withMetadata(metadata);
		amazonS3.putObject(putObjectRequest);
	}
}
