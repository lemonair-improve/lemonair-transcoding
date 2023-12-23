package com.hanghae.lemonairtranscoding.learntest;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class StreamLearnTest {
	@Test
	void streamOf() {
		Stream.of(new int[] {1, 2, 3, 4}).map(String::valueOf).forEach(System.out::println);
	}
}
