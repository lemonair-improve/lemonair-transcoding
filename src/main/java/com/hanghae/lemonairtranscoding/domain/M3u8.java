package com.hanghae.lemonairtranscoding.domain;

import java.io.File;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class M3u8 extends UploadFile {
	public M3u8(String fileName, File file) {
		super(fileName, file);
	}

	@Override
	public String getS3Key() {
		int dotIndex = this.fileName.lastIndexOf('.');
		return this.fileName.substring(0,dotIndex);
	}
}
