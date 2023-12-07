package com.hanghae.lemonairtranscoding.aws;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.SdkClientException;
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

	private final AmazonS3 amazonS3;
	@Value("${aws.s3.bucket}")
	private String bucket;

	/**
	 * S3에 파일을 업로드하는 함수
	 *
	 * @param uploadFile : 업로드 할 파일
	 * @param fileName   : 원본 파일 이름
	 * @return : s3에 업로드 된 경로 url
	 */
	public String upload(File uploadFile, String fileName, boolean publicRead) {
		String uploadedUrl = uploadToS3(uploadFile, fileName, publicRead);
		removeLocalFile(uploadFile);
		return uploadedUrl;
	}

	private void removeLocalFile(File targetFile) {
		if (targetFile.delete()) {
			log.info("로컬 파일 삭제 성공");
			return;
		}
		log.error(targetFile.getName() + " 로컬 파일 삭제 실패");
	}

	private String uploadToS3(File uploadFile, String fileName, boolean publicRead) {
		// 못찾으면 SdkClientException 이 발생한다.
		amazonS3.putObject(new PutObjectRequest(bucket, CommonUtils.buildS3Key_Username_Slash_FileName(fileName),
			uploadFile).withCannedAcl(
			publicRead ? CannedAccessControlList.PublicRead : CannedAccessControlList.Private));

		return amazonS3.getUrl(bucket, fileName).toString();
	}
}
