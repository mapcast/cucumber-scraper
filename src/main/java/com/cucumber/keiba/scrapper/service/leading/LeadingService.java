package com.cucumber.keiba.scrapper.service.leading;

import org.bson.Document;
import org.springframework.stereotype.Service;

import com.cucumber.keiba.scrapper.repository.TranslateDataRepository;
import com.cucumber.keiba.scrapper.service.translate.TranslateService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertOneResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@Service
@RequiredArgsConstructor
public class LeadingService {

	private final MongoDatabase mongoDatabase;
	
	public boolean saveDocs(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("leading_datas");
		Document search = new Document();
		search.append("category", document.get("category"));
		search.append("order", document.get("order"));
		if(collection.findOneAndReplace(search, document) == null) {
			log.info("new leading data");
			InsertOneResult result = collection.insertOne(document);
			return result.wasAcknowledged();
		} else {
			log.info("replace leading data");
			return true;
		}
	}
}
