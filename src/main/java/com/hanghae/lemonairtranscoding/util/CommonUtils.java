package com.hanghae.lemonairtranscoding.util;

public class CommonUtils {
	private static final String SEPARATOR = ".";

	/**
	 *s3에 저장할 때 lyulbyung/lyulbyung-20231208_025600.ts 와 같이 저장할 수 있도록 key를 반환한다.
 	 */
	public static String buildS3Key_Username_Slash_FileName(String fileName){
		// 파일명이 lyulbyung-20231208_025600.ts와 같으므로 첫번째 하이픈 전이 이름임
		String username = fileName.substring(0, fileName.indexOf('-'));
		return username + "/" + fileName;
	}
}
