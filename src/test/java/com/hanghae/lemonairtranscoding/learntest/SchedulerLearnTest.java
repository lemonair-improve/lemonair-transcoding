package com.hanghae.lemonairtranscoding.learntest;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class SchedulerLearnTest {
	//immediate는 스케쥴러가 필요하지만 쓰레드를 변경하고 싶지 않을 때
	// single은 일회성으로 사용하려고 쓰레드풀을 만들 때
	// parallel은 병렬로 여러 태스크를 처리할 때(cpu에 의존하지만 짧은 태스크 처리용), 쓰레드풀 크기는 고정됨
	// elastic은 긴 태스크에 대해 IO blocking이 발생할 때
	// boundedElastic은 elastic과 비슷하지만 쓰레드풀의 크기를 제한함. 입출력 작업에 적합함 - 추천



	@Test
	void customScheduler(){
		Scheduler scheduler = Schedulers.newBoundedElastic(10, 100, "subscriber1");
		Flux.range(1, 100)
			.map(i ->{
				System.out.println(Thread.currentThread().getName());
				return i*10;
			}).subscribeOn(scheduler)
			.subscribe(System.out::println);



	}
}
