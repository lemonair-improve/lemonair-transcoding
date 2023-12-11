package com.hanghae.lemonairtranscoding.learntest;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class PubSubTest {



	@Test
	void notUsePubSub() {
		Flux.range(1, 3).map(i -> {
			log.info("first map");
			return i;
		}).map(i -> {
			log.info("second map");
			return i;
		}).subscribe(i -> {
			log.info("subscribe");
		});
	}

	@Test
	void publishOn1() {
		log.info("빠른 publisher와 느린 subscriber로 chain이 구성될 때 사용함");
		Flux.range(1, 3).map(i -> {
			log.info("first map");
			return i;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			log.info("second map");
			return i;
		}).subscribe(i -> {
			log.info("subscribe");
		});
	}


	// 서술식 순서와 실제 스케쥴링 되는 순서는 같지 않다.
	// 스케쥴링은 체인을 거슬러 올라가면서 진행되기때문임
	// 그러니까 SchedulerB가 먼저 스케쥴링되고 나서 schedulerA가 스케쥴링된다.
	@Test
	void publishOnTwiceWithCustomScheduler() {
		SchedulerA schedulerA = new SchedulerA();
		SchedulerB schedulerB = new SchedulerB();
		Flux.range(1, 5).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).publishOn(schedulerA).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).publishOn(schedulerB).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).subscribe(i -> {
			log.info("Subscribe + " + String.valueOf(i));
		});
	}


	@Test
	void publishOnTwiceWithBoundedElasticScheduler() {
		Flux.range(1, 5).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).subscribe(i -> {
			log.info(String.valueOf(i));
		});
	}

	@Test
	void subscribeOn(){
		// 이전에 정의한 모든 체인들도 다 subscribeOn 때문에 schedulerA 가 책임진다.
		// 따라서 느린 publishe 와 빠른 subscriber일때 필요함
		SchedulerA schedulerA = new SchedulerA();

		Flux.range(1, 5).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).subscribe(i -> {
			log.info(String.valueOf(i));
		});
	}

	@Test
	void subscribeAfterPublish(){

		// 이전에 정의한 모든 체인들도 다 subscribeOn 때문에 schedulerA 가 책임진다.
		// 그런데 이전에 정의한 체인들 사이 publishOn이 있다면?

		SchedulerA schedulerA = new SchedulerA();
		SchedulerB schedulerB = new SchedulerB();

		Flux.range(1, 5).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).publishOn(schedulerB).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			log.info(String.valueOf(i));
			return i * 10;
		}).subscribe(i -> {
			log.info(String.valueOf(i));
		});
	}



	public static class SchedulerA implements Scheduler{

		@Override
		public Disposable schedule(Runnable task) {
			return null;
		}

		@Override
		public Worker createWorker() {
			return new CustomWorker();
		}

		private static class CustomWorker implements Worker{
			@Override
			public Disposable schedule(Runnable task) {
				log.info("스케쥴러 A schedule 함수 실행");
				Thread thread = new Thread(task, "쓰레드 A");
				thread.start();
				return () ->{
					thread.interrupt();
				};
			}
			@Override
			public void dispose() {
				log.error("WorkerA disposed");
			}
		}
	}

	public static class SchedulerB implements Scheduler{

		@Override
		public Disposable schedule(Runnable task) {
			return null;
		}

		@Override
		public Worker createWorker() {
			return new CustomWorker();
		}

		private static class CustomWorker implements Worker{

			@Override
			public Disposable schedule(Runnable task) {
				log.info("스케쥴러 B schedule 함수 실행");
				Thread thread = new Thread(task, "쓰레드 B");
				thread.start();
				return () ->{
					thread.interrupt();
				};
			}
			@Override
			public void dispose() {
				log.error("WorkerA disposed");
			}
		}
	}
}
