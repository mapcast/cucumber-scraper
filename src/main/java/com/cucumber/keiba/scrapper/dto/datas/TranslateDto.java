package com.cucumber.keiba.scrapper.dto.datas;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TranslateDto {
	private String source;
	private String target;
	private String text;
}
