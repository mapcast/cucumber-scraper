package com.cucumber.keiba.scrapper.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ListResult<T> {
	
	private int code;
	
	private String message;
	
	private List<T> list;
	
}
