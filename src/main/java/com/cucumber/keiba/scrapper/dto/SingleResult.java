package com.cucumber.keiba.scrapper.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SingleResult<T> {
	
	private int code;
	
	private String message;
	
	private T result;
	
}
