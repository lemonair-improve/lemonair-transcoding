package com.hanghae.lemonairtranscoding.domain;

import java.io.File;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Jpg extends UploadFile {
	public Jpg(String fileName, File uploadFile) {
		super(fileName, uploadFile);
	}

	// lyulbyung-thumbnail-0001.jpg
	@Override
	public String getS3Key() {
		int secondHyphenIndex = this.fileName.lastIndexOf('-');
		int firstHyphenIndex = this.fileName.lastIndexOf('-', secondHyphenIndex - 1);

		return this.fileName.substring(0,firstHyphenIndex)
			+ "/" + this.fileName.substring(firstHyphenIndex + 1, secondHyphenIndex)
			+ "/" + this.fileName;
	}
}
