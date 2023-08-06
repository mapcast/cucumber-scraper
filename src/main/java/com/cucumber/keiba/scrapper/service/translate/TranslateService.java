package com.cucumber.keiba.scrapper.service.translate;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.springframework.stereotype.Service;

import com.cucumber.keiba.scrapper.model.TranslateData;
import com.cucumber.keiba.scrapper.model.TranslateDataType;
import com.cucumber.keiba.scrapper.repository.TranslateDataRepository;
import com.google.common.base.Optional;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TranslateService {
	
	private final TranslateDataRepository translateDataRepository;
	
	private final MongoDatabase mongoDatabase;
	
	public String translate(TranslateDataType dataType, String original, boolean containSearch) {
		
		MongoCollection<Document> collection = mongoDatabase.getCollection("translate_datas");
		Document search = new Document();
		search.append("category", dataType);
		if(containSearch) {
			search.append("original", new Document("$regex", original.trim()).append("$options", "i"));
		} else {
			search.append("original", original.trim());
		}
		
		Document searched = collection.find(search).first();
		if(searched != null) {
			if(searched.getBoolean("translated_by_machine")) {
				StringBuffer result = new StringBuffer();
				result.append(searched.getString("translated"));
				result.append('(');
				result.append(searched.getString("original"));
				result.append(')');
				return result.toString();
			} else {
				return searched.getString("translated");
			}
		} else {
			return original;
		}
	}
	/*
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
	*/
	public String translateJapaneseOnly(TranslateDataType dataType, String original) {
		String japaneseCharactersRegex = "[\\u3040-\\u309F\\u30A0-\\u30FF]";

        // 입력 문자열에 일본어가 포함되어 있는지를 검사
        Pattern pattern = Pattern.compile(japaneseCharactersRegex);
        Matcher matcher = pattern.matcher(original);
        boolean isContainsJapanese = matcher.find();
        
        if(isContainsJapanese) {
        	return translate(dataType, original.replaceAll("[^\\u3040-\\u309F\\u30A0-\\u30FF]", ""), false);
        } else {
        	return original;
        }
	}
	
	public void moveToMongodb() {
		MongoCollection<Document> collection = mongoDatabase.getCollection("translate_datas");
		List<TranslateData> datas = translateDataRepository.findAll();
		for(TranslateData data : datas) {
			Document document = new Document();
			document.append("category", data.getType());
			document.append("original", data.getOriginal());
			document.append("translated", data.getTranslated());
			document.append("translated_by_machine", data.getIsTranslatedByMachine());
			collection.insertOne(document);
		}
	}
	
	public void insertTranslateData(TranslateDataType dataType, String original, String translated, boolean translatedByMachine) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("translate_datas");
		
		Document search = new Document();
		search.append("category", dataType);
		search.append("original", original);
		
		Document searched = collection.find(search).first();
		if(searched == null) {
			Document document = new Document();
			document.append("category", dataType);
			document.append("original", original);
			document.append("translated", translated);
			document.append("translated_by_machine", translatedByMachine);
			collection.insertOne(document);
		}
	}
	
	public void deleteTranslateDatas(TranslateDataType dataType) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("translate_datas");
		Document search = new Document();
		search.append("category", dataType);
		collection.deleteMany(search);
	}
}
