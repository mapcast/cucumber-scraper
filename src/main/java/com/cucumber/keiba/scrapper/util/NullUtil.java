package com.cucumber.keiba.scrapper.util;

import org.bson.Document;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

@Component
public class NullUtil {
	
	public Document appendIfNotNull(Document document, JsonNode jsonNode, String name) {
		if(jsonNode != null && !jsonNode.isNull()) {
			if(document.containsKey(name)) {
				document.replace(name, Document.parse(jsonNode.toString()));
			} else {
				document.append(name, Document.parse(jsonNode.toString()));
			}
			return document;
		} else {
			return document;
		}
	}
	
	public Document appendIfNotNullString(Document document, JsonNode jsonNode, String name) {
		if(jsonNode != null && !jsonNode.isNull()) {
			if(document.containsKey(name)) {
				document.replace(name, jsonNode.textValue());
			} else {
				document.append(name, jsonNode.textValue());
			}
			
			return document;
		} else {
			return document;
		}
	}
	
}
