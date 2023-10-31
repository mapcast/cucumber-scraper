package com.cucumber.keiba.scrapper.service.race;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

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
	
	public MongoCursor<Document> getDocsByDocumentQuery(Document query) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		return collection.find(query).cursor();
	}
	
	public MongoCursor<Document> get20RequestRaces(Document query) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		return collection.aggregate(Arrays.asList(
			Aggregates.match(query),
			Aggregates.limit(20)
		)).cursor();
	}
	
	public Document getFirstDocByDocumentQuery(Document query) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		return collection.find(query).first();
	}
	
	public boolean saveDocs(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("race_datas");
		Document search = new Document();
		search.append("original_id", document.get("original_id"));
		if(collection.findOneAndReplace(search, document) == null) {
			log.info("new race data");
			InsertOneResult result = collection.insertOne(document);
			return result.wasAcknowledged();
		} else {
			log.info("replace race data");
			return true;
		}
	}
	
	public boolean saveSchedule(Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection("schedule_datas");
		Document search = new Document();
		search.append("order", document.get("order"));
		if(collection.findOneAndReplace(search, document) == null) {
			log.info("new schedule data");
			InsertOneResult result = collection.insertOne(document);
			return result.wasAcknowledged();
		} else {
			log.info("replace race data");
			return true;
		}
	}
}
