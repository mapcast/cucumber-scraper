package com.cucumber.keiba.scrapper.util;

import org.bson.Document;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

@Component
public class DocumentUtil {
	
	public Document appendIfNotNull(Document document, JsonNode jsonNode, String name) {
		if(jsonNode != null && !jsonNode.isNull()) {
			replaceOrAddElement(document, name, Document.parse(jsonNode.toString()));
			return document;
		} else {
			return document;
		}
	}
	
	public Document appendIfNotNullString(Document document, JsonNode jsonNode, String name) {
		if(jsonNode != null && !jsonNode.isNull()) {
			replaceOrAddElement(document, name, Document.parse(jsonNode.toString()));
			return document;
		} else {
			return document;
		}
	}
	
	public Document replaceOrAddElement(Document document, String key, Object value) {
        if (document.containsKey(key)) {
            document.replace(key, value);
        } else {
            document.append(key, value);
        }
        return document;
    }
	
}
