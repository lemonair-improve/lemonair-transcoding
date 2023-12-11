package com.hanghae.lemonairtranscoding.learntest;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class PubSubTest {

	static AtomicInteger atomicInteger = new AtomicInteger(1);
	static SchedulerA schedulerA = new SchedulerA();
	static SchedulerB schedulerB = new SchedulerB();

	@Test
	void notUsePubSub() {
		log.info("publish, subscribe시에 특별히 지정하지 않으면 모든 작업을 싱글스레드 -> 즉 동기로 작업한다.");
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
		// publisOn 이전의 작업은 Main Thread가,
		// 이후의 작업은 스케쥴러가 역임한다.
		System.out.println("빠른 publisher와 느린 subscriber로 chain이 구성될 때 사용함");
		Flux.range(1, 3).map(i -> {
			System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + i);
			return i * 10;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + i);
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
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + i);
			return i * 10;
		}).publishOn(schedulerA).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + i);
			return i * 10;
		}).publishOn(schedulerB).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + i);
		});
	}

	@Test
	void publishOnTwiceWithBoundedElasticScheduler() {
		Flux.range(1, 5).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
		});
	}

	@Test
	void subscribeOn() {
		// 이전에 정의한 모든 체인들도 다 subscribeOn 때문에 schedulerA 가 책임진다.
		// 따라서 느린 publishe 와 빠른 subscriber일때 필요함

		Flux.range(1, 5).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
		});
	}

	@Test
	void subscribeOnTwice() {
		//map1 map2 사이에 subscriber로 스케쥴러b
		//map2 map3 사이에 subscriber로 스케쥴러a
		// 체인의 뒤에서부터 스케쥴링하므로 아래와 같은 과정이 실행됨

		// 스케쥴러 b가 앞뒤 publisher chain, subscriber chain을 담당
		// 스케쥴러 a가 앞뒤 pub chain, sub chain 을 담당함. 
		// 스케쥴러 b의에서 먼저 쓰레드를 생성하여 task를 줬으나, 체인의 끝(그러니까 시작점)까지 도달하기 전에 다른 subscriber에게 뺏겼다.
		// 스테쥴러 a에서 생성된 두번째 쓰레드가 모든 작업을 역임함
		Flux.range(1, 5).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerB).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
		});
	}

	@Test
	void subscribeAfterPublish() {

		// chain의 앞에서 부터 이해한다면
		// map1 과 map2, map3, subscribe 작업을 분리해서
		// map2, map3, subscribe 작업은 schedulerA가 맡게된다.
		// map1은 아직 main thread가 맡는 상태

		// map2와 map3 사이에 있는 subscribeOn schedulerB에 의해
		// 앞 뒤 체인을 뺏어오기때문에 스케쥴러 A가 맡지 않은 main thread의 map1의 작업을 빼앗아 오게 된다.
		/**
		 * map1 : b
		 * map2 : a
		 * map3 : a
		 * subscribe : a
		 */

		// chain의 뒤에서 부터 이해한다면
		// schedulerB가 전체 작업을 모두 맡게된다
		// map1, map2 사이에 publishOn(schedulerA) 코드가 있으므로 map1 이후의 작업은 schedulerA가 맡게된다
		// 결과는 전체 작업을 맡기로한 스케쥴러 b가 a에게 map1 이후의 작업을 뺏기게 된다.
		/**
		 * map1 : b
		 * map2 : a
		 * map3 : a
		 * subscribe : a
		 */

		// 혼합해서 사용하면 publishOn에 의해 분리된 스케쥴러가 먼저 쓰레드를 생성한다.

		Flux.range(1, 5).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(schedulerA).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerB).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
		});
	}

	@Test
	void publishAfterSubscribe() {

		//chain의 뒤에서 부터 이해하기,
		// schedulerB가 map3, subscribe 작업을 맡기로 했다.
		// schedulerA가 앞뒤 체인들중 schedule 되지 않은 task들 모두룰 가져간다.
		/**
		 * map1 : a
		 * map2 : a
		 * map3 : b
		 * subscribe : b
		 */

		// 혼합해서 사용하면 publishOn에 의해 분리된 스케쥴러가 먼저 쓰레드를 생성한다.

		Flux.range(1, 5).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(schedulerB).map(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribe(i -> {
			System.out.println("쓰레드 : " + Thread.currentThread().getName() + " " + i);
		});
	}

	@Test
	void publishOuterPublishInnerWithBondedElasticScheduler() {
		// inner Flux에서 publish한 구역이 nestes map 2 이후이므로 nested map 1 에는 스케쥴러가 할당되지 않았음
		// outer Flux에서 map 2 이후로 publish 하기로했으므로, outer flux map 2 & inner flux nested map 1 만큼을 역임한다.
		// 스케쥴러를 부여받지 못한 map 1 task는 main thread가 수행한다.
		Flux.range(1, 3).map(i -> {
			System.out.println("1st map():" + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(Schedulers.boundedElastic()).map(i -> {
			System.out.println("2nd map():" + Thread.currentThread().getName() + " " + i);
			return Flux.fromIterable(Arrays.asList(1, 2)).map(j -> {
				System.out.println("nested 1st map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).publishOn(Schedulers.boundedElastic()).map(j -> {
				System.out.println("nested 2nd map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribe(j -> {
				System.out.println("subscribe():" + Thread.currentThread().getName() + " " + i + " " + j);
			});
		}).subscribe();

		/**
		 1st map():Test worker 1
		 1st map():Test worker 2
		 1st map():Test worker 3

		 publishOn에 의해 스케쥴링, 첫번째 쓰레드가 작업을 시작한다.
		 2nd map():boundedElastic-1 10
		 2nd map():boundedElastic-1 20
		 2nd map():boundedElastic-1 30
		 nested 1st map():boundedElastic-1 10 1
		 nested 1st map():boundedElastic-1 10 2

		 bondedElastic 스케쥴러에게 다시 publish, 두번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-2 10 10
		 nested 2nd map():boundedElastic-2 10 20
		 subscribe():boundedElastic-2 10 100
		 subscribe():boundedElastic-2 10 200

		 nested 1st map():boundedElastic-1 20 1
		 nested 1st map():boundedElastic-1 20 2

		 boundedElastic 스케쥴러에게 다시 publish, 세번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-3 20 10
		 nested 2nd map():boundedElastic-3 20 20
		 subscribe():boundedElastic-3 20 100
		 subscribe():boundedElastic-3 20 200


		 nested 1st map():boundedElastic-1 30 1
		 nested 1st map():boundedElastic-1 30 2

		 boundedElastic 스케쥴러에게 다시 publish, 네번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-4 30 10
		 nested 2nd map():boundedElastic-4 30 20
		 subscribe():boundedElastic-4 30 100
		 subscribe():boundedElastic-4 30 200
		 */
	}

	@Test
	void publishOuterPublishInnerWithCustomScheduler() {

		// nested 1st map 이후에 publishOn(schedulerB) 코드에 의해서 각 작업이 다른 쓰레드에게 역임되고있음

		// 내부적으로는 schedulerB 가 nested map2, nested subscribe 작업을 맡기로 하고 거슬러 올라가서
		// map2, nested map1 을 schedulerA가 맡게된다. 따라서 schedulerA 가 map2 부분에서  Flux.map 작업을 return되는 Flux.map 3개에 대해서 수행해야하므로
		// 각 3개의 task를 아까 역임하기로한 schedulerB에게 전달한다. 따라서 schedulerB는 3개의 쓰레드를 운용하게됨
		Flux.range(1, 3).map(i -> {
			System.out.println("1st map():" + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(schedulerA).map(i -> {
			System.out.println("2nd map():" + Thread.currentThread().getName() + " " + i);
			return Flux.fromIterable(Arrays.asList(1, 2)).map(j -> {
				System.out.println("nested 1st map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).publishOn(schedulerB).map(j -> {
				System.out.println("nested 2nd map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribe(j -> {
				System.out.println("subscribe():" + Thread.currentThread().getName() + " " + i + " " + j);
			});
		}).subscribe();

		/**
		 1st map():Test worker 1
		 1st map():Test worker 2
		 1st map():Test worker 3

		 publishOn에 의해 스케쥴링, 첫번째 쓰레드가 작업을 시작한다.
		 2nd map():boundedElastic-1 10
		 2nd map():boundedElastic-1 20
		 2nd map():boundedElastic-1 30
		 nested 1st map():boundedElastic-1 10 1
		 nested 1st map():boundedElastic-1 10 2

		 bondedElastic 스케쥴러에게 다시 publish, 두번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-2 10 10
		 nested 2nd map():boundedElastic-2 10 20
		 subscribe():boundedElastic-2 10 100
		 subscribe():boundedElastic-2 10 200

		 nested 1st map():boundedElastic-1 20 1
		 nested 1st map():boundedElastic-1 20 2

		 boundedElastic 스케쥴러에게 다시 publish, 세번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-3 20 10
		 nested 2nd map():boundedElastic-3 20 20
		 subscribe():boundedElastic-3 20 100
		 subscribe():boundedElastic-3 20 200


		 nested 1st map():boundedElastic-1 30 1
		 nested 1st map():boundedElastic-1 30 2

		 boundedElastic 스케쥴러에게 다시 publish, 네번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-4 30 10
		 nested 2nd map():boundedElastic-4 30 20
		 subscribe():boundedElastic-4 30 100
		 subscribe():boundedElastic-4 30 200
		 */
	}

	@Test
	void subscribeOuterPublishInnerWithCustomScheduler() throws InterruptedException {
		// 특이한 점은 test worker Thread를 sleep해서 대기하지않으면 모두 출력되지 않는다는것.

		// inner Flux에 대해서 nested map 1 이후의 작업은 publishOn(schedulerB) 에 의해 schedulerB가 맡게 된다.
		// outer Flux에 대해서는 map1과 map2 사이에 publishOn(schedulerA)에 의해 map2작업부터 schedulerA가 맡게 된다.
		Flux.range(1, 3).map(i -> {
			System.out.println("1st map():" + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			System.out.println("2nd map():" + Thread.currentThread().getName() + " " + i);
			return Flux.fromIterable(Arrays.asList(1, 2)).map(j -> {
				System.out.println("nested 1st map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).publishOn(schedulerB).map(j -> {
				System.out.println("nested 2nd map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribe(j -> {
				System.out.println("subscribe():" + Thread.currentThread().getName() + " " + i + " " + j);
			});
		}).subscribe();

		Thread.sleep(1000L);
		/**
		 1st map():Test worker 1
		 1st map():Test worker 2
		 1st map():Test worker 3

		 publishOn에 의해 스케쥴링, 첫번째 쓰레드가 작업을 시작한다.
		 2nd map():boundedElastic-1 10
		 2nd map():boundedElastic-1 20
		 2nd map():boundedElastic-1 30
		 nested 1st map():boundedElastic-1 10 1
		 nested 1st map():boundedElastic-1 10 2

		 bondedElastic 스케쥴러에게 다시 publish, 두번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-2 10 10
		 nested 2nd map():boundedElastic-2 10 20
		 subscribe():boundedElastic-2 10 100
		 subscribe():boundedElastic-2 10 200

		 nested 1st map():boundedElastic-1 20 1
		 nested 1st map():boundedElastic-1 20 2

		 boundedElastic 스케쥴러에게 다시 publish, 세번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-3 20 10
		 nested 2nd map():boundedElastic-3 20 20
		 subscribe():boundedElastic-3 20 100
		 subscribe():boundedElastic-3 20 200


		 nested 1st map():boundedElastic-1 30 1
		 nested 1st map():boundedElastic-1 30 2

		 boundedElastic 스케쥴러에게 다시 publish, 네번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-4 30 10
		 nested 2nd map():boundedElastic-4 30 20
		 subscribe():boundedElastic-4 30 100
		 subscribe():boundedElastic-4 30 200
		 */
	}

	@Test
	void subscribeOuterSubscribeInnerWithCustomScheduler() {

		// inner Flux에 대해서 nested map 1 이후의 작업은 publishOn(schedulerB) 에 의해 schedulerB가 맡게 된다.
		// outer Flux에 대해서는 map1과 map2 사이에 publishOn(schedulerA)에 의해 map2작업부터 schedulerA가 맡게 된다.
		Flux.range(1, 3).map(i -> {
			System.out.println("1st map():" + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).subscribeOn(schedulerA).map(i -> {
			System.out.println("2nd map():" + Thread.currentThread().getName() + " " + i);
			return Flux.fromIterable(Arrays.asList(1, 2)).map(j -> {
				System.out.println("nested 1st map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribeOn(schedulerB).map(j -> {
				System.out.println("nested 2nd map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribe(j -> {
				System.out.println("subscribe():" + Thread.currentThread().getName() + " " + i + " " + j);
			});
		}).subscribe();

		/**
		 1st map():Test worker 1
		 1st map():Test worker 2
		 1st map():Test worker 3

		 publishOn에 의해 스케쥴링, 첫번째 쓰레드가 작업을 시작한다.
		 2nd map():boundedElastic-1 10
		 2nd map():boundedElastic-1 20
		 2nd map():boundedElastic-1 30
		 nested 1st map():boundedElastic-1 10 1
		 nested 1st map():boundedElastic-1 10 2

		 bondedElastic 스케쥴러에게 다시 publish, 두번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-2 10 10
		 nested 2nd map():boundedElastic-2 10 20
		 subscribe():boundedElastic-2 10 100
		 subscribe():boundedElastic-2 10 200

		 nested 1st map():boundedElastic-1 20 1
		 nested 1st map():boundedElastic-1 20 2

		 boundedElastic 스케쥴러에게 다시 publish, 세번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-3 20 10
		 nested 2nd map():boundedElastic-3 20 20
		 subscribe():boundedElastic-3 20 100
		 subscribe():boundedElastic-3 20 200


		 nested 1st map():boundedElastic-1 30 1
		 nested 1st map():boundedElastic-1 30 2

		 boundedElastic 스케쥴러에게 다시 publish, 네번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-4 30 10
		 nested 2nd map():boundedElastic-4 30 20
		 subscribe():boundedElastic-4 30 100
		 subscribe():boundedElastic-4 30 200
		 */
	}

	@Test
	void publishOuterSubscribeInnerWithCustomScheduler() {

		// inner Flux에 대해서 모든 작업은 subscribeOn(schedulerB) 에 의해 schedulerB가 맡게 된다.
		// outer Flux에 대해서는 map1과 map2 사이에 publishOn(schedulerA)에 의해 map2작업부터 schedulerA가 맡게 된다.
		Flux.range(1, 3).map(i -> {
			System.out.println("1st map():" + Thread.currentThread().getName() + " " + i);
			return i * 10;
		}).publishOn(schedulerA).map(i -> {
			System.out.println("2nd map():" + Thread.currentThread().getName() + " " + i);
			return Flux.fromIterable(Arrays.asList(1, 2)).map(j -> {
				System.out.println("nested 1st map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribeOn(schedulerB).map(j -> {
				System.out.println("nested 2nd map():" + Thread.currentThread().getName() + " " + i + " " + j);
				return j * 10;
			}).subscribe(j -> {
				System.out.println("subscribe():" + Thread.currentThread().getName() + " " + i + " " + j);
			});
		}).subscribe();
		/**
		 1st map():Test worker 1
		 1st map():Test worker 2
		 1st map():Test worker 3

		 publishOn에 의해 스케쥴링, 첫번째 쓰레드가 작업을 시작한다.
		 2nd map():boundedElastic-1 10
		 2nd map():boundedElastic-1 20
		 2nd map():boundedElastic-1 30
		 nested 1st map():boundedElastic-1 10 1
		 nested 1st map():boundedElastic-1 10 2

		 bondedElastic 스케쥴러에게 다시 publish, 두번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-2 10 10
		 nested 2nd map():boundedElastic-2 10 20
		 subscribe():boundedElastic-2 10 100
		 subscribe():boundedElastic-2 10 200

		 nested 1st map():boundedElastic-1 20 1
		 nested 1st map():boundedElastic-1 20 2

		 boundedElastic 스케쥴러에게 다시 publish, 세번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-3 20 10
		 nested 2nd map():boundedElastic-3 20 20
		 subscribe():boundedElastic-3 20 100
		 subscribe():boundedElastic-3 20 200


		 nested 1st map():boundedElastic-1 30 1
		 nested 1st map():boundedElastic-1 30 2

		 boundedElastic 스케쥴러에게 다시 publish, 네번째 쓰레드가 작업을 시작한다.

		 nested 2nd map():boundedElastic-4 30 10
		 nested 2nd map():boundedElastic-4 30 20
		 subscribe():boundedElastic-4 30 100
		 subscribe():boundedElastic-4 30 200
		 */
	}

	@Test
	void executionTime1() throws InterruptedException {
		int cases = 4;
		AtomicLong[] befores = new AtomicLong[cases];
		for (int i = 0; i < 4; i++) {
			befores[i] = new AtomicLong();
		}
		int count = 10000000;

		/**
		 * pub sub 활용하지 않으면 194 ms
 		 */

		Flux.range(1, count)
			.doOnSubscribe(s -> befores[0].set(System.currentTimeMillis()))
			.map(i -> i * 11)
			.filter(i -> i % 100 == 17)
			// .subscribeOn(Schedulers.boundedElastic())
			.map(i -> i / 100)
			.doOnComplete(() -> {
				long after = System.currentTimeMillis();
				long elaspedTime = after - befores[0].get();
				System.out.println("메인 쓰레드만 작업");
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + elaspedTime + " 밀리초");
			})
			.subscribe();

		/**
		 * map1 <-> map2 사이 sub 103 ms
		 */

		Flux.range(1, count)
			.doOnSubscribe(s -> befores[1].set(System.currentTimeMillis()))
			.map(i -> i * 11)
			.filter(i -> i % 100 == 17)
			.subscribeOn(Schedulers.boundedElastic())
			.map(i -> i / 100)
			.doOnComplete(() -> {
				long after = System.currentTimeMillis();
				long elaspedTime = after - befores[1].get();
				System.out.println("map1과 map2 사이 sub");
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + elaspedTime + " 밀리초");
			})
			.subscribe();

		/**
		 * map1 <-> map2 사이 pub 129 ms
		 */

		Flux.range(1, count)
			.doOnSubscribe(s -> befores[2].set(System.currentTimeMillis()))
			.map(i -> i * 11)
			.filter(i -> i % 100 == 17)
			.publishOn(Schedulers.boundedElastic())
			.map(i -> i / 100)
			.doOnComplete(() -> {
				long after = System.currentTimeMillis();
				long elaspedTime = after - befores[2].get();
				System.out.println("map1과 map2 사이 pub");
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + elaspedTime + " 밀리초");
			})
			.subscribe();

		/**
		 * map1 이전 sub 124ms
		 */

		Flux.range(1, count)
			.doOnSubscribe(s -> befores[3].set(System.currentTimeMillis()))
			.subscribeOn(Schedulers.boundedElastic())
			.map(i -> i * 11)
			.filter(i -> i % 100 == 17)
			.map(i -> i / 100)
			.doOnComplete(() -> {
				long after = System.currentTimeMillis();
				long elaspedTime = after - befores[3].get();
				System.out.println("map1이전 sub");
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + elaspedTime + " 밀리초");
			})
			.subscribe();

		/**
		 * map1 이전 sub 124ms
		 */

		Flux.range(1, count)
			.doOnSubscribe(s -> befores[3].set(System.currentTimeMillis()))
			.publishOn(Schedulers.boundedElastic())
			.map(i -> i * 11)
			.filter(i -> i % 100 == 17)
			.map(i -> i / 100)
			.doOnComplete(() -> {
				long after = System.currentTimeMillis();
				long elaspedTime = after - befores[3].get();
				System.out.println("map1이전 pub");
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + elaspedTime + " 밀리초");
			})
			.subscribe();

		Thread.sleep(1000L);
	}

	public static class SchedulerA implements Scheduler {

		private static final AtomicInteger atomicInteger = new AtomicInteger(0);

		@Override
		public Disposable schedule(Runnable task) {
			System.out.println("스케쥴러 A schedule 함수 실행 " + atomicInteger.get() + " 번째로 schedule 함수 호출");
			Thread thread = new Thread(task, "스케쥴러A 에 의해 생성된 " + atomicInteger.getAndIncrement() + " 번째 쓰레드");
			thread.start();
			return () -> {
				thread.interrupt();
			};
		}

		@Override
		public Worker createWorker() {
			return new CustomWorker();
		}

		private static class CustomWorker implements Worker {
			@Override
			public Disposable schedule(Runnable task) {
				System.out.println("스케쥴러 A schedule 함수 실행 " + atomicInteger.get() + " 번째로 schedule 함수 호출");
				Thread thread = new Thread(task, "스케쥴러A 에 의해 생성된 " + atomicInteger.getAndIncrement() + " 번째 쓰레드");
				thread.start();
				return () -> {
					thread.interrupt();
				};
			}

			@Override
			public void dispose() {
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + "WorkerA disposed");
			}
		}
	}

	public static class SchedulerB implements Scheduler {

		private static final AtomicInteger atomicInteger = new AtomicInteger(0);

		@Override
		public Disposable schedule(Runnable task) {
			System.out.println("스케쥴러 B schedule 함수 실행 " + atomicInteger.get() + " 번째로 schedule 함수 호출");
			Thread thread = new Thread(task, "스케쥴러B 에 의해 생성된 " + atomicInteger.getAndIncrement() + " 번째 쓰레드");
			thread.start();
			return () -> {
				thread.interrupt();
			};
		}

		@Override
		public Worker createWorker() {
			return new CustomWorker();
		}

		private static class CustomWorker implements Worker {
			@Override
			public Disposable schedule(Runnable task) {
				System.out.println("스케쥴러 B schedule 함수 실행 " + atomicInteger.get() + " 번째로 schedule 함수 호출");
				Thread thread = new Thread(task, "스케쥴러B 에 의해 생성된 " + atomicInteger.getAndIncrement() + " 번째 쓰레드");
				thread.start();
				return () -> {
					thread.interrupt();
				};
			}

			@Override
			public void dispose() {
				System.out.println(String.format("쓰레드 : %s ", Thread.currentThread().getName()) + "WorkerA disposed");
			}
		}
	}
}
