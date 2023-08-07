package com.cucumber.keiba.scrapper.service.horse;

import java.util.Map;

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
	
	public boolean isIdExist(String originalId) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		Document search = new Document();
		search.append("original_id", originalId);
		if(collection.find(search).first() != null) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean insertDocs(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		InsertOneResult result = collection.insertOne(document);
		return result.wasAcknowledged();
	}
}
