package com.hanghae.lemonairtranscoding.learntest.webflux;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class AvoidFlatMapTest {

	public Flux<String> getStringFlux(int number) {
		return Flux.just(String.format("%d를 문자열로 변환하기", number));
	}

	// private ArrayList<?>[][] results;
	// private ArrayList<String>[] stringResults;
	// private ArrayList<Integer>[] integerResults;
	//
	// private final int NUM_TYPE = 2;
	// private final int NUM_STREAMS = 2;
	// @BeforeEach
	// void beforeEach(){
	// 	// stringResults = new ArrayList[NUM_STREAMS];
	// 	// for (int i = 0; i < 2; i++) {
	// 	// 	stringResults[i] = new ArrayList<>();
	// 	// }
	// 	results = new ArrayList[NUM_TYPE][NUM_STREAMS];
	// 	for (int i = 0; i < NUM_TYPE; i++) {
	// 		ArrayList<?>[] tempResults = new ArrayList[NUM_STREAMS];
	// 		for (int j = 0; j < NUM_STREAMS; j++) {
	// 			tempResults[i] = new ArrayList<>();
	// 		}
	// 		results[i] = tempResults;
	// 	}
	// }
	//
	// @AfterEach
	// void afterEach(){
	// 	stringResults = new ArrayList[2];
	// 	for (int i = 0; i < 2; i++) {
	// 		stringResults[i] = new ArrayList<>();
	// 	}
	// }

	@Test
	void applySrpInFlatMap() {
		//given
		// 대부분의 경우 flatMap은 유용하지만 아래와 같은 경우 flatMap 안에서 Flux Stream 내의 요소를
		// 직접 조작한 뒤 다른 reactive operation에 적용하고 있다.
		// 해당 flatMap에 책임이 2개임
		Flux<String> flatMapFlux = Flux.just(-1, 0, 1)
			.flatMap(myNumber -> {
				// get newNumber
				int newNumber = myNumber * 3 - 1;
				return getStringFlux(newNumber);
			});

		// 개선된 코드
		Flux<String> mapFlux = Flux.just(-1, 0, 1)
			.map(myNumber -> myNumber * 3 - 1)
			.flatMap(this::getStringFlux);

		//when

		StepVerifier.create(flatMapFlux)
			.expectNext("-4를 문자열로 변환하기")
			.expectNext("-1를 문자열로 변환하기")
			.expectNext("2를 문자열로 변환하기")
			.verifyComplete();
		StepVerifier.create(mapFlux)
			.expectNext("-4를 문자열로 변환하기")
			.expectNext("-1를 문자열로 변환하기")
			.expectNext("2를 문자열로 변환하기")
			.verifyComplete();
	}

	@Test
	void useMapRatherThanReturningMonoInFlatMap() {
		//given
		Flux<Integer> returnMonoInFlatMapFlux = Flux.just(-1, 0, 1)
			.flatMap(myNumber -> Mono.just(myNumber + 1));

		Flux<Integer> mapFlux = Flux.just(-1, 0, 1)
			.map(myNumber -> myNumber + 1);

		//when
		StepVerifier.create(returnMonoInFlatMapFlux)
			.expectNext(0)
			.expectNext(1)
			.expectNext(2)
			.verifyComplete();
		StepVerifier.create(mapFlux)
			.expectNext(0)
			.expectNext(1)
			.expectNext(2)
			.verifyComplete();
	}

}
