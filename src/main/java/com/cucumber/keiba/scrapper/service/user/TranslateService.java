package com.cucumber.keiba.scrapper.service.user;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.cucumber.keiba.scrapper.model.TranslateData;
import com.cucumber.keiba.scrapper.model.TranslateDataType;
import com.cucumber.keiba.scrapper.repository.TranslateDataRepository;
import com.google.common.base.Optional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TranslateService {
	
	private final TranslateDataRepository translateDataRepository;
	
	public String translate(TranslateDataType dataType, String original) {
		Optional<TranslateData> wrapped = translateDataRepository.findByTypeAndOriginal(dataType, original);
		if(wrapped.isPresent()) {
			TranslateData translateData = wrapped.get();
			if(translateData.getIsTranslatedByMachine()) {
				StringBuffer result = new StringBuffer();
				result.append(translateData.getTranslated());
				result.append('(');
				result.append(translateData.getOriginal());
				result.append(')');
				return result.toString();
			} else {
				return translateData.getTranslated();
			}
		} else {
			return original;
		}
	}
	
	public String translateContains(TranslateDataType dataType, String original) {
		List<TranslateData> searchList = translateDataRepository.findByTypeAndOriginalContaining(dataType, original);
		if(searchList.size() > 0) {
			TranslateData translateData = searchList.get(0);
			if(translateData.getIsTranslatedByMachine()) {
				StringBuffer result = new StringBuffer();
				result.append(translateData.getTranslated());
				result.append('(');
				result.append(translateData.getOriginal());
				result.append(')');
				return result.toString();
			} else {
				return translateData.getTranslated();
			}
		} else {
			return original;
		}
	}
	
	public String translateJapaneseOnly(TranslateDataType dataType, String original) {
		String japaneseCharactersRegex = "[\\u3040-\\u309F\\u30A0-\\u30FF]";

        // 입력 문자열에 일본어가 포함되어 있는지를 검사
        Pattern pattern = Pattern.compile(japaneseCharactersRegex);
        Matcher matcher = pattern.matcher(original);
        boolean isContainsJapanese = matcher.find();
        
        if(isContainsJapanese) {
        	return translate(dataType, original.replaceAll("[^\\u3040-\\u309F\\u30A0-\\u30FF]", ""));
        } else {
        	return original;
        }
	}
}
