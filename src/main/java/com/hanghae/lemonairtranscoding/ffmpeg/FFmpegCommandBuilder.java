package com.hanghae.lemonairtranscoding.ffmpeg;

import static com.hanghae.lemonairtranscoding.ffmpeg.FFmpegCommandConstants.*;

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

	// 일반적인 방법으로는 썸네일에 현재날짜시각을 추가하여 저장하는 방법이 없음 1,2,3,4로...
	private final String THUMBNAIL_SERIAL_NUMBER_POSTFIX = "_thumbnail_%04d.jpg";

	/**
	 * 1. inputStream 지정 <br>
	 * 2. banner 출력 여부 <br>
	 * 3. logging level 지정 <br>
	 * 4. 종료 후 통계 출력 여부 지정 <br>
	 * 5. 비디오 코덱 지정 <br>
	 * 6. 오디오 코덱 지정 <br>
	 * 7. tempfile 쓰기 작업에 활용 여부 <br>
	 * 8. 세그먼트 하나의 길이 <br>
	 * 9. 세그먼트 리스트 크기 <br>
	 * 10. ts파일에 timestamp 적용 여부 <br>
	 * 11. output타입 지정 <br>
	 * 12. VTT(자막파일) 생성 여부 <br>
	 * 13. m3u8 파일명 지정(스트리머 이름) <br>
	 * 14. 썸네일 생성 주기 지정 <br>
	 * 15. 썸네일 품질 지정 <br>
	 * 16. 썸네일 파일명 지정(스트리머 이름)
	 */
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

	public FFmpegCommandBuilder setInputStreamPathVariable(String email) {
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
	public FFmpegCommandBuilder setLoggingLevel(int loggingLevel) {
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

	public FFmpegCommandBuilder timeStampFileNaming(boolean use){
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
		return this.setInputStreamPathVariable(email)
			.printFFmpegBanner(false)
			.setLoggingLevel(LOGGING_LEVEL_INFO)
			.printStatistic(true)
			.setVideoCodec(VIDEO_H264)
			.setAudioCodec(AUDIO_AAC)
			.useTempFileWriting(true)
			.setSegmentUnitTime(10)
			.setSegmentListSize(2)
			.timeStampFileNaming(true)
			.setOutputType(OUTPUT_TYPE_HLS)
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
