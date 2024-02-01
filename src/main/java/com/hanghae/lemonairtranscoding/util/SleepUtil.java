package com.hanghae.lemonairtranscoding.util;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SleepUtil {
	public static void sleepByMillis(long milli) {
		try {
			TimeUnit.MILLISECONDS.sleep(milli);
		} catch (InterruptedException e) {
			log.error("InterruptedException 발생");
			throw new RuntimeException(e);
		}
	}

	public static void sleepBySec(int sec) {
		try {
			TimeUnit.SECONDS.sleep(sec);
		} catch (InterruptedException e) {
			log.error("InterruptedException 발생");
			throw new RuntimeException(e);
		}
	}
}
