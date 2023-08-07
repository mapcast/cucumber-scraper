package com.cucumber.keiba.scrapper.service.race;

import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertOneResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RaceService {
	
	private final MongoDatabase mongoDatabase;
	
	public Optional<Document> getByOriginalId(String originalId) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		Document search = new Document();
		search.append("original_id", originalId);
		Document result = collection.find(search).first();
		if(result != null) {
			return Optional.of(result);
		} else {
			return Optional.empty();
		}
	}
	
	public MongoCursor<Document> getDocsByConditions(Map<String, Object> conditions) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		Document search = new Document();
		for(String key : conditions.keySet()) {
			search.append(key, conditions.get(key));
		}
		return collection.find(search).cursor();
	}
	
	public MongoCursor<Document> getDocsByBsonFilter(Bson bson) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		return collection.find(bson).cursor();
	}
	
	public boolean insertDocs(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		InsertOneResult result = collection.insertOne(document);
		return result.wasAcknowledged();
	}
}
