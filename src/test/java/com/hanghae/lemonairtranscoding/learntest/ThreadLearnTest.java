package com.hanghae.lemonairtranscoding.learntest;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadLearnTest {

	@Test
	void startThread(){
		Thread thread = new CustomThread();
		thread.start();
		log.info("");
	}

	static class CustomThread extends Thread {
		@Override
		public void run(){
			log.info("");
		}

	}
}
