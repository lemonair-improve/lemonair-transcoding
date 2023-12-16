package com.hanghae.lemonairtranscoding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FFmpegConfig {

	@Value("${ffmpeg.output.directory}")
	private String outputPath;
	@Value("${ffmpeg.exe}")
	private String ffmpegPath;
	@Value("${ffmpeg.ip}")
	private String inputStreamIp;

	// @Bean

}
