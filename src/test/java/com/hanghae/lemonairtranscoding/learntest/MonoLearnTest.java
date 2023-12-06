package com.hanghae.lemonairtranscoding.learntest;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MonoLearnTest {

	//Mono 역시 Flux와 마찬가지로 Reactive Stream의 Publisher를 상속받은 것을 확인할 수 있습니다.
	//
	// 따라서 Mono도 아이템을 produce할 수 있고, subscriber가 subscribe하면 Emit을 할 수 있다는 것은 동일합니다.

	@Test
	void MonoClassNameLearnTest() {
		Mono<String> noData = Mono.empty();
		Mono<String> data = Mono.just("hello-mono");
		System.out.println("Mono.empty()의 클래스명 : " + noData.getClass().getName());
		System.out.println("Mono.just(value)의 클래스명 : " + data.getClass().getName());
		System.out.println("Mono.empty().subscribe()의 클래스명 : " + noData.subscribe().getClass().getName());
		System.out.println("Mono.just(value).subscribe() 의 클래스명 : " + noData.subscribe().getClass().getName());
	}

	@Test
	void subscribeMonoLearnTest1() {
		Mono<Integer> monoInt = Mono.just(1);
		monoInt.subscribe(System.out::println);
	}

	@Test
	void subscribeFluxLearnTest1() {
		Flux<int[]> fluxInt = Flux.fromStream(Stream.of(new int[] {1, 2, 3, 4, 5, 6}));
		System.out.println(" flux는 stream 안에 들어있는 T 형식 그대로 사용합니다.");
		fluxInt.subscribe(intArray -> {
			for (int i = 0; i < intArray.length; i++) {
				System.out.println(intArray[i]);
			}
		});
	}

	@Test
	void exceptionInSubscribeTest() {
		Flux<Integer> fluxInt = Flux.range(1, 10)
			.map(integer -> {
				if (integer % 2 == 1)
					return integer;
				throw new RuntimeException("로직상 짝수는 예외처리됩니다.");
			});
		fluxInt.subscribe(System.out::println);
		System.out.println("subscribe 메소드에서 예외 발생시 예외가 subscribe 메소드 외부로 throw되지 않습니다.");
		System.out.println("대신 예외가 발생하면 errorConsumer 예외 처리를 맡기고 더이상 consumer가 작업을 진행하지 않습니다.");

		System.out.println("따라서 assertThatThrownBy 등의 예외 테스트 메소드로 예외가 발생하는지 여부를 파악할 수 없음.");

		AtomicInteger errorCount = new AtomicInteger();
		fluxInt.subscribe(System.out::println, error -> {
			errorCount.getAndIncrement();
			System.out.println("subscribe 메서드에서 consumer 예외를 errorConsumer가 처리 : " + error);
		});


		assertThat(errorCount.get()).isEqualTo(1);
	}

	@Test
	void cancelSubscribeTest() {
		// subscribe는 Disposable 리턴타입을 갖는다. 만약 dispose() 메서드가 호출되면 subscription이 종료됨
		// 더이상 Mono or Flux 안의 요소를 producing 하지 않도록 cancle하는 것이 일반적인데, dispose함수가 실행되는 즉시 종료될 것이라는 것은 보장되지 ㅇ낳는다.

		Flux.range(1, 10)
			.subscribe(System.out::println)
			.dispose();

		Flux.range(1, 10)
			.subscribe(System.out::println);

		//Flux.range(1, 1000).subscribe(System.out::println).dispose();
		//
		// 첫 번째 Flux는 구독 후 즉시 dispose() 메서드를 호출하여 subscription을 취소합니다. 이는 Flux가 데이터를 생성하고 나면 바로 subscription을 종료하게 됩니다.
		// 따라서 첫 번째 Flux는 1부터 1000까지의 모든 요소를 생성하지만, subscribe 직후에 dispose()가 호출되어 더 이상의 데이터를 생성하지 않고 subscription이 종료됩니다.
		// Flux.range(1, 1000).subscribe(System.out::println);
		//
		// 두 번째 Flux는 구독 후에 명시적으로 dispose() 메서드를 호출하지 않습니다. 따라서 subscription은 명시적으로 종료되지 않고 계속 유지됩니다.
		// 이 경우에는 Flux가 1부터 1000까지의 모든 요소를 생성하고 계속해서 subscription이 유지되므로, 데이터가 생성되는 동안 계속해서 출력이 이어집니다.

		System.out.println();
	}

}
