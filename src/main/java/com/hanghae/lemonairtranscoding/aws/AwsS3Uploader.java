package com.hanghae.lemonairtranscoding.aws;

import java.io.File;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.hanghae.lemonairtranscoding.util.CommonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3Uploader {

	@Value("${aws.s3.bucket}")
	private String bucket;

	private final AmazonS3 amazonS3;

	public String upload(File uploadFile, String fileName){
		String uploadedUrl = uploadToS3(uploadFile, fileName);
		removeLocalFile(uploadFile);
		return uploadedUrl;
	}
	private String uploadToS3Private(File uploadFile, String s3FileName){
		amazonS3.putObject(new PutObjectRequest(bucket, s3FileName, uploadFile).withCannedAcl(
			CannedAccessControlList.PublicRead));
		return amazonS3.getUrl(bucket, s3FileName).toString();
	}
	public void removeLocalFile(File targetFile){
		if(targetFile.delete()){
			log.info("로컬 파일 삭제 성공");
			return;
		}
		log.error(targetFile.getName() + " 로컬 파일 삭제 실패");
	}

	/**
	 * S3에 파일을 업로드하는 함수
	 * @param uploadFile : 업로드 할 파일
	 * @param fileName : 원본 파일 이름
	 * @return : s3에 업로드 된 경로 url
	 */
	public String uploadToS3(File uploadFile, String fileName){
		amazonS3.putObject(new PutObjectRequest(bucket, CommonUtils.buildS3FileName(fileName),uploadFile));
		return amazonS3.getUrl(bucket, fileName).toString();
	}
}
