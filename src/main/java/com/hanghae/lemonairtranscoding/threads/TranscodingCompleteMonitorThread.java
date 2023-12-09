package com.hanghae.lemonairtranscoding.threads;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import com.hanghae.lemonairtranscoding.aws.AwsS3Uploader;
import com.hanghae.lemonairtranscoding.domain.Jpg;
import com.hanghae.lemonairtranscoding.domain.M3u8;
import com.hanghae.lemonairtranscoding.domain.Ts;
import com.hanghae.lemonairtranscoding.domain.UploadFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TranscodingCompleteMonitorThread {

	private final AwsS3Uploader awsS3Uploader;

	private final String SAVED_FILE_LOG_PREFIX = "[hls";
	private final String TEMP_FILE_EXTENSION_POSTFIX = ".tmp";

	private final String EXTENSION_M3U8 = "m3u8";
	private final String EXTENSION_JPG = "jpg";
	private final String EXTENSION_TS = "ts";

	public void monitor(Process processToDetect) throws InterruptedException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(processToDetect.getInputStream()));
		Thread thread = new Thread(() -> {
			try {
				String line;
				while (true) {
					line = reader.readLine();

					if (line == null) {
						Thread.sleep(1000); // 1초간 대기 후 다시 확인
						continue;
					}
					if (line.contains(SAVED_FILE_LOG_PREFIX)) {
						System.out.println("파일 저장 감지 " + line);

						// [hls @ 000002045e81f800] Opening 'C:\Users\sbl\Desktop\ffmpegoutput\lyulbyung\videos/lyulbyung-20231208012612.ts' for writing
						// [hls @ 000002045e81f800] Opening 'C:\Users\sbl\Desktop\ffmpegoutput\lyulbyung\videos/lyulbyung-20231208012612.ts.tmp' for writing

						/**
						 * 위와 같은 로그 출력에서 파일 디렉토리만 추출 하여 아래와 같이 만든다.
						 */
						//C:\Users\sbl\Desktop\ffmpegoutput\lyulbyung\videos/lyulbyung-20231208012612.ts
						String fileDirectory = line.substring(line.indexOf('\'') + 1, line.lastIndexOf('\''));

						// 추출한 fileDirectory에서 .tmp확장자가 붙어있을 수가 있으므로 확인한다.
						if (fileDirectory.endsWith(TEMP_FILE_EXTENSION_POSTFIX)) {
							log.info("파일 디렉토리에서 .tmp 제거");
							fileDirectory = fileDirectory.substring(0,
								fileDirectory.length() - TEMP_FILE_EXTENSION_POSTFIX.length());
						}
						String uploadedUrl = uploadToS3(fileDirectory);
						System.out.println("s3에 업로드 성공" + uploadedUrl);
						// 원하는 동작 수행
					} else {
						System.out.println("[FFMPEG]" + line);
					}
				}
			} catch (IOException e) {
				log.error("IOException 발생 error : " + e);
			} catch (InterruptedException e) {
				log.error("InterruptedException 밠애 error : " + e);
			}
		});

		thread.start();
		processToDetect.waitFor();
		thread.join();
	}

	private String uploadToS3(String fileDirectory) {
		// 경로.파일명.ts or 경로\파일명.m3u8 파일
		String fileName = fileDirectory.substring(fileDirectory.lastIndexOf('\\') + 1);
		File file = new File(fileDirectory);
		UploadFile uploadFile = null;

		if (fileName.endsWith(EXTENSION_JPG)) {
			uploadFile = new Jpg(fileName, file);
		} else if (fileName.endsWith(EXTENSION_TS)) {
			uploadFile = new Ts(fileName, file);
		} else if (fileName.endsWith(EXTENSION_M3U8)) {
			uploadFile = new M3u8(fileName, file);
		}


		return awsS3Uploader.upload(uploadFile);
	}
}
