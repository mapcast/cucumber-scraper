package com.cucumber.keiba.scrapper.service.horse;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;
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
	
	public long getDocsCountByConditions(Map<String, Object> conditions) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		Document search = new Document();
		for(String key : conditions.keySet()) {
			search.append(key, conditions.get(key));
		}
		return collection.countDocuments(search);
	}
	
	public MongoCursor<Document> getRandom20HorsesByConditions(Map<String, Object> conditions) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("horse_datas");
		Document search = new Document();
		for(String key : conditions.keySet()) {
			search.append(key, conditions.get(key));
		}
		return collection.aggregate(Arrays.asList(
			Aggregates.match(search),
			Aggregates.sample(20)
		)).cursor();
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
			log.info("new horse data");
			InsertOneResult result = collection.insertOne(document);
			return result.wasAcknowledged();
		} else {
			log.info("replace horse data");
			return true;
		}
	}
	
	public boolean isBloodlineExist(String horseId) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("bloodline_datas");
		Document search = new Document();
		search.append("horse_id", horseId);
		Document document = collection.find(search).first();
		if(document == null) return false;
		else return true;
	}
	
	public boolean saveBloodLine(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("bloodline_datas");
		Document search = new Document();
		search.append("horse_id", document.get("horse_id"));
		if(collection.findOneAndReplace(search, document) == null) {
			log.info("new bloodline data");
			InsertOneResult result = collection.insertOne(document);
			return result.wasAcknowledged();
		} else {
			log.info("replace horse data");
			return true;
		}
	}
}
