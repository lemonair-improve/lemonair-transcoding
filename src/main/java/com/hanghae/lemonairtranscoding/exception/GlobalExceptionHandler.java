package com.hanghae.lemonairtranscoding.exception;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(ExpectedException.class)
	protected Mono<ResponseEntity<String>> handleExpectedException(final ExpectedException exception) {
		log.info(exception.getMessage());
		return Mono.just(ResponseEntity.status(exception.getStatus()).body(exception.getMessage()));
	}
}