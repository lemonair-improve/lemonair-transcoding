package com.hanghae.lemonairtranscoding.exception;

import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public class ExpectedException extends RuntimeException {
	private final ErrorCode errorCode;
	public HttpStatusCode getStatus(){
		return errorCode.getStatus();
	}
	public ExpectedException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}