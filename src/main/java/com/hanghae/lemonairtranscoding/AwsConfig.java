package com.hanghae.lemonairtranscoding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
public class AwsConfig {

	@Value("${aws.s3.credentials.accessKey}")
	private String accessKey;

	@Value("${aws.s3.credentials.secretKey}")
	private String secretKey;

	@Value("${aws.s3.region}")
	private String region;

	private AwsCredentials credentials;

	@PostConstruct
	void init() {
		credentials = AwsBasicCredentials.create(accessKey, secretKey);
	}

	@Bean
	public AmazonS3 amazonS3SyncClient() {
		AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
		return AmazonS3ClientBuilder.standard()
			.withCredentials(new AWSStaticCredentialsProvider(credentials))
			.withRegion(region)
			.build();
	}

	@Bean
	public S3AsyncClient amazonS3AsyncClient() {
		return S3AsyncClient.builder()
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(credentials))
			.build();
	}

}
