package com.cucumber.keiba.scrapper.service.horse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertOneResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@Service
@RequiredArgsConstructor
public class HorseService {
	private final MongoDatabase mongoDatabase;
	
	public MongoCursor<Document> getDocsByConditions(Map<String, Object> conditions) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		Document search = new Document();
		for(String key : conditions.keySet()) {
			search.append(key, conditions.get(key));
		}
		return collection.find(search).cursor();
	}
	
	public MongoCursor<Document> getDocsByBsonFilter(Bson bson) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		return collection.find(bson).cursor();
	}
	
	public Optional<Document> findByOriginalId(String originalId) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		Document search = new Document();
		search.append("original_id", originalId);
		Document searched = collection.find(search).first();
		if(collection.find(search).first() != null) {
			return Optional.of(searched);
		} else {
			return Optional.empty();
		}
	}
	
	public boolean saveDocs(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		Document search = new Document();
		search.append("original_id", document.get("original_id"));
		if(collection.findOneAndReplace(search, document) == null) {
			log.info("new event");
			InsertOneResult result = collection.insertOne(document);
			return result.wasAcknowledged();
		} else {
			log.info("replace event");
			return true;
		}
	}
}
