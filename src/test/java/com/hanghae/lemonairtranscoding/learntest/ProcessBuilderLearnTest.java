package com.hanghae.lemonairtranscoding.learntest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ProcessBuilderLearnTest {

	//subscribe 함수:
	//
	// subscribe 함수는 Mono나 Flux를 구독(Subscribe)하여 해당 스트림의 이벤트를 처리할 때 사용됩니다.
	// subscribe 함수를 호출하면 Mono나 Flux의 Publisher에 구독이 시작되며, 데이터 흐름이 시작됩니다.
	// subscribe 함수에는 Subscriber를 직접 지정할 수 있어서 다양한 이벤트 핸들링을 지원합니다.
	// doOnNext 함수:
	//
	// doOnNext 함수는 각 데이터 항목이 방출될 때 특정 동작을 수행하고자 할 때 사용됩니다.
	// doOnNext 함수는 해당 Mono나 Flux의 각 항목이 방출될 때 어떤 동작을 추가하고자 할 때 활용됩니다.
	// doOnNext 함수는 데이터를 변형하지 않고, 단순히 데이터의 특정 이벤트에 대한 부가적인 동작을 정의할 때 사용됩니다.
	@Test
	void ProcessBuilderBlockingCallTest() {
		// start()는 블로킹 호출, 프로세스가 시작되면 그 후의 동작을 진행한다.
		long startTime = System.currentTimeMillis();
		Mono.fromCallable(() -> powerShellProcess(0L).start())
			.subscribeOn(Schedulers.boundedElastic())
			.doOnSuccess(process -> {
				new Thread(() -> {
					try {
						InputStream inputStream = process.getInputStream();
						BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
						String line;
						while ((line = reader.readLine()) != null) {
							System.out.println(line);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}).start();
			}).block();

		System.out.println(" 블로킹 되긴 하지만 대기 시간이 없는 경우" + (System.currentTimeMillis() - startTime));

		startTime = System.currentTimeMillis();
		Mono.fromCallable(() -> powerShellProcess(3000L).start()).subscribeOn(Schedulers.boundedElastic())
			.doOnSuccess(process -> {
				new Thread(() -> {
					try {
						Thread.sleep(3000L);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
					try {
						InputStream inputStream = process.getInputStream();
						BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
						String line;
						while ((line = reader.readLine()) != null) {
							System.out.println(line);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}).start();
			}).block();
		System.out.println(" 블로킹되고 대기시간 3초인 경우" + (System.currentTimeMillis() - startTime));

	}

	ProcessBuilder powerShellProcess(Long waitMillisecond) throws InterruptedException {
		Thread.sleep(waitMillisecond);
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.inheritIO();
		processBuilder.command("powershell.exe", "echo hello-powershell");
		return processBuilder;
	}

}
