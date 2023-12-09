package com.hanghae.lemonairtranscoding.util;

import com.hanghae.lemonairtranscoding.domain.UploadFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsUtils {
	private static final String SEPARATOR = ".";

	/**
	 *s3에 저장할 때 lyulbyung/lyulbyung-20231208_025600.ts 와 같이 저장할 수 있도록 key를 반환한다.
 	 */
	// public static String getS3KeyFromTSFile(String fileName){
	// 	// 파일명이 lyulbyung-20231208_025600.ts와 같으므로 첫번째 하이픈 전이 이름임
	// }

	/**
	 * s3에 저장할 때 lyulbyung/lyulbyung.m3u8 와 같이 저장할 수 있도록 key를 반환한다.
	 * @param fileName
	 * @return username/username.m3u8
	 */
	public static String getS3KeyFrom(String fileName){
		log.info("fileName : " + fileName);
		String username = fileName.substring(0, fileName.lastIndexOf('.'));
		log.info("username : " + username);
		return username + "/" + fileName;
	}

	public static String getS3KeyFrom(UploadFile uploadFile) {
		String fileName = uploadFile.getFileName();
		log.info("fileName : " + fileName);
		return fileName;
	}
}
