package com.hanghae.lemonairtranscoding.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ErrorCode {
	TranscodingProcessNotStarted(HttpStatus.INTERNAL_SERVER_ERROR, "T-A001", "영상 변환 작업이 정상적으로 시작되지 못했습니다."),
	CannotCreateStreamerDirectory(HttpStatus.INTERNAL_SERVER_ERROR, "T-A002", "영상 변환 작업이 정상적으로 시작되지 못했습니다.");

	private final HttpStatusCode status;
	private final String code;
	private final String message;
}