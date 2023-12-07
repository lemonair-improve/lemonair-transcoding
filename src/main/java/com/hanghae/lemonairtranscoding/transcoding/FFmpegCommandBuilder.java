package com.hanghae.lemonairtranscoding.transcoding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FFmpegCommandBuilder {


	private StringBuilder command;

	private final String inputStreamIp;
	private final String outputPath;
	private final String ffmpegPath;

	private final String THUMBNAIL_SERIAL_NUMBER_POSTFIX = "_thumbnail_%04d.jpg";
	// private final String THUMBNAIL_DATETIME_POSTFIX = "_thumbnail_%Y%m%d_%H%M%S.jpg";

	public FFmpegCommandBuilder(String ffmpegPath, String inputStreamIp, String outputPath) {
		this.inputStreamIp = inputStreamIp;
		this.ffmpegPath = ffmpegPath;
		this.outputPath = outputPath;
		init();
	}

	public void init(){
		command = new StringBuilder();
		command.append(ffmpegPath);
		command.append(' ');
	}

	public FFmpegCommandBuilder setInputStreamRequestUrl(String email) {
		command.append("-i").append(' ');
		command.append(this.inputStreamIp);
		command.append('/');
		command.append(email);
		command.append(' ');
		return this;
	}

	public FFmpegCommandBuilder printFFmpegBanner(boolean print) {
		if (!print) {
			command.append("-hide_banner").append(' ');
		}
		return this;
	}

	public FFmpegCommandBuilder setLoggingLevel(String loggingLevel) {
		command.append("-loglevel").append(' ').append(loggingLevel).append(' ');
		return this;
	}

	public FFmpegCommandBuilder printStatistic(boolean print) {
		if (print) {
			command.append("-stats").append(' ');
		}
		return this;
	}

	public FFmpegCommandBuilder setVideoCodec(String videoCodec) {
		command.append("-c:v").append(' ');
		command.append(videoCodec).append(' ');
		return this;
	}

	public FFmpegCommandBuilder setAudioCodec(String audioCodec){
		command.append("-c:a").append(' ');
		command.append(audioCodec).append(' ');
		return this;
	}

	public FFmpegCommandBuilder useTempFileWriting(boolean use){
		if(use){
			command.append("-hls_flags temp_file").append(' ');
		}
		return this;
	}

	public FFmpegCommandBuilder setSegmentUnitTime(int second){
		command.append("-hls_time").append(' ').append(second).append(' ');
		return this;
	}

	public FFmpegCommandBuilder setSegmentListSize(int size){
		command.append("-hls_list_size").append(' ').append(size).append(' ');
		return this;
	}

	public FFmpegCommandBuilder setOutputType(String type){
		if(type.equals("hls")){
			command.append("-f hls").append(' ');
		}
		return this;
	}

	public FFmpegCommandBuilder createVTTFile(boolean create){
		if(!create){
			command.append("-sn").append(' ');
		}
		return this;
	}

	public FFmpegCommandBuilder setM3U8FileName(String name){
		command.append(getOrCreateVideoPath(name)).append('\\').append(name).append(".m3u8").append(' ');
		return this;
	}

	public FFmpegCommandBuilder createThumbnailBySeconds(int second){
		command.append("-vf fps=").append("1/").append(second).append(' ');
		return this;
	}

	public FFmpegCommandBuilder setThumbnailQuality(int quality){
		command.append("-q:v").append(' ').append(quality).append(' ');
		return this;
	}

	public FFmpegCommandBuilder setThumbnailCreatePath(String streamerName){
		command.append(getOrCreateThumbnailPath(streamerName));
		return this.setThumbnailFileName(streamerName);
	}

	private FFmpegCommandBuilder setThumbnailFileName(String streamerName) {
		command.append('\\').append(streamerName).append(THUMBNAIL_SERIAL_NUMBER_POSTFIX).append(' ');
		return this;
	}

	public List<String> build(){
		command.append("-y");
		String buildedCommand = command.toString();
		log.info("buildedCommand : " + buildedCommand);

		return getSplitCommand(buildedCommand);
	}
	public FFmpegCommandBuilder setBitrate(String bitrate) {
		command.append("-b:v ").append(bitrate); // 1000k 등의 입력
		command.append(' ');
		return this;
	}

	public FFmpegCommandBuilder useDateTimeFileNaming(boolean use){
		if(use){
			command.append("-strftime 1").append(' ');
		}
		return this;
	}

	public FFmpegCommandBuilder setOutPutPath(String outputPath) {
		command.append(outputPath);
		command.append(' ');
		return this;
	}

		// String runffmpegWithStreamingUrl = String.format("%s -i %s ", ffmpegPath,
		// 	inputStreamIp + "/" + owner + "@gmail.com");
		// String loggingSettings = "-hide_banner -loglevel info -stats ";
		// String defaultCodecSettings = "-c:v libx264 -c:a aac "; // 기본 비디오 코덱은 libx264, 오디오 코덱은 aac 자막 파일인 vtt 파일은 생성하지 않는다.
		// String hlsSegmentSettings = String.format("-hls_flags temp_file -hls_time %d -hls_list_size %d ", 2,
		// 	6); // .m3u8 파일에 포함될 세그먼트의 최대 개수
		// String m3u8SaveSettings = String.format("-strftime 1 -f hls -sn %s/%s.m3u8 ", "videos",
		// 	owner);// -strftime 1 현재 시각을 사용하여 videos/지정한파일명.m3u8 로 저장합니다.
		// String thumbnailSaveSettings = String.format("-vf fps=1/10 -q:v 2 %s -y",
		// 	thumbnailOutputPathAndName + "\\" + owner + THUMBNAIL_SERIAL_NUMBER_POSTFIX+ ".jpg"); // -y 옵션 : overwrite할지 물어보는 경우가 있다.


	private static List<String> getSplitCommand(String command) {
		List<String> splitCommand = new ArrayList<>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(command);
		while (regexMatcher.find()) {
			if (regexMatcher.group(1) != null) {
				// 큰따옴표 없이 큰따옴표로 된 문자열을 추가하세요
				splitCommand.add(regexMatcher.group(1));
			} else if (regexMatcher.group(2) != null) {
				// 작은따옴표 없이 작은따옴표로 된 문자열을 추가하세요
				splitCommand.add(regexMatcher.group(2));
			} else {
				// 따옴표 없는 단어를 추가하세요
				splitCommand.add(regexMatcher.group());
			}
		}
		return splitCommand;
	}

	public List<String> getDefaultCommand(String email, String streamerName) {
		return this.setInputStreamRequestUrl(email)
			.printFFmpegBanner(false)
			.setLoggingLevel("info")
			.printStatistic(true)
			.setVideoCodec("libx264")
			.setAudioCodec("aac")
			.useTempFileWriting(true)
			.setSegmentUnitTime(10)
			.setSegmentListSize(2)
			.useDateTimeFileNaming(true)
			.setOutputType("hls")
			.createVTTFile(false)
			.setM3U8FileName(streamerName)
			.createThumbnailBySeconds(10)
			.setThumbnailQuality(2)
			.setThumbnailCreatePath(streamerName)
			.build();
	}



	public String getOrCreateThumbnailPath(String streamerName) {
		Path thumbnailDirectory = Paths.get(outputPath).resolve(streamerName).resolve("thumbnail");
		if (!Files.exists(thumbnailDirectory)) {
			try {
				Files.createDirectories(thumbnailDirectory);
			} catch (IOException e) {
				log.error("createThumbnailFilePath exception :" + e);
			}
		}
		return thumbnailDirectory.toAbsolutePath().toString();
	}

	private String getOrCreateVideoPath(String streamerName) {
		Path videoDirectory = Paths.get(outputPath).resolve(streamerName).resolve("videos");
		if (!Files.exists(videoDirectory)) {
			try {
				Files.createDirectories(videoDirectory);
			} catch (IOException e) {
				log.error("createVideoFilePath exception:" + e);
			}
		}
		return videoDirectory.toAbsolutePath().toString();
	}
}
