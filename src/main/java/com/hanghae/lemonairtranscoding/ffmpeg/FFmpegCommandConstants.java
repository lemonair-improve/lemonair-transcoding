package com.hanghae.lemonairtranscoding.ffmpeg;

public class FFmpegCommandConstants {

	/**
	 * Show nothing at all; be silent.
	 */
	public static final int LOGGING_LEVEL_QUIET = -8;

	/**
	 * Only show fatal errors which could lead the process to crash, such as an assertion failure. This is not currently used for anything.
	 */
	public static final int LOGGING_LEVEL_PANIC = 0;
	/**
	 * Only show fatal errors. These are errors after which the process absolutely cannot continue.
	 */
	public static final int LOGGING_LEVEL_FATAL = 8;
	/**
	 * Show all errors, including ones which can be recovered from.
	 */
	public static final int LOGGING_LEVEL_ERROR = 16;
	/**
	 * Show all warnings and errors. Any message related to possibly incorrect or unexpected events will be shown.
	 */
	public static final int LOGGING_LEVEL_WARNING = 24;
	/**
	 * Show informative messages during processing. This is in addition to warnings and errors. This is the default value.
	 */
	public static final int LOGGING_LEVEL_INFO = 32;
	/**
	 * Same as info, except more verbose.
	 */
	public static final int LOGGING_LEVEL_VERBOSE = 40;
	/**
	 * Show everything, including debugging information.
	 */
	public static final int LOGGING_LEVEL_DEBUG = 48;
	// ‘trace, 56’
	public static final int LOGGING_LEVEL_TRACE = 56;


	public static final String VIDEO_H264 ="libx264";
	public static final String VIDEO_H265 = "libx265";
	public static final String VIDEO_VP9 = "libvpx-vp9";

	public static final String AUDIO_AAC = "aac";
	public static final String AUDIO_MP3 = "libmp3lame";
	public static final String AUDIO_OPUS = "libopus";

	public static final String IMAGE_JPG = "mjpeg";
	public static final String IMAGE_PNG = "png";

	public static final String OUTPUT_TYPE_HLS = "hls";
	public static final String OUTPUT_TYPE_MPEG_TS = "mpegts";


	public static final int SEGMENTLIST_ALL = 0;

}
