package com.hanghae.lemonairtranscoding.learntest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

public class PubSubTest {

	@Test
	void notUsePubSub(){
		Flux.range(1, 3)
			.map(i -> {
				System.out.println("1st map(): " + Thread.currentThread().getName() + " " + i);
				return i;
			})
			.map(i -> {
				System.out.println("2nd map(): " + Thread.currentThread().getName() + " " + i);
				return i;
			})
			.subscribe(i -> {
				System.out.println("subscribe():" + Thread.currentThread().getName() + " " + i);
			});
	}

	@Test
	void stream(){
		String toString = "문자열로 매핑하기";
		Stream.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22)
			.filter(i-> i%2==0)
			.map(i-> toString+i )
			.filter(s -> s.length() == toString.length() + 2)
			.sorted((s1,s2) ->s2.compareTo(s1))
			.forEach(System.out::println);
	}
}
