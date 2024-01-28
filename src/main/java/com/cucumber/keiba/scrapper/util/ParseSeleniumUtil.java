package com.cucumber.keiba.scrapper.util;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.cucumber.keiba.scrapper.dto.datas.TranslateDto;
import com.cucumber.keiba.scrapper.enums.RaceGrade;
import com.cucumber.keiba.scrapper.enums.RaceHost;
import com.cucumber.keiba.scrapper.enums.TranslateDataType;
import com.cucumber.keiba.scrapper.service.horse.HorseService;
import com.cucumber.keiba.scrapper.service.race.RaceService;
import com.cucumber.keiba.scrapper.service.translate.TranslateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseSeleniumUtil {
	
	@Value("${translate.client-id}")
	private String translateClientId;
	
	@Value("${translate.client-secret}")
	private String translateClientSecret;
	
	private final DocumentUtil documentUtil;
	
	public void parseUpcomingRaces(RaceService raceService, HorseService horseService, TranslateService translateService, LocalDate now, List<String> raceLinks, WebDriver driver) throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		RestTemplate restTemplate = new RestTemplate();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Naver-Client-Id", translateClientId);
        headers.set("X-Naver-Client-Secret", translateClientSecret);
        
        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
		
		
		for(String raceLink : raceLinks) {
        	Document document = new Document();
        	driver.navigate().to(raceLink);
        	String raceOriginalId = new URL(raceLink).getQuery().split("&")[0].replace("race_id=", "");
        	Optional<Document> wrappedDocs = raceService.getByOriginalId(raceOriginalId);
        	if(wrappedDocs.isPresent()) {
        		document = wrappedDocs.get();
        	} else {
        		document.append("original_id", raceOriginalId);
        		document.append("race_id", UUID.randomUUID().toString());
        	}
        	
        	String roundString = driver.findElement(By.cssSelector(".RaceNum")).getText();
        	document = documentUtil.replaceOrAddElement(document, "round", documentUtil.convertToInteger(roundString));
        	
        	
        	//레이스 정보 스크래핑
        	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
        	document = documentUtil.replaceOrAddElement(document, "name", 
        			translateService.translate(TranslateDataType.RACE, raceNameAndGrade.getText()
					.replace("以上", "이상 ")
        			.replace("障害", "장애물 ")
					.replace("歳", "세 ")
					.replace("新馬", "신마전")
					.replace("未勝利", "미승리전")
					.replace("オープン", "오픈")
					.replace("勝クラス", "승 클래스")
					.replace("万下", "만엔 이하"), false));
        	
        	
        	if(raceNameAndGrade.findElements(By.cssSelector("span")).size() > 0) {
        		String gradeIconClasses = raceNameAndGrade.findElement(By.cssSelector("span")).getAttribute("class");
        		if(gradeIconClasses.contains("Icon_GradeType18")) {
        			document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.ONE);
        			document.append("grade_number", 2);
            	} else if(gradeIconClasses.contains("Icon_GradeType17")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.TWO);
            		document.append("grade_number", 3);
            	} else if(gradeIconClasses.contains("Icon_GradeType16")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.THREE);
            		document.append("grade_number", 4);
            	} else if(gradeIconClasses.contains("Icon_GradeType15")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.L);
            		document.append("grade_number", 5);
            	} else if(gradeIconClasses.contains("Icon_GradeType5")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.OP);
            		document.append("grade_number", 6);
            	} else if(gradeIconClasses.contains("Icon_GradeType3") || gradeIconClasses.contains("Icon_GradeType12")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G3);
            		document.append("grade_number", 7);
            	} else if(gradeIconClasses.contains("Icon_GradeType2") || gradeIconClasses.contains("Icon_GradeType11")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G2);
            		document.append("grade_number", 8);
            	} else if(gradeIconClasses.contains("Icon_GradeType1") || gradeIconClasses.contains("Icon_GradeType10")) {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G1);
            		document.append("grade_number", 9);
            	} else {
            		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.NONE);
            		document.append("grade_number", 1);
            	}
        	} else {
        		if(document.getString("name").contains("1승 클래스")) {
        			document.append("grade", RaceGrade.ONE);
            		document.append("grade_number", 2);
        		} else if(document.getString("name").contains("2승 클래스")) {
        			document.append("grade", RaceGrade.TWO);
            		document.append("grade_number", 3);
        		} else if(document.getString("name").contains("3승 클래스")) {
        			document.append("grade", RaceGrade.THREE);
            		document.append("grade_number", 4);
        		} else {
        			document.append("grade", RaceGrade.NONE);
            		document.append("grade_number", 1);
        		}
        	}
        	
        	WebElement raceDataLine1 = driver.findElement(By.cssSelector(".RaceData01"));
        	//String startTime = raceDataLine1.getText();
        	String[] times = raceDataLine1.getText().substring(0, raceDataLine1.getText().indexOf("発走")).split(":");
        	if(times.length == 2) {
        		LocalDateTime startTime = LocalDateTime.now()
        			.withYear(now.getYear())
        			.withMonth(now.getMonthValue())
        			.withDayOfMonth(now.getDayOfMonth())
    				.withHour(Integer.parseInt(times[0]))
    				.withMinute(Integer.parseInt(times[1]))
    				.withSecond(0)
    				.withNano(0);
        		document = documentUtil.replaceOrAddElement(document, "start_time", startTime);
        	} else {
        		log.info("경기 시작시간 parsing에 실패했습니다.");
        		continue;
        	}
        	
        	String raceSpecs = raceDataLine1.findElement(By.cssSelector("span")).getText();
        	document = documentUtil.replaceOrAddElement(document, "track", 
        			translateService.translate(TranslateDataType.TRACK, raceSpecs.replaceAll("[0-9]", "").replace("m", ""), false));
        	document = documentUtil.replaceOrAddElement(document, "distance", documentUtil.convertToInteger(raceSpecs.replaceAll("[^0-9]", "")));
        	if(document.getString("track").equals("잔디")) document.append("track_number", 1);
        	else if(document.getString("track").equals("더트")) document.append("track_number", 2);
        	else if(document.getString("track").equals("장애물")) document.append("track_number", 3);
        	
        	List<WebElement> raceDataLine2Spans = driver.findElements(By.cssSelector(".RaceData02 span"));
        	for(int i = 0; i < raceDataLine2Spans.size(); i++) {
        		switch(i) {
        			case 1: 
        				document = documentUtil.replaceOrAddElement(document, "stadium", 
        					translateService.translate(TranslateDataType.STADIUM, raceDataLine2Spans.get(i).getText(), false));
        				break;
        			case 3: 
        				document = documentUtil.replaceOrAddElement(document, "ages", 
        					raceDataLine2Spans.get(i).getText().replace("障害", "").replace("サラ系", "").replace("歳", "세").replace("以上", " 이상"));
        				break;
        			case 4: 
        				document = documentUtil.replaceOrAddElement(document, "race_class", 
    						raceDataLine2Spans.get(i).getText()
    						.replace("障害", "")//붙어있음...
    						.replace("歳", "세 ")
    						.replace("新馬", "신마전")
    						.replace("未勝利", "미승리전")
    						.replace("オープン", "오픈")
    						.replace("勝クラス", "승 클래스")
    						.replace("万下", "만엔 이하"));
        				break;
        			case 5:
        				if(raceDataLine2Spans.get(i).getText().contains("牝") && !raceDataLine2Spans.get(i).getText().contains("牡")) document.append("lady_only", true);
        				else document.append("lady_only", false);
        				break;
        			case 8: 
        				String[] earningArray = raceDataLine2Spans.get(i).getText().replace("本賞金:", "").replace("万円", "").split("\\,");
        				Document earnings = new Document();
        				for(int place = 1; place <= earningArray.length; place++) {
        					earnings.append(Integer.toString(place), documentUtil.convertToInteger(earningArray[place - 1]));
        				}
        				document = documentUtil.replaceOrAddElement(document, "earnings", earnings);
        				break;
        		}
        	}
        	
        	//출주마 목록 스크래핑
        	List<WebElement> horseRows = driver.findElements(By.cssSelector(".HorseList"));
        	List<Document> horses = new ArrayList<>();
        	for(WebElement horseRow : horseRows) {
        		List<WebElement> horseDatas = horseRow.findElements(By.cssSelector("td"));
        		
        		Document listHorse = new Document();
        		for(int i = 0; i < horseDatas.size(); i++) {
        			switch(i) {
            			case 0: 
            				String wakuString = horseDatas.get(i).getText();
            				listHorse.append("waku", documentUtil.convertToInteger(wakuString));
            				break;
            			case 1: 
            				String horseNumberString = horseDatas.get(i).getText();
            				listHorse.append("horse_number", documentUtil.convertToInteger(horseNumberString));
            				break;
            			case 3: 
            				//horses에 넣을 데이터
            				Document horse = new Document();
            				
            				
            				WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
            				
            				String originalId = horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", "");
            				
            				Optional<Document> existingHorse = horseService.findByOriginalId(originalId);
            				if(existingHorse.isPresent()) { 
            					horse = existingHorse.get();
            					listHorse.append("horse_id", horse.get("horse_id"));
            				} else {
            					String newUuid = UUID.randomUUID().toString();
            					horse.append("horse_id", newUuid);
            					listHorse.append("horse_id", newUuid);
            				}
            				listHorse.append("original_id", originalId);
            				
            				
            				String horseNameString = horseNameElement.getText().trim();
            				if(horseDatas.get(i).findElements(By.cssSelector("div")).size() > 0) 
            					horse = documentUtil.replaceOrAddElement(horse, "original_name", horseNameString);
            				Optional<Document> translatedDataWrapped = translateService.checkTranslateDataExist(TranslateDataType.HORSE, horseNameString);
            				if(translatedDataWrapped.isPresent()) {
            					horse = documentUtil.replaceOrAddElement(horse, "translated_name", translatedDataWrapped.get().getString("translated"));
            					listHorse.append("translated_name",  translatedDataWrapped.get().getString("translated"));
            				} else {
            					TranslateDto translateDto = new TranslateDto("ja", "ko", horseNameString);
                				HttpEntity<TranslateDto> translateEntity = new HttpEntity<>(translateDto, headers);
                				try {
                					JsonNode result = objectMapper.readTree(restTemplate.postForEntity(apiUrl, translateEntity, String.class).getBody());
                					String translated = result.get("message").get("result").get("translatedText").textValue();
                					//System.out.println("번역 결과");
                					//System.out.println(result.toPrettyString());
                					translateService.insertTranslateData(TranslateDataType.HORSE, horseNameString, translated, true);
                					//기계번역 데이터는 미번역 데이터로 취급합니다.
                					translateService.insertUntranslateData(TranslateDataType.HORSE, horseNameString);
                					horse = documentUtil.replaceOrAddElement(horse, "translated_name", translated);
                					listHorse.append("translated_name",  translated);
                				} catch(Exception e) {
                					log.info(e.toString());
                				}
            				}
            				horse = documentUtil.replaceOrAddElement(horse, "need_to_scrap", true);
            				horse = documentUtil.replaceOrAddElement(horse, "original_id", originalId);
            				horseService.saveDocs(horse);
            				break;
            			case 4: 
            				String sexAndAge = horseDatas.get(i).getText();
            				listHorse.append("gender", sexAndAge.substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마").replace("セ", "거세마"));
            				String ageString = sexAndAge.substring(1);
            				listHorse.append("age", documentUtil.convertToInteger(ageString));
            				break;
            			case 5: 
            				String loadString = horseDatas.get(i).getText();
            				listHorse.append("load_weight", documentUtil.convertToDouble(loadString));
            				break;
            			case 6: 
            				if(horseDatas.get(i).findElements(By.cssSelector("a")).size() > 0) {
            					listHorse.append("jockey", translateService.translate(TranslateDataType.JOCKEY, documentUtil.removeMark(horseDatas.get(i).findElement(By.cssSelector("a")).getText()), true));
            				} else {
            					listHorse.append("jockey", "미정");
            				}
            				
            				break;
            			case 7: 
            				WebElement home = horseDatas.get(i);
            				listHorse.append("region", home.findElement(By.cssSelector("span")).getText().replace("美浦", "미호").replace("栗東", "릿토").replace("地方", "지방"));
            				listHorse.append("trainer", translateService.translate(TranslateDataType.TRAINER, home.findElement(By.cssSelector("a")).getText(), true));
            				break;
            			case 8: 
            				String weightAndChanges = horseDatas.get(i).getText();
                            if(weightAndChanges.trim().equals("計不")) {
                            	listHorse.append("weight", 0);
                            } else {
                            	if(weightAndChanges.contains("(")) {
                            		String weightString = weightAndChanges.substring(0, weightAndChanges.indexOf("("));
                            		String weightChangeString = weightAndChanges.substring(weightAndChanges.indexOf("(") + 1, weightAndChanges.indexOf(")"));
                            		listHorse.append("weight", documentUtil.convertToInteger(weightString));
                            		listHorse.append("weight_change", documentUtil.convertToInteger(weightChangeString));
                            	} else {
                            		listHorse.append("weight", documentUtil.convertToInteger(weightAndChanges));
                            	}
                            }
            				break;
            			case 9: 
            				String ownesString = horseDatas.get(i).findElement(By.cssSelector("span")).getText();
            				listHorse.append("ownes", documentUtil.convertToDouble(ownesString));
            				break;
            			case 10: 
            				String expectedString = horseDatas.get(i).findElement(By.cssSelector("span")).getText();
            				listHorse.append("expected", documentUtil.convertToInteger(expectedString));
            				break;
            			default: break;
        			}
        		}
        		horses.add(listHorse);
        	}
        	document = documentUtil.replaceOrAddElement(document, "horses", horses);
        	document = documentUtil.replaceOrAddElement(document, "host", RaceHost.JRA);
        	document = documentUtil.replaceOrAddElement(document, "is_ended", false);
        	raceService.saveDocs(document);
        	Thread.sleep(500);
        }
	}
	
	public void scrapEndedRaceData(RaceService raceService, HorseService horseService, TranslateService translateService, WebDriver driver, 
			Document endedRace, String apiUrl, ObjectMapper objectMapper, RestTemplate restTemplate, HttpHeaders headers) {
		
		boolean isRequested = endedRace.getBoolean("scrap_requested") != null;
		
		//레이스 정보 스크래핑
    	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
    	endedRace.append("name", 
    			translateService.translate(TranslateDataType.RACE, raceNameAndGrade.getText()
    			.replace("以上", "이상 ")
    			.replace("障害", "장애물 ")
				.replace("歳", "세 ")
				.replace("新馬", "신마전")
				.replace("未勝利", "미승리전")
				.replace("オープン", "오픈")
				.replace("勝クラス", "승 클래스")
				.replace("万下", "만엔 이하"), false));
    	//요청 데이터일시 기초 데이터도 모두 스크래핑
		if(isRequested) {
			String roundString = driver.findElement(By.cssSelector(".RaceNum")).getText();
			endedRace.append("round", documentUtil.convertToInteger(roundString));
			
			//레이스 정보 스크래핑
			if(raceNameAndGrade.findElements(By.cssSelector("span")).size() > 0) {
        		String gradeIconClasses = raceNameAndGrade.findElement(By.cssSelector("span")).getAttribute("class");
        		if(gradeIconClasses.contains("Icon_GradeType18")) {
        			endedRace.append("grade", RaceGrade.ONE);
        			endedRace.append("grade_number", 2);
            	} else if(gradeIconClasses.contains("Icon_GradeType17")) {
            		endedRace.append("grade", RaceGrade.TWO);
            		endedRace.append("grade_number", 3);
            	} else if(gradeIconClasses.contains("Icon_GradeType16")) {
            		endedRace.append("grade", RaceGrade.THREE);
            		endedRace.append("grade_number", 4);
            	} else if(gradeIconClasses.contains("Icon_GradeType15")) {
            		endedRace.append("grade", RaceGrade.L);
            		endedRace.append("grade_number", 5);
            	} else if(gradeIconClasses.contains("Icon_GradeType5")) {
            		endedRace.append("grade", RaceGrade.OP);
            		endedRace.append("grade_number", 6);
            	} else if(gradeIconClasses.contains("Icon_GradeType3") || gradeIconClasses.contains("Icon_GradeType12")) {
            		endedRace.append("grade", RaceGrade.G3);
            		endedRace.append("grade_number", 7);
            	} else if(gradeIconClasses.contains("Icon_GradeType2") || gradeIconClasses.contains("Icon_GradeType11")) {
            		endedRace.append("grade", RaceGrade.G2);
            		endedRace.append("grade_number", 8);
            	} else if(gradeIconClasses.contains("Icon_GradeType1") || gradeIconClasses.contains("Icon_GradeType10")) {
            		endedRace.append("grade", RaceGrade.G1);
            		endedRace.append("grade_number", 9);
            	} else {
            		endedRace.append("grade", RaceGrade.NONE);
            		endedRace.append("grade_number", 1);
            	}
        	} else {
        		if(endedRace.getString("name").contains("1승 클래스")) {
        			endedRace.append("grade", RaceGrade.ONE);
        			endedRace.append("grade_number", 2);
        		} else if(endedRace.getString("name").contains("2승 클래스")) {
        			endedRace.append("grade", RaceGrade.TWO);
        			endedRace.append("grade_number", 3);
        		} else if(endedRace.getString("name").contains("3승 클래스")) {
        			endedRace.append("grade", RaceGrade.THREE);
        			endedRace.append("grade_number", 4);
        		} else {
        			endedRace.append("grade", RaceGrade.NONE);
        			endedRace.append("grade_number", 1);
        		}
        	}
			
			WebElement raceDataLine1 = driver.findElement(By.cssSelector(".RaceData01"));
        	//String startTime = raceDataLine1.getText();
        	String[] times = raceDataLine1.getText().substring(0, raceDataLine1.getText().indexOf("発走")).split(":");
        	if(times.length == 2) {
        		String activedDate = driver.findElement(By.cssSelector("#RaceList_DateList")).findElement(By.cssSelector(".Active")).getText();
        		String monthString = activedDate.substring(0, activedDate.indexOf("月"));
        		String dayString = activedDate.substring(activedDate.indexOf("月") + 1, activedDate.indexOf("日"));
        		
        		LocalDateTime startTime = LocalDateTime.now()
        			.withYear(documentUtil.convertToInteger(endedRace.getString("original_id").substring(0, 4)))
        			.withMonth(documentUtil.convertToInteger(monthString))
        			.withDayOfMonth(documentUtil.convertToInteger(dayString))
    				.withHour(Integer.parseInt(times[0]))
    				.withMinute(Integer.parseInt(times[1]))
    				.withSecond(0)
    				.withNano(0);
        		endedRace.append("start_time", startTime);
        	} else {
        		log.info("경기 시작시간 parsing에 실패했습니다.");
        	}
        	
        	String raceSpecs = raceDataLine1.findElement(By.cssSelector("span")).getText();
        	endedRace.append("track", 
        			translateService.translate(TranslateDataType.TRACK, raceSpecs.replaceAll("[0-9]", "").replace("m", ""), false));
        	endedRace.append("distance", documentUtil.convertToInteger(raceSpecs.replaceAll("[^0-9]", "")));
        	if(endedRace.getString("track").equals("잔디")) endedRace.append("track_number", 1);
        	else if(endedRace.getString("track").equals("더트")) endedRace.append("track_number", 2);
        	else if(endedRace.getString("track").equals("장애물")) endedRace.append("track_number", 3);
        	
        	List<WebElement> raceDataLine2Spans = driver.findElements(By.cssSelector(".RaceData02 span"));
        	for(int i = 0; i < raceDataLine2Spans.size(); i++) {
        		switch(i) {
        			case 1: 
        				endedRace.append("stadium", 
        					translateService.translate(TranslateDataType.STADIUM, raceDataLine2Spans.get(i).getText(), false));
        				break;
        			case 3: 
        				endedRace.append("ages", 
        					raceDataLine2Spans.get(i).getText().replace("障害", "").replace("サラ系", "").replace("歳", "세").replace("以上", " 이상"));
        				break;
        			case 4: 
        				endedRace.append("race_class", 
    						raceDataLine2Spans.get(i).getText()
    						.replace("障害", "")//붙어있음...
    						.replace("歳", "세 ")
    						.replace("新馬", "신마전")
    						.replace("未勝利", "미승리전")
    						.replace("オープン", "오픈")
    						.replace("勝クラス", "승 클래스")
    						.replace("万下", "만엔 이하"));
        				break;
        			case 5:
        				if(raceDataLine2Spans.get(i).getText().contains("牝") && !raceDataLine2Spans.get(i).getText().contains("牡")) endedRace.append("lady_only", true);
        				else endedRace.append("lady_only", false);
        				break;
        			case 8: 
        				String[] earningArray = raceDataLine2Spans.get(i).getText().replace("本賞金:", "").replace("万円", "").split("\\,");
        				Document earnings = new Document();
        				for(int place = 1; place <= earningArray.length; place++) {
        					earnings.append(Integer.toString(place), documentUtil.convertToInteger(earningArray[place - 1]));
        				}
        				endedRace.append("earnings", earnings);
        				break;
        		}
        	}
			
			endedRace.remove("scrap_requested");
		}
    	
    	WebElement raceDataLine1 = driver.findElement(By.cssSelector(".RaceData01"));
    	for(String raceData : raceDataLine1.getText().split(" ")) {
    		if(raceData.contains("発走")) {
    			//이미 처리한 데이터
    		} else if(raceData.contains("m")) {
    			//System.out.println("마장: " + translateService.translate(TranslateDataType.TRACK, raceData.replaceAll("[0-9]", "").replace("m", ""), false));
            	//System.out.println("거리: " + raceData.replaceAll("[^0-9]", ""));
    		} else if(raceData.contains("天候:")) {
    			endedRace = documentUtil.replaceOrAddElement(endedRace, "weather", 
    					translateService.translate(TranslateDataType.WEATHER, raceData.replace("天候:", ""), false));
    		} else if(raceData.contains("馬場:")) {
    			endedRace = documentUtil.replaceOrAddElement(endedRace, "condition", 
    					translateService.translate(TranslateDataType.CONDITION, raceData.replace("馬場:", ""), false));
    			
    			if(endedRace.getString("condition").equals("양마장")) endedRace.append("condition_number", 1);
    			else if(endedRace.getString("condition").equals("약중마장")) endedRace.append("condition_number", 2);
    			else if(endedRace.getString("condition").equals("중마장")) endedRace.append("condition_number", 3);
    			else if(endedRace.getString("condition").equals("불량마장")) endedRace.append("condition_number", 4);
    		}
    	}
    	
    	//출주마 목록 스크래핑
    	List<Document> horses = new ArrayList<>();
    	List<WebElement> horseRows = driver.findElements(By.cssSelector(".HorseList"));
    	for(WebElement horseRow : horseRows) {
    		List<WebElement> horseDatas = horseRow.findElements(By.cssSelector("td"));
    		Document listHorse = new Document();
    		for(int i = 0; i < horseDatas.size(); i++) {
    			switch(i) {
    				case 0: 
    					String rposString = horseDatas.get(i).getText();
    					listHorse.append("rpos", documentUtil.convertToInteger(rposString));
    					break;
        			case 1: 
        				String wakuString = horseDatas.get(i).getText();
        				listHorse.append("waku", documentUtil.convertToInteger(wakuString));
        				break;
        			case 2: 
        				String horseNumberString = horseDatas.get(i).getText();
        				listHorse.append("horse_number", documentUtil.convertToInteger(horseNumberString));
        				break;
        			case 3: 
        				
        				if(!isRequested) {
	        				Document horse = new Document();
	        				
	        				WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
	        				String originalId = horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", "");
	        				
	        				Optional<Document> existingHorse = horseService.findByOriginalId(originalId);
	        				if(existingHorse.isPresent()) { 
	        					horse = existingHorse.get();
	        					listHorse.append("horse_id", horse.getString("horse_id"));
	        					listHorse.append("original_id", originalId);
	        				} else {
	        					String newUuid = UUID.randomUUID().toString();
	        					horse.append("horse_id", newUuid);
	        					listHorse.append("horse_id", newUuid);
	        					listHorse.append("original_id", originalId);
	        				}
	        				
	        				String horseNameString = horseNameElement.getText().trim();
	        				//if(horseDatas.get(i).findElements(By.cssSelector("div")).size() > 0) 
	        				horse = documentUtil.replaceOrAddElement(horse, "original_name", horseNameString);
	        				Optional<Document> translatedDataWrapped = translateService.checkTranslateDataExist(TranslateDataType.HORSE, horseNameString);
	        				if(translatedDataWrapped.isPresent()) {
	        					Document translateData = translatedDataWrapped.get();
	        					if(translateData.getBoolean("translated_by_machine")) {
	        						horse = documentUtil.replaceOrAddElement(horse, "translated_by_machine", true);
	        					} else {
	        						horse = documentUtil.replaceOrAddElement(horse, "translated_by_machine", false);
	        					}
	        					horse = documentUtil.replaceOrAddElement(horse, "translated_name", translatedDataWrapped.get().getString("translated"));
	        					listHorse.append("translated_name",  translatedDataWrapped.get().getString("translated"));
	        				} else {
	        					TranslateDto translateDto = new TranslateDto("ja", "ko", horseNameString);
	            				HttpEntity<TranslateDto> translateEntity = new HttpEntity<>(translateDto, headers);
	            				try {
	            					JsonNode result = objectMapper.readTree(restTemplate.postForEntity(apiUrl, translateEntity, String.class).getBody());
	            					String translated = result.get("message").get("result").get("translatedText").textValue();
	            					//System.out.println("번역 결과");
	            					//System.out.println(result.toPrettyString());
	            					translateService.insertTranslateData(TranslateDataType.HORSE, horseNameString, translated, true);
	            					//기계번역 데이터는 미번역 데이터로 취급합니다.
	            					translateService.insertUntranslateData(TranslateDataType.HORSE, horseNameString);
	            					horse = documentUtil.replaceOrAddElement(horse, "translated_name", translated);
	            					horse = documentUtil.replaceOrAddElement(horse, "translated_by_machine", true);
	            					horse.append("translated_name", translated);
	            					horse.append("translated_by_machine", true);
	            					listHorse.append("translated_name",  translated);
	            				} catch(Exception e) {
	            					log.info(e.toString());
	            				}
	        				}
	        				//horse = documentUtil.replaceOrAddElement(horse, "need_to_scrap", true);
	        				horse = documentUtil.replaceOrAddElement(horse, "original_id", originalId);
	        				horse.append("last_race_time", endedRace.getDate("start_time"));
	        				horseService.saveDocs(horse);
        				} else {
        					WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
	        				String originalId = horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", "");
        					Optional<Document> existingHorse = horseService.findByOriginalId(originalId);
	        				if(existingHorse.isPresent()) { 
	        					listHorse.append("horse_id", existingHorse.get().getString("horse_id"));
	        				} else {
	        					listHorse.append("original_id", originalId);
	        				}
	        				String translated = translateService.translate(TranslateDataType.HORSE, horseNameElement.getText().trim(), false);
	        				listHorse.append("translated_name",  translated);
        				}
        				break;
        			case 4: 
        				String sexAndAge = horseDatas.get(i).getText();
        				listHorse.append("gender", sexAndAge.substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마").replace("セ", "거세마"));
        				String ageString = sexAndAge.substring(1);
        				listHorse.append("age", documentUtil.convertToInteger(ageString));
        				break;
        			case 5: 
        				String loadWeightString = horseDatas.get(i).getText();
        				listHorse.append("load_weight", documentUtil.convertToDouble(loadWeightString));
        				break;
        			case 6: 
        				listHorse.append("jockey", translateService.translate(TranslateDataType.JOCKEY, documentUtil.removeMark(horseDatas.get(i).getText()), true));
        				break;
        			case 7: 
        				listHorse.append("time", horseDatas.get(i).getText());
        				break;
        			case 8: 
        				listHorse.append("interval", documentUtil.convertToDouble(horseDatas.get(i).getText()));
        				break;
        			case 9: 
        				String expectedString = horseDatas.get(i).getText();
        				listHorse.append("expected", documentUtil.convertToInteger(expectedString));
        				break;
        			case 10: 
        				String ownesString = horseDatas.get(i).getText();
        				listHorse.append("ownes", documentUtil.convertToDouble(ownesString));
        				break;
        			case 11: 
        				String last3fString = horseDatas.get(i).getText();
        				listHorse.append("last_3f", documentUtil.convertToDouble(last3fString));
        				break;
        			case 12: 
        				listHorse.append("conner_throughs", horseDatas.get(i).getText());
        				break;
        			case 13: 
        				WebElement home = horseDatas.get(i);
        				listHorse.append("region", home.findElement(By.cssSelector("span")).getText().replace("美浦", "미호").replace("栗東", "릿토").replace("地方", "지방"));
        				listHorse.append("trainer", translateService.translate(TranslateDataType.TRAINER, home.findElement(By.cssSelector("a")).getText(), true));
        				break;
        			case 14: 
        				String weightAndChanges = horseDatas.get(i).getText();
                        if(weightAndChanges.trim().equals("計不")) {
                        	listHorse.append("weight", 0);
                        } else {
                        	if(weightAndChanges.contains("(")) {
                        		String weightString = weightAndChanges.substring(0, weightAndChanges.indexOf("("));
                        		String weightChangeString = weightAndChanges.substring(weightAndChanges.indexOf("(") + 1, weightAndChanges.indexOf(")"));
                        		listHorse.append("weight", documentUtil.convertToInteger(weightString));
                        		listHorse.append("weight_change", documentUtil.convertToInteger(weightChangeString));
                        	} else {
                        		listHorse.append("weight", documentUtil.convertToInteger(weightAndChanges));
                        	}
                        }
        				break;
        			default: break;
    			}
    		}
    		horses.add(listHorse);
    	}
    	endedRace = documentUtil.replaceOrAddElement(endedRace, "horses", horses);
    	endedRace = documentUtil.replaceOrAddElement(endedRace, "host", RaceHost.JRA);
    	endedRace = documentUtil.replaceOrAddElement(endedRace, "is_ended", true);
    	raceService.saveDocs(endedRace);
	}
	
	
	public void scrapHorseData(RaceService raceService, HorseService horseService, TranslateService translateService, WebDriver driver, Document horseData, boolean isEnded) {
		try {
			//경주마 프로필
            WebElement horseTitle = driver.findElement(By.cssSelector(".horse_title"));
            if(!horseData.containsKey("translated_name")) {
            	String originalName = horseTitle.findElement(By.cssSelector("h1")).getText().trim();
            	horseData.append("translated_name", translateService.translate(TranslateDataType.HORSE, originalName, false));
            	horseData.append("original_name", originalName);
            }
            if(horseTitle.findElements(By.cssSelector(".eng_name a")).size() > 0)
            	horseData = documentUtil.replaceOrAddElement(horseData, "english_name", horseTitle.findElement(By.cssSelector(".eng_name a")).getText().trim());
            String[] horseBaseDatas = horseTitle.findElement(By.cssSelector(".txt_01")).getText().split("　");
            
            for(String horseBaseData : horseBaseDatas) {
            	if(horseBaseData.contains("歳")) {
            		horseData.append("age", documentUtil.convertToInteger(horseBaseData));
            	}
            	if(horseBaseData.contains("抹消")) {
            		horseData.append("age", 9999);
            	}
            	if(horseBaseData.contains("牡")) {
        			horseData.append("gender", "숫말");
        		} else if(horseBaseData.contains("牝")) {
        			horseData.append("gender", "암말");
        		} else if(horseBaseData.contains("騸") || horseBaseData.contains("セ")) {
        			horseData.append("gender", "거세마");
        		}
            	if(horseBaseData.contains("毛")) {
            		horseData.append("color", translateService.translate(TranslateDataType.COLOR, horseBaseData, true));
            	}
            }
            
            List<WebElement> horseProps = driver.findElements(By.cssSelector(".db_prof_table tbody tr"));
            for(WebElement horseProp : horseProps) {
            	String header = horseProp.findElement(By.cssSelector("th")).getText().trim();
            	if(header.equals("生年月日")) {
            		String[] birthdayStrings = horseProp.findElement(By.cssSelector("td")).getText().replace("年", "-").replace("月", "-").replace("日", "").trim().split("-");
                    LocalDate birthday = LocalDate
                    		.now()
                    		.withYear(Integer.parseInt(birthdayStrings[0]))
                    		.withMonth(Integer.parseInt(birthdayStrings[1]))
                    		.withDayOfMonth(Integer.parseInt(birthdayStrings[2]));
            		horseData = documentUtil.replaceOrAddElement(horseData, "birthday", birthday);
            	} else if(header.equals("調教師")) {
            		String trainerAndRegion = horseProp.findElement(By.cssSelector("td")).getText();
                    if(trainerAndRegion.contains("(美浦)")) horseData = documentUtil.replaceOrAddElement(horseData, "region", "미호");
                    else if(trainerAndRegion.contains("(栗東)")) horseData = documentUtil.replaceOrAddElement(horseData, "region", "릿토"); 
                    else if(trainerAndRegion.contains("(地方)")) horseData = documentUtil.replaceOrAddElement(horseData, "region", "지방"); 
                    horseData = documentUtil.replaceOrAddElement(horseData, "trainer", 
                    		translateService.translate(TranslateDataType.TRAINER, horseProp.findElement(By.cssSelector("td a")).getText(), true));
            	} else if(header.equals("馬主")) {
            		horseData = documentUtil.replaceOrAddElement(horseData, "owner", 
            				translateService.translate(TranslateDataType.OWNER, horseProp.findElement(By.cssSelector("td")).getText(), false)); 
            	} else if(header.equals("募集情報")) {
            		horseData = documentUtil.replaceOrAddElement(horseData, "club_price", horseProp.findElement(By.cssSelector("td")).getText().replace("口", "구").replace("万円", "만엔")); 
            	} else if(header.equals("生産者")) {
            		horseData = documentUtil.replaceOrAddElement(horseData, "breeder", 
            				translateService.translate(TranslateDataType.BREEDER, horseProp.findElement(By.cssSelector("td")).getText(), false)); 
            	} else if(header.equals("産地")) {
            		horseData = documentUtil.replaceOrAddElement(horseData, "hometown", 
            				translateService.translate(TranslateDataType.HOMETOWN, horseProp.findElement(By.cssSelector("td")).getText(), false)); 
            	} else if(header.equals("セリ取引価格")) {
            		String salePriceString = horseProp.findElement(By.cssSelector("td")).getText();
            		int indexOfPar = salePriceString.indexOf("(");
            		if(indexOfPar > -1) {
            			horseData = documentUtil.replaceOrAddElement(horseData, "sale_price", 
	            				salePriceString.substring(0, indexOfPar).replace("億", "억 ").replace("万", "만 ").replace("円", "엔")); 
            		} else {
            			horseData = documentUtil.replaceOrAddElement(horseData, "sale_price", salePriceString.replace("億", "억 ").replace("万", "만 ").replace("円", "엔")); 
            		}
            		horseData.append("sale_price_long", documentUtil.priceToInteger(salePriceString));
            		
            	} else if(header.equals("獲得賞金")) {
            		String[] earnings = horseProp.findElement(By.cssSelector("td")).getText().split("/");
            		for(String earning : earnings) {
                    	if(earning.contains("(中央)")) {
                    		horseData = documentUtil.replaceOrAddElement(horseData, "jra_price", earning.replace("億", "억 ").replace("万円", "만 엔").replace(" (中央)", "")); 
                    		horseData.append("jra_price_long", documentUtil.priceToInteger(earning));
                    	} else if(earning.contains("(地方)")) {
                    		horseData = documentUtil.replaceOrAddElement(horseData, "local_price", earning.replace("億", "억 ").replace("万円", "만 엔").replace(" (地方)", "")); 
                    		horseData.append("local_price_long", documentUtil.priceToInteger(earning));
                    	}
                    }
            	} else if(header.equals("通算成績")) {
            		String[] records = horseProp.findElement(By.cssSelector("td")).getText().replace("[", "").replace("]", "").split(" ");
                    horseData = documentUtil.replaceOrAddElement(horseData, "overalls", records[0].replace("戦", "전 ").replace("勝", "승").trim()); 
                    horseData = documentUtil.replaceOrAddElement(horseData, "arrivals", records[1].trim()); 
            	}
            }
            
            List<WebElement> bloodlineTable = driver.findElements(By.cssSelector(".blood_table tr"));
            horseData.append("father", 
            		translateService.translateJapaneseOnly(TranslateDataType.STALION, 
            				bloodlineTable.get(0).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
            horseData.append("mother",
            		translateService.translateJapaneseOnly(TranslateDataType.MARE, 
            				bloodlineTable.get(2).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
            horseData.append("bms",
            		translateService.translateJapaneseOnly(TranslateDataType.STALION, 
            				bloodlineTable.get(2).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText()));
            
            //출주 기록
            List<Document> raceResults = new ArrayList<>();
            List<WebElement> raceResultElements = driver.findElements(By.cssSelector(".db_h_race_results tbody tr"));
            for(WebElement raceResultElement : raceResultElements) {
            	Document raceResult = new Document();
            	
            	List<WebElement> raceResultProperties = raceResultElement.findElements(By.cssSelector("td"));
        		String raceDateStrings[] = raceResultProperties.get(0).findElement(By.cssSelector("a")).getText().replace("/", "-").trim().split("-");
                LocalDate raceDate = LocalDate.now()
                		.withYear(Integer.parseInt(raceDateStrings[0]))
                		.withMonth(Integer.parseInt(raceDateStrings[1]))
                		.withDayOfMonth(Integer.parseInt(raceDateStrings[2]));
        		raceResult.append("race_date", raceDate);
				String stadiumString = raceResultProperties.get(1).findElement(By.cssSelector("a")).getText();
            	raceResult.append("stadium", translateService.translate(TranslateDataType.STADIUM, stadiumString.replaceAll("[0-9]", ""), false));
            	raceResult.append("weather", translateService.translate(TranslateDataType.WEATHER, raceResultProperties.get(2).getText(), false));
            	String roundString = raceResultProperties.get(3).getText();
            	raceResult.append("round", documentUtil.convertToInteger(roundString));
            	
            	String originalId = raceResultProperties.get(4).findElement(By.cssSelector("a")).getAttribute("href").replaceAll("[^0-9]", "");
            	raceResult.append("original_id", originalId);
            	
                String raceString = raceResultProperties.get(4).findElement(By.cssSelector("a")).getText();
                if(raceString.contains("(")) {
            		raceResult.append("name", 
            				translateService.translate(TranslateDataType.RACE, raceString.substring(0, raceString.indexOf("(")), false)
            				.replace("以上", "이상 ")
		        			.replace("障害", "장애물 ")
	            			.replace("歳", "세 ")
							.replace("新馬", "신마전")
							.replace("未勝利", "미승리전")
							.replace("オープン", "오픈")
							.replace("勝クラス", "승 클래스")
							.replace("万下", "만엔 이하"));
            		//System.out.println("그레이드: " + raceString.substring(raceString.indexOf("(") + 1, raceString.indexOf(")")));
            		String gradeString = raceString.substring(raceString.indexOf("(") + 1, raceString.indexOf(")"));
            		if(gradeString.contains("1勝")) {
            			raceResult.append("grade", RaceGrade.ONE);
                	} else if(gradeString.contains("2勝")) {
                		raceResult.append("grade", RaceGrade.TWO);
                	} else if(gradeString.contains("3勝")) {
                		raceResult.append("grade", RaceGrade.THREE);
                	} else if(gradeString.contains("OP")) {
                		raceResult.append("grade", RaceGrade.OP);
                	} else if(gradeString.contains("L")) {
                		raceResult.append("grade", RaceGrade.L);
                	} else if(gradeString.contains("G3") || gradeString.contains("J.G3")) {
                		raceResult.append("grade", RaceGrade.G3);
                	} else if(gradeString.contains("G2") || gradeString.contains("J.G2")) {
                		raceResult.append("grade", RaceGrade.G2);
                	} else if(gradeString.contains("G1") || gradeString.contains("J.G1")) {
                		raceResult.append("grade", RaceGrade.G1);
                	} else {
                		raceResult.append("grade", RaceGrade.NONE);
                	}
            	} else {
            		raceResult.append("name", 
        				translateService.translate(TranslateDataType.RACE, raceString, true)
        				.replace("以上", "이상 ")
	        			.replace("障害", "장애물 ")
            			.replace("歳", "세 ")
						.replace("新馬", "신마전")
						.replace("未勝利", "미승리전")
						.replace("オープン", "오픈")
						.replace("勝クラス", "승 클래스")
						.replace("万下", "만엔 이하"));
            		raceResult.append("grade", RaceGrade.NONE);
            	}
                
                String horseCountString = raceResultProperties.get(6).getText();
                raceResult.append("horse_count", documentUtil.convertToInteger(horseCountString));
                String wakuString = raceResultProperties.get(7).getText();
                raceResult.append("waku", documentUtil.convertToInteger(wakuString));
                String horseNumberString = raceResultProperties.get(8).getText();
                raceResult.append("horse_number", documentUtil.convertToInteger(horseNumberString));
                String ownesString = raceResultProperties.get(9).getText();
                raceResult.append("ownes", documentUtil.convertToDouble(ownesString));
                String expectedString = raceResultProperties.get(10).getText();
                raceResult.append("expected", documentUtil.convertToInteger(expectedString));
                String rposString = raceResultProperties.get(11).getText();
                raceResult.append("rpos", documentUtil.convertToInteger(rposString));
                raceResult.append("jockey", translateService.translate(TranslateDataType.JOCKEY, documentUtil.removeMark(raceResultProperties.get(12).getText()), true));
                String loadWeightString = raceResultProperties.get(13).getText();
                raceResult.append("load_weight", documentUtil.convertToDouble(loadWeightString));
                
                String trackRaw = raceResultProperties.get(14).getText().replaceAll("[0-9]", "");
                raceResult.append("track", translateService.translate(TranslateDataType.TRACK, trackRaw, false));
                String distanceString = raceResultProperties.get(14).getText().replaceAll("[^0-9]", "");
                raceResult.append("distance", documentUtil.convertToInteger(distanceString));
				
                raceResult.append("condition", 
                		translateService.translate(TranslateDataType.CONDITION, raceResultProperties.get(15).getText(), true));
                raceResult.append("time", raceResultProperties.get(17).getText());
                raceResult.append("interval", documentUtil.convertToDouble(raceResultProperties.get(18).getText()));
                raceResult.append("conner_throughs", raceResultProperties.get(20).getText());
                raceResult.append("last3f", documentUtil.convertToDouble(raceResultProperties.get(22).getText()));
                
                String weightString = raceResultProperties.get(23).getText();
                if(weightString.trim().equals("計不")) {
                	raceResult.append("weight", 0);
                } else {
                	if(weightString.contains("(")) {
                		raceResult.append("weight", documentUtil.convertToInteger(weightString.substring(0, weightString.indexOf("("))));
                		raceResult.append("weight_change", documentUtil.convertToInteger(weightString.substring(weightString.indexOf("(") + 1, weightString.indexOf(")"))));
                	} else {
                		raceResult.append("weight", documentUtil.convertToInteger(weightString));
                	}
                }
                raceResult.append("first_or_second_place", translateService.translate(TranslateDataType.HORSE, raceResultProperties.get(26).getText().replace("(", "").replace(")", ""), false));
                
                try {
                	String fosOriginalId = raceResultProperties.get(26).findElement(By.cssSelector("a")).getAttribute("href").replaceAll("[^0-9]", "");
                	raceResult.append("fos_original_id", fosOriginalId);
                } catch(Exception e) {/*2착마 기록이 없을수 있음*/}
                
            	raceResult.append("original_id", originalId);
                
                raceResults.add(raceResult);
            }
            horseData = documentUtil.replaceOrAddElement(horseData, "race_results", raceResults);
            if(!horseService.isBloodlineExist(horseData.getString("horse_id"))) {
            	//혈통표
	            driver.navigate().to("https://db.netkeiba.com/horse/ped/" + horseData.getString("original_id"));
	            Thread.sleep(500);
	            
	            List<WebElement> bloodLine = driver.findElements(By.cssSelector(".blood_table tr"));
	            //부계 혈통
	            /*
	            horseData = documentUtil.replaceOrAddElement(horseData, "father", //부
	            	translateService.translateJapaneseOnly(TranslateDataType.STALION, documentUtil.cutBeforePar(bloodLine.get(0).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
	            horseData = documentUtil.replaceOrAddElement(horseData, "mother", //모
	            	translateService.translateJapaneseOnly(TranslateDataType.MARE, documentUtil.cutBeforePar(bloodLine.get(16).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
	            horseData = documentUtil.replaceOrAddElement(horseData, "bms", //모부
		            	translateService.translateJapaneseOnly(TranslateDataType.STALION, documentUtil.cutBeforePar(bloodLine.get(16).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));
	            */
	            Document bloodlines = documentUtil.scrapBloodLineDetail(bloodLine, translateService, documentUtil);
	            bloodlines.append("horse_id", horseData.get("horse_id"));
	            bloodlines.append("original_id", horseData.get("original_id"));
	            horseService.saveBloodLine(bloodlines);
            }
            
            horseData = documentUtil.replaceOrAddElement(horseData, "need_to_scrap", false);
            if(isEnded) horseData.remove("last_race_time");
            horseService.saveDocs(horseData);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

}
