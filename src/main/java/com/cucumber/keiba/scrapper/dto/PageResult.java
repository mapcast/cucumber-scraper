package com.cucumber.keiba.scrapper.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PageResult<T> {
	private int code;
	
	private String message;
	
	private Page<T> page;
}
