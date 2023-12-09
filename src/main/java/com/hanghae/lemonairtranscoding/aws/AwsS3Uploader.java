package com.hanghae.lemonairtranscoding.aws;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.hanghae.lemonairtranscoding.domain.UploadFile;
import com.hanghae.lemonairtranscoding.util.AwsUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3Uploader {

	private final AmazonS3 amazonS3;
	@Value("${aws.s3.bucket}")
	private String bucket;


	// private void removeLocalFile(File targetFile) {
	// 	if (targetFile.delete()) {
	// 		log.info("로컬 파일 삭제 성공");
	// 		return;
	// 	}
	// 	log.error(targetFile.getName() + " 로컬 파일 삭제 실패");
	// }

	public String upload(UploadFile uploadFile) {
		String uploadedUrl = uploadToS3(uploadFile);
		// removeLocalFile(uploadFile);
		return uploadedUrl;
	}

	private String uploadToS3(UploadFile uploadFile) {
		String s3UploadKey = AwsUtils.getS3KeyFrom(uploadFile);
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, s3UploadKey, uploadFile.getUploadFile());
		PutObjectResult putObjectResult = amazonS3.putObject(putObjectRequest);
		log.info("putObjectResult : " + putObjectResult);
		return amazonS3.getUrl(bucket, s3UploadKey).toString();
	}
}
