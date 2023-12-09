package com.hanghae.lemonairtranscoding.awstest;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.amazonaws.services.s3.AmazonS3;
import com.hanghae.lemonairtranscoding.aws.AwsS3Uploader;


@SpringBootTest
public class AwsS3Test {

	@Autowired
	private AwsS3Uploader awsS3Uploader;

	@Autowired
	private AmazonS3 amazonS3;

	@Test
	void uploadFileTest(){
		File file = new File("C:\\Users\\sbl\\Desktop\\uploadtest.txt.txt");
		amazonS3.putObject("lemonair-streaming", "sbl/sm2", file);
	}
}
