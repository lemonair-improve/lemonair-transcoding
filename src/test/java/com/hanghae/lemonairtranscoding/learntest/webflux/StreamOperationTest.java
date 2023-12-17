package com.hanghae.lemonairtranscoding.learntest.webflux;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.test.StepVerifier;

public class StreamOperationTest {

	public Flux<String> getStringFlux(int number) {
		return Flux.just(String.format("%d를 문자열로 변환하기", number));
	}

	@Test
	void applySrpInFlatMap() {
		//given
		// 대부분의 경우 flatMap은 유용하지만 아래와 같은 경우 flatMap 안에서 Flux Stream 내의 요소를
		// 직접 조작한 뒤 다른 reactive operation에 적용하고 있다.
		// 해당 flatMap에 책임이 2개임
		Flux<String> flatMapFlux = Flux.just(-1, 0, 1).flatMap(myNumber -> {
			// get newNumber
			int newNumber = myNumber * 3 - 1;
			return getStringFlux(newNumber);
		});

		// 개선된 코드
		Flux<String> mapFlux = Flux.just(-1, 0, 1).map(myNumber -> myNumber * 3 - 1).flatMap(this::getStringFlux);

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
	void useMapInsteadOfReturningMonoInFlatMap() {
		//given
		Flux<Integer> returnMonoInFlatMapFlux = Flux.just(-1, 0, 1).flatMap(myNumber -> Mono.just(myNumber + 1));

		Flux<Integer> mapFlux = Flux.just(-1, 0, 1).map(myNumber -> myNumber + 1);

		//when
		StepVerifier.create(returnMonoInFlatMapFlux).expectNext(0).expectNext(1).expectNext(2).verifyComplete();
		StepVerifier.create(mapFlux).expectNext(0).expectNext(1).expectNext(2).verifyComplete();
	}

	@Test
	void emptyPublisherWhenUsingFilter() {
		// given
		// 자신의 mock 객체를 만들어서 목객체의 비즈니스로직을 filter 이후에 둬서
		// filter가 emtpy publisher 인 경우 비즈니스로직이 수행되는지 테스트
		StreamOperationTest mockThis = Mockito.spy(new StreamOperationTest());
		Flux<String> emptyPublisherFlux = Flux.just(-1, 0, 1).filter(i -> i > 2).flatMap(mockThis::myBusinessLogic);
		// when
		List<String> results = new ArrayList<>();
		emptyPublisherFlux.subscribe(results::add);

		// then
		assert results.isEmpty();
		verify(mockThis, never()).myBusinessLogic(Mockito.anyInt());

	}

	private Flux<String> myBusinessLogic(int str) {
		return Flux.just(String.valueOf(str));
	}

	// 위처럼 filter를 사용하는 경우 Empty Publisher가 되었을 가능성이 항상 0이 될 순 없기 때문에,
	// 비즈니스로직이 수행되지 않는 상황을 피하려면 switchIfEmpty나 defaultIfEmpty를 사용해야함

	// switchIfEmpty의 경우, filter 등에 의해 여태까지의 publisher chain 이 empty publisher인 경우
	// swithchIfEmpty에서 리턴하는 요소가 추가된다.

	@Test
	void switchIfEmpty() {
		// given
		Flux<String> flux = Flux.just(-1, 0, 1)
			.filter(num -> num > 2)
			.flatMap(num -> Flux.just(String.valueOf(num)))
			.switchIfEmpty(Mono.just("switchIfEmpty"));
		// when then
		StepVerifier.create(flux).expectNext("switchIfEmpty").verifyComplete();
	}

	@Test
	void filterWhen() {
		// given

		// 안좋은 예
		// 깔끔해보이긴 하지만 중첩 구조의 flatMap 을 사용중이다. 코드에 문제가 생겼을 때 건들기에 난이도가 높음
		// 심지어 중첩된 flatMap에서는 해당 스트림에서 요소로 들어있는 name을 사용하는 것이 아니라 상위 스트림의 id를 사용중이다.
		// 오해하기 좋은 코드이다.
		Flux<String> badFlux = Flux.just(-1, 0, 1)
			.flatMap(id -> getName(id).filter(name -> !"john".equals(name)).flatMap(name -> reactiveOperation(id)));

		// 좋은 예, filterWhen -> map을 이용해서 복잡한 조건의 filter를 수행한 뒤 가공된 id로 flatMap을 수행하고 있음
		Flux<String> goodFlux = Flux.just(-1, 0, 1)
			.filterWhen(id -> getName(id).map(name -> !"john".equals(name)))
			.flatMap(this::reactiveOperation);

		// when then
		StepVerifier.create(badFlux).expectNext("-2").expectNext("0").verifyComplete();

		StepVerifier.create(goodFlux).expectNext("-2").expectNext("0").verifyComplete();
	}

	private Flux<String> reactiveOperation(Integer id) {
		return Flux.just(String.valueOf(id * 2));
	}

	private Flux<String> getName(int id) {
		if (id == 1)
			return Flux.just("john");
		return Flux.just("Alice");
	}

	/**
	 * 두 Mono 사이 종속성이 없는 경우 간단하게 Mono.zip()을 활용하여
	 * 두 Mono의 값으로 비즈니스 로직을 수행할 수 있다. Mono<Tuple<M1,M2>> 를 반환한다.
	 */
	@Test
	void zip() {
		// given
		Mono<String> loginMono = Mono.zip(getKey(), getPassword())
			.flatMap(tuple -> login(tuple.getT1(), tuple.getT2()));
		// when then
		StepVerifier.create(loginMono).expectNext("jwt토큰").verifyComplete();
	}

	/**
	 * 두 Mono 사이 종속성이 존쟇나느 경우 zipWhen()을 사용할 수 잇다.
	 * 직관적으로 두 Mono의 값을 가져오는 zip() 메서드와 달리 두 Mono간 Publisher 역할을 하는 Mono가 요소를 생산했을 때,
	 * publisher 역할의 Mono의 값으로 subscriber 역할의 Mono의 값을 생성하여 이후 Mono<Tuple<P,S>> 를 반환한다.
	 */
	@Test
	void zipWhen() {
		// given
		Mono<String> passwordVerifyMono =
			getPassword()
			.zipWhen(this::encryptPassword)
			.flatMap(tuple -> login(tuple.getT1(), tuple.getT2()));
		// when
		// then

		StepVerifier.create(passwordVerifyMono).expectNext("jwt토큰").verifyComplete();
	}

	/**
	 * zip() 이든 zipWhen()이든 사용하다보면, tuple을 완성하여 생성하고, 요청해서 비즈니스 로직에서 tuple.getTN() 을 이용해서 튜플을 사용할 때
	 * 조금 사용하기 곤란함을 느꼈을 것이다.
	 * TupleUtils.function() 을 사용하면 Tuple에서 요소들을 꺼내서 바로 사용할 수 있도록 해준다.
	 */

	@Test
	void tupleUtils_function(){
	    // given
	    Mono<String> loginMono = Mono.zip(getKey(), getPassword())
			.flatMap(TupleUtils.function(this::login));
	    // when
	    // then
		StepVerifier.create(loginMono).expectNext("jwt토큰").verifyComplete();
	}

	@Test
	void delayUntil(){
	    Flux.just(1,2,3,4,5).log().delayUntil(i -> Flux.just(i * 10)).log().subscribe();
		// log를 찍어보면 ConcatMapNoPrefetch 클래스가 onNext를 호출하며 이전에 요청받은 데이터를 처리하고나서 새로운 요소를 요청한다.
	}

	private Mono<String> encryptPassword(String password) {
		return Mono.just(password + "까꿍");
	}

	private Mono<String> login(String t1, String t2) {
		//어?쩌구 쿵쾅쿵쾅 로직
		return Mono.just("jwt토큰");
	}

	private Mono<String> getKey() {
		return Mono.just("sbl1998");
	}

	private Mono<String> getPassword() {
		return Mono.just("qwer1234");
	}



}
