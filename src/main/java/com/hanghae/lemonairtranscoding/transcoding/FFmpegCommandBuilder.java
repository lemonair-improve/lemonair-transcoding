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
	private final String THUMBNAIL_DATETIME_POSTFIX = "_thumbnail_%Y%m%d_%H%M%S.jpg";

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
		command.append("/transcode/");
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
		command.append("videos").append('/').append(name).append(".m3u8").append(' ');
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
		command.append(getOrCreateThumbnailPath(streamerName)).append(' ');
		return this;
	}

	private FFmpegCommandBuilder setThumbnailFileName(String streamerName) {
		command.append('\\').append(streamerName).append(THUMBNAIL_DATETIME_POSTFIX).append(' ');
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
			command.append("-strftime 1");
		}
		return this;
	}

	public FFmpegCommandBuilder setOutPutPath(String outputPath) {
		command.append(outputPath);
		command.append(' ');
		return this;
	}

	public List<String> getSplitCommand(String owner, String thumbnailOutputPathAndName) {
		// String command = String.format(template, address + "/" + owner, owner + "_%v/data%d.ts", owner + "_%v.m3u8", thumbnailOutputPathAndName);
		//  -i rtmp://localhost:1935: 입력으로 사용될 RTMP 소스의 URL을 지정합니다. 여기서는 localhost의 1935 포트를 사용합니다.
		//  -c:v libx264: 비디오 스트림에 대한 비디오 코덱을 libx264로 설정합니다. libx264는 H.264 비디오 코덱을 나타냅니다.
		// 	-c:a aac: 오디오 스트림에 대한 오디오 코덱을 aac로 설정합니다. AAC는 Advanced Audio Coding의 약자로, 오디오 압축을 위한 코덱입니다.
		// 	-hls_time 10: HLS 세그먼트의 시간을 10초로 설정합니다. 이는 HLS 세그먼트의 길이를 나타냅니다.
		// 	-hls_list_size 6: HLS 재생목록(.m3u8 파일)에 포함될 세그먼트의 최대 개수를 6으로 설정합니다. 새로운 세그먼트가 생성되면, 재생목록에 최대 6개까지만 유지됩니다.
		//  C:\Users\sbl\Desktop\ffmpegoutput\byeongryeol.m3u8: HLS 스트리밍의 출력 디렉토리 및 재생목록 파일의 경로를 지정합니다. 여기서는 byeongryeol.m3u8이라는 재생목록 파일이 생성되며, 세그먼트 파일들은 해당 디렉토리에 저장됩니다.

		String runffmpegWithStreamingUrl = String.format("%s -i %s ", ffmpegPath,
			inputStreamIp + "/" + owner + "@gmail.com");
		String loggingSettings = "-hide_banner -loglevel info -stats ";
		String defaultCodecSettings = "-c:v libx264 -c:a aac "; // 기본 비디오 코덱은 libx264, 오디오 코덱은 aac 자막 파일인 vtt 파일은 생성하지 않는다.
		String hlsSegmentSettings = String.format("-hls_flags temp_file -hls_time %d -hls_list_size %d ", 2,
			6); // .m3u8 파일에 포함될 세그먼트의 최대 개수
		String m3u8SaveSettings = String.format("-strftime 1 -f hls -sn %s/%s.m3u8 ", "videos",
			owner);// -strftime 1 현재 시각을 사용하여 videos/지정한파일명.m3u8 로 저장합니다.
		String thumbnailSaveSettings = String.format("-vf fps=1/10 -q:v 2 %s -y",
			thumbnailOutputPathAndName); // -y 옵션 : overwrite할지 물어보는 경우가 있다.

		// ffmpeg 실행파일 -i rtmp 요청 들어오는 주소
		// 비디오,오디오 코덱 설정
		// hls를 지원할때 세그먼트 설정 (세그먼트의 길이 등)
		// 현재시각을 활용해서 해당 이름으로 .m3u8파일을 만들어라
		// 썸네일을 10초마다 한개씩 2의 화질로 어떤 경로에 만들어라

		String command =
			runffmpegWithStreamingUrl + loggingSettings + defaultCodecSettings + hlsSegmentSettings + m3u8SaveSettings
				+ thumbnailSaveSettings;

		log.info(command);

		return getSplitCommand(command);
	}

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

	public List<String> getDefault(String email, String streamerName) {
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
			.setThumbnailFileName(streamerName)
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
