package com.hanghae.lemonairtranscoding.domain;

import java.io.File;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Ts extends UploadFile {

	public Ts(String fileName, File file) {
		super(fileName, file);
	}

	@Override
	public String getS3Key() {
		// name-timestamp.ts
		int lastHyphenIndex = this.fileName.lastIndexOf('-');
		return this.fileName.substring(0,lastHyphenIndex);
	}

}
