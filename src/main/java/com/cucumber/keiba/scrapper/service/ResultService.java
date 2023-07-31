package com.cucumber.keiba.scrapper.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.cucumber.keiba.scrapper.dto.CommonResult;
import com.cucumber.keiba.scrapper.dto.ListResult;
import com.cucumber.keiba.scrapper.dto.PageResult;
import com.cucumber.keiba.scrapper.dto.SingleResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResultService {

	public CommonResult createCommonResult(int code, String message) {
		return new CommonResult(code, message);
	}
	
	public <T> SingleResult<T> createSingleResult(int code, String message, T object) {
		return new SingleResult<T>(code, message, object);
	}
	
	public <T> ListResult<T> createListResult(int code, String message, List<T> list) {
		return new ListResult<T>(code, message, list);
	}
	
	public <T> PageResult<T> createPageResult(int code, String message, Page<T> page) {
		return new PageResult<T>(code, message, page);
	}
	
}
