package com.hanghae.lemonairtranscoding.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanghae.lemonairtranscoding.service.TranscodeService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transcode")
@RequiredArgsConstructor
public class TranscodeController {
	private final TranscodeService transcodeService;

	@GetMapping("/{owner}")
	Mono<Long> startTransCoding(@PathVariable("owner") String userId){

		// TODO: 2023-12-05 현재는 obs studio가 보낸 요청으로부터 사용자 정보의 수정 없이 전달되므로
		//  email 자체가 전송된다. @ 이전 부분만 사용
		return transcodeService.startTranscoding(userId);
	}

	@GetMapping("/offair/{owner}")
	Mono<Boolean> endBroadcast(@PathVariable String owner) {
		return transcodeService.endBroadcast(owner);
	}
}
