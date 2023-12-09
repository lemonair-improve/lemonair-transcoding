package com.hanghae.lemonairtranscoding.domain;

import java.io.File;

import lombok.Getter;

@Getter
public abstract class UploadFile {
	protected String fileName;
	protected File uploadFile;
	protected String userName;

	public UploadFile(String fileName, File file) {
		this.fileName = fileName;
		this.uploadFile = file;
	}

	public abstract String getS3Key();

}
