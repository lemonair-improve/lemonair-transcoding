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

	@GetMapping("/{streamerId}")
	Mono<Long> startTransCoding(@PathVariable("streamerId") String userId) {
		return transcodeService.startTranscoding(userId);
	}

	@GetMapping("/offair/{streamerId}")
	Mono<Boolean> endBroadcast(@PathVariable String streamerId) {
		return transcodeService.endBroadcast(streamerId);
	}

}

