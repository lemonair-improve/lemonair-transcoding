package com.hanghae.lemonairtranscoding.learntest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class RegexLearnTest {

	@Test
	void test1() {
		String[] targets = new String[] {"sbl-123.txt", "sbl.mp3"};
		String regex = "[a-zA-Z]+";
		Pattern pattern = Pattern.compile(regex);

		for (int i = 0; i < 2; i++) {
			Matcher matcher = pattern.matcher(targets[i]);
			if (matcher.find()) {
				System.out.println(matcher.group());
			}
		}
	}
}
