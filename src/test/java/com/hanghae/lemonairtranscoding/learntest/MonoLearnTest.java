package com.hanghae.lemonairtranscoding.learntest;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MonoLearnTest {

	// Mono 역시 Flux와 마찬가지로 Reactive Stream의 Publisher를 상속받은 것을 확인할 수 있습니다.
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


	@Test
	void test(){
		//This is for synchronous and one-by-one emissions, meaning that the sink is a SynchronousSink
		// and that its next() method can only be called at most once per callback invocation.
		// You can then additionally call error(Throwable) or complete(), but this is optional.
		Flux<String> flux = Flux.generate(
			() -> 0,
			(state, sink) -> {
				sink.next("3 x " + state + " = " + 3*state);
				if (state == 10) sink.complete();
				return state + 1;
			});
		flux.subscribe(System.out::println);
	}

	@Test
	void mapAndSubscribeTwiceTest(){
		Flux<Integer> squared = Flux.range(1,100).map(x->x*x);
		squared.subscribe(x-> System.out.print(x + " "));
		System.out.println();
		squared.map(x -> x / 10).subscribe(x -> System.out.print(x+ " "));
		System.out.println("두 번 이상 subscribe할 수도 있다.");
	}


	//map과 flatMap은 둘 다 스트림의 중간에 값을 변환해주는 역할을 한다.
	// map은 1 : 1로 반환을 보증하고 flatMap은 1 : N을 변환할 수 있다.
	// 요청에 대해 N개를 병렬로 실행할 경우가 많지 않아 map을 많이 쓸 것 같지만 개발을 하다 보면 대다수의 경우 flatMap을 사용하게 된다.
	@Test
	void monoMapTest(){
		/**
		 * 아래 코드는 map 의 리턴 타입이 원래의 Mono<T> 타입과 동일하기 때문에 에러가 발생합니다.
		 */
		// Mono<Integer> intMono = Mono.just(1)
		// 	.map( x-> (float)x * 10)
		// 	.subscribe(System.out::println);

		Mono<Integer> intMono = Mono.just(1)
			.map( x-> x * 10);
		intMono.subscribe(System.out::println);
	}

	@Test
	void intMonoMapToDoubleMonoTest(){
		Mono<Double> doubleMono = Mono.fromSupplier(()-> 1).map(Integer::doubleValue);
		doubleMono.subscribe(System.out::println);
	}

	@Test
	void intMonoMapToStringMonoTest(){
		Mono<String> stringMono = Mono.fromSupplier(()->1).map(String::valueOf);
		stringMono.subscribe(x-> System.out.println(x instanceof String));
	}

	@Test
	void intMonoFlatMapToStringMonoTest(){
		Mono<String> stringMono = Mono.fromSupplier(()-> -123).flatMap( x-> Mono.just("toString :" + x));
		stringMono.subscribe(System.out::println);
	}

	/**
	 * Mono 와 Flux의 map, flatMap 메서드의 차이
	 * 1. map은 동기 처리, flatMap은 비동기 처리,
	 * 동기로 꼭 처리해야하는 상황(지금은 어떤지 잘 모르지만) 아니고서야 flatMap으로 처리하게하는 게 좋겠다.
	 * map으로는 Mono -> Flux, Flux -> Mono의 변환을 할 수 없다.
	 */

	@Test
	void reduceFluxToMonoTest(){
		AtomicInteger result = new AtomicInteger();
		Mono<Integer> integerMono = Flux.range(1,100).reduce(0, Integer::sum);
		integerMono.subscribe(result::set);

		assertThat(result.get()).isEqualTo(5050);
	}

	@Test
	void flatMapManyMonoToFluxTest(){
		Mono<Integer> integerMono = Mono.just(10);
		Flux<String> stringFlux = integerMono.flatMapMany(i ->{
			List<String> temp = new ArrayList<>();
			for (int j = 0; j < 10; j++) {
				temp.add(i * j + " to String");
			}
			return Flux.fromIterable(temp);
		});
		stringFlux.subscribe(System.out::println);
	}

	@Test
	void flatMapMonoToFluxFailedTest (){
		Mono<String> mono = Mono.just("Hello");
		/**
		 * 아래 코드는 flatMap으로 Flux를 반환하려면 Flux가 provided되어야하는데 Mono가 provided되었다는 오류가 발생합니다.
		 */
		// Flux<String> flux = mono.flatMap(value -> Flux.just(value + " World", value + " Universe"));
	}


}
