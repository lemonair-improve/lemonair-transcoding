package com.hanghae.lemonairtranscoding.learntest.webflux;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.test.StepVerifier;

public class MonoFlux_Basic {

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
		Flux<Integer> fluxInt = Flux.range(1, 10).map(integer -> {
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

		Flux.range(1, 10).subscribe(System.out::println).dispose();

		Flux.range(1, 10).subscribe(System.out::println);

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
	void test() {
		//This is for synchronous and one-by-one emissions, meaning that the sink is a SynchronousSink
		// and that its next() method can only be called at most once per callback invocation.
		// You can then additionally call error(Throwable) or complete(), but this is optional.
		Flux<String> flux = Flux.generate(() -> 0, (state, sink) -> {
			sink.next("3 x " + state + " = " + 3 * state);
			if (state == 10)
				sink.complete();
			return state + 1;
		});
		flux.subscribe(System.out::println);
	}

	@Test
	void mapAndSubscribeTwiceTest() {
		Flux<Integer> squared = Flux.range(1, 100).map(x -> x * x);
		squared.subscribe(x -> System.out.print(x + " "));
		System.out.println();
		squared.map(x -> x / 10).subscribe(x -> System.out.print(x + " "));
		System.out.println("두 번 이상 subscribe할 수도 있다.");
	}

	//map과 flatMap은 둘 다 스트림의 중간에 값을 변환해주는 역할을 한다.
	// map은 1 : 1로 반환을 보증하고 flatMap은 1 : N을 변환할 수 있다.
	// 요청에 대해 N개를 병렬로 실행할 경우가 많지 않아 map을 많이 쓸 것 같지만 개발을 하다 보면 대다수의 경우 flatMap을 사용하게 된다.
	@Test
	void monoMapTest() {
		/**
		 * 아래 코드는 map 의 리턴 타입이 원래의 Mono<T> 타입과 동일하기 때문에 에러가 발생합니다.
		 */
		// Mono<Integer> intMono = Mono.just(1)
		// 	.map( x-> (float)x * 10)
		// 	.subscribe(System.out::println);

		Mono<Integer> intMono = Mono.just(1).map(x -> x * 10);
		intMono.subscribe(System.out::println);
	}

	@Test
	void intMonoMapToDoubleMonoTest() {
		Mono<Double> doubleMono = Mono.fromSupplier(() -> 1).map(Integer::doubleValue);
		doubleMono.subscribe(System.out::println);
	}

	@Test
	void intMonoMapToStringMonoTest() {
		Mono<String> stringMono = Mono.fromSupplier(() -> 1).map(String::valueOf);
		stringMono.subscribe(x -> System.out.println(x instanceof String));
	}

	@Test
	void intMonoFlatMapToStringMonoTest() {
		Mono<String> stringMono = Mono.fromSupplier(() -> -123).flatMap(x -> Mono.just("toString :" + x));
		stringMono.subscribe(System.out::println);
	}

	/**
	 * Mono 와 Flux의 map, flatMap 메서드의 차이
	 * 1. map은 동기 처리, flatMap은 비동기 처리,
	 * 동기로 꼭 처리해야하는 상황(지금은 어떤지 잘 모르지만) 아니고서야 flatMap으로 처리하게하는 게 좋겠다.
	 * map으로는 Mono -> Flux, Flux -> Mono의 변환을 할 수 없다.
	 */

	@Test
	void reduceFluxToMonoTest() {
		AtomicInteger result = new AtomicInteger();
		Mono<Integer> integerMono = Flux.range(1, 100).reduce(0, Integer::sum);
		integerMono.subscribe(result::set);

		assertThat(result.get()).isEqualTo(5050);
	}

	@Test
	void flatMapManyMonoToFluxTest() {
		Mono<Integer> integerMono = Mono.just(10);
		Flux<String> stringFlux = integerMono.flatMapMany(i -> {
			List<String> temp = new ArrayList<>();
			for (int j = 0; j < 10; j++) {
				temp.add(i * j + " to String");
			}
			return Flux.fromIterable(temp);
		});
		stringFlux.subscribe(System.out::println);
	}

	@Test
	void flatMapMonoToFluxFailedTest() {
		Mono<String> mono = Mono.just("Hello");
		/**
		 * 아래 코드는 flatMap으로 Flux를 반환하려면 Flux가 provided되어야하는데 Mono가 provided되었다는 오류가 발생합니다.
		 */
		// Flux<String> flux = mono.flatMap(value -> Flux.just(value + " World", value + " Universe"));
	}

	@Test
	void fluxGenerateMethodTest() {
		// 초기 state 97, 이 state와 SynchronousSink를 가지고 BiFunction의 apply 메소드에 쓰여야 할 내용을 람다식으로 작성하여 BiFunction을 Flux.generate함수에 전달한다.
		// 즉 Flux.generate( 초기값 반환 함수, 초기값을 가지고 계속해서 생성할 규칙을 정의한 함수)
		Flux<Character> flux = Flux.generate(() -> 97, (state, sink) -> {
			char value = (char)state.intValue();
			sink.next(value);
			if (value == 'z') {
				sink.complete();
			}
			return state + 1;
		}, (state) -> {
			// stateConsumer를 전달하는 부분, 아래는 Consumer<? extends state의 형식> 형식의 stateConsumer가 accept되었을때, 즉 subscriber가 Flux를 사용햇을 때 일어날 일을 정의하고잇다.
			// 아래 생성 끝 이라는 문장은 subscriber가 이 Flux를 사용한 경우 출력되게된다.
			System.out.println("생성 끝");
		});

		List<Character> result = new ArrayList<>();
		flux.subscribe(result::add);
		flux.subscribe(System.out::println);

		flux.subscribe((character -> {
			/**
			 * donothing
			 */
		}));

		for (int i = 0; i < 26; i++) {
			assertThat(result.get(i)).isEqualTo((char)('a' + i));
		}
	}

	@Test
	void fluxGenerateMethodWithExplicitBiFunctionTest() {

		// 현재 상태 Integer, SynchronousSink의 타입 Character는 emit되는 값의 타입, 뒤에 있는 Integer는 apply 메서드의 반환 타입이다.
		// BiFunction의 apply는 apply된 결과에 apply함수를 실행한 결과를 반환하고, 더이상 apply되지 않아야 할 때 sink.complete함수를 호출하여 더이상 apply가 진행되지 않고
		// 여태까지 생성된 것을 Flux<Character> 로 반환한다.
		BiFunction<Integer, SynchronousSink<Character>, Integer> biFunction = new BiFunction<Integer, SynchronousSink<Character>, Integer>() {
			@Override
			public Integer apply(Integer state, SynchronousSink<Character> characterSynchronousSink) {
				char value = (char)state.intValue();
				characterSynchronousSink.next(value);
				if (value == 'z')
					characterSynchronousSink.complete();
				return value + 1;
			}
		};
		// 첫번째 인자로 callable 함수 로 생성기의 초기 상태를 정의한다. 이 경우 값이 97
		Consumer<Integer> stateConsumer = new Consumer<Integer>() {
			@Override
			public void accept(Integer integer) {
				System.out.println("accepted when state is " + integer);
			}
		};
		Flux<Character> flux = Flux.generate(() -> 97, biFunction, stateConsumer);

		List<Character> result = new ArrayList<>();
		flux.subscribe(result::add);
		flux.subscribe(System.out::println);

		for (int i = 0; i < 26; i++) {
			assertThat(result.get(i)).isEqualTo((char)('a' + i));
		}
	}

	@Test
	public void whenGeneratingCharacters_thenCharactersAreProduced() {
		// getGenerated_atozFlux()는 Flux.generate 함수를 사용하여 동기 방식으로 a부터 z 를 Flux에
		Flux<Character> characterFlux = Flux.generate(() -> 97, (state, sink) -> {
			char value = (char)state.intValue();
			sink.next(value);
			if (value == 'z') {
				sink.complete();
			}
			return state + 1;
		}).take(3).cast(Character.class);

		System.out.println("flux take는 구독자의 입장에서 n개의 항목만 요청하기위해서 사용한다.");
		StepVerifier.create(characterFlux).expectNext('a', 'b', 'c').expectComplete().verify();

	}

	@Test
	void whenCreatingCharactersWithMultipleThreads_thenSequenceIsProducedAsynchronously() {

		Flux<Character> characterFlux = Flux.generate(() -> 97, (state, sink) -> {
			char value = (char)state.intValue();
			sink.next(value);
			if (value == 'z') {
				sink.complete();
			}
			return state + 1;
		});
		List<Character> sequence1 = characterFlux.take(3).collect(Collectors.toList()).block();
		List<Character> sequence2 = characterFlux.take(2).collect(Collectors.toList()).block();
		assert sequence1 != null;
		sequence1.stream().forEach(System.out::println);
		assert sequence2 != null;
		sequence2.stream().forEach(System.out::println);
	}

	@Test
	void whenCreatingCharactersWithMultipleThreads_thenSequenceIsProducedAsynchronously2() throws InterruptedException {

		List<Character> sequence1 = new ArrayList<>();
		List<Character> sequence2 = new ArrayList<>();
		sequence1.add('a');
		sequence1.add('b');
		sequence1.add('c');
		sequence2.add('a');
		sequence2.add('b');

		// {a,b,c} 와 {a,b} 리스트에 대해서 sequence1, sequence2를 각각 accept하여 리스트 각각의 요소를 Flux로 생성하는 Runnable 객체를
		// 각 두개의 Thread에 전달한다.
		CharacterCreator characterCreator = new CharacterCreator();
		Thread producerThread1 = new Thread(() -> characterCreator.consumer.accept(sequence1));
		Thread producerThread2 = new Thread(() -> characterCreator.consumer.accept(sequence2));

		List<Character> consolidated = new ArrayList<>();

		//아래 코드가 실행되기 전 까지는 CharacterCreator의 consumer가 null 인 상황 왜?
		// consumer가 accept하긴 했지만 해당 consumer가 사용되는 Flux가 아무것도 subscribe하지 않아서??

		characterCreator.createCharacterSequence().subscribe(consolidated::add);

		producerThread1.start();
		producerThread2.start();
		// 현재 이 테스트를 수행하고 있는 쓰레드가 생산자 쓰레드 2개가 작업을 마치기 전 까지 대기
		producerThread1.join();
		producerThread2.join();

		consolidated.stream().forEach(System.out::println);
		assertThat(consolidated).containsExactlyInAnyOrder('a', 'b', 'c', 'a', 'b');
	}

	/**
	 * doAfterTerminate() 메서드는 Operation 수행 성공/실패와 관계없이 종료 이벤트(onComplete(), onError()) 발생시 수행할 부분ㄴ
	 */

	@Test
	void doAfterTerminate() {
		AtomicBoolean isTerminated = new AtomicBoolean(false);
		// given
		Flux<Object> flux = Flux.generate(() -> 1, (state, sink) -> {
			sink.next(state);
			if (state == 10) {
				sink.complete();
			}
			return state + 1;
		}).filter(i -> (int)i > 20).doOnTerminate(() -> isTerminated.set(true));

		// when
		assert !isTerminated.get(); // 현재는 isTerminated가 false
		StepVerifier.create(flux).verifyComplete(); // flux를 subscribe해도 filter때문에 아무 값을 가지지 않는다.

		// then
		assert isTerminated.get(); // 하지만 doOnTerminate는 실행되었음

	}

	public static class CharacterCreator {
		public Consumer<List<Character>> consumer;

		public Flux<Character> createCharacterSequence() {
			return Flux.create(sink -> CharacterCreator.this.consumer = items -> items.forEach(sink::next));
		}
	}
}
