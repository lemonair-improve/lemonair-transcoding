package com.hanghae.lemonairtranscoding.util;

public class CommonUtils {
	private static final String SEPARATOR = ".";

	// s3에 저장할 때 파일명_업로드시간.확장자 로 저장하기 위한 메소드
	public static String buildS3FileName(String fileName){
		int extensionDotIndex = fileName.lastIndexOf(SEPARATOR);
		String fileExtention = fileName.substring(extensionDotIndex);
		String timeNow = String.valueOf(System.currentTimeMillis());
		return fileName.substring(0, extensionDotIndex) + timeNow + fileExtention;
	}
}
