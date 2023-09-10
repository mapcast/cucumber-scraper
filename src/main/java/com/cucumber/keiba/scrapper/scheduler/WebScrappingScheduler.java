package com.cucumber.keiba.scrapper.scheduler;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.cucumber.keiba.scrapper.dto.datas.TranslateDto;
import com.cucumber.keiba.scrapper.enums.RaceGrade;
import com.cucumber.keiba.scrapper.enums.RaceHost;
import com.cucumber.keiba.scrapper.enums.TranslateDataType;
import com.cucumber.keiba.scrapper.model.TranslateData;
import com.cucumber.keiba.scrapper.service.horse.HorseService;
import com.cucumber.keiba.scrapper.service.race.RaceService;
import com.cucumber.keiba.scrapper.service.translate.TranslateService;
import com.cucumber.keiba.scrapper.util.CommonUtil;
import com.cucumber.keiba.scrapper.util.DocumentUtil;
import com.cucumber.keiba.scrapper.util.WebScrapperUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import net.bytebuddy.implementation.bytecode.Throw;

@Log
@Component
@RequiredArgsConstructor
public class WebScrappingScheduler {
	
	private final WebScrapperUtil scrapperUtil;
	
	private final DocumentUtil documentUtil;
		
	private final TranslateService translateService;
	
	private final RaceService raceService;
	
	private final HorseService horseService;
	
	@Value("${translate.client-id}")
	private String translateClientId;
	
	@Value("${translate.client-secret}")
	private String translateClientSecret;
	/*
	@Scheduled(cron = "0 40 22 * * *")
	public void syncTempData() {
		String driverName = "tempRaceDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			ObjectMapper objectMapper = new ObjectMapper();
			RestTemplate restTemplate = new RestTemplate();
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.set("X-Naver-Client-Id", translateClientId);
	        headers.set("X-Naver-Client-Secret", translateClientSecret);
	        
	        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
									
			WebDriver driver = scrapperUtil.getEdgeDriver();
			try {
	        	driver.get("https://race.netkeiba.com/race/result.html?race_id=202310030504&rf=race_list");
	        	String raceOriginalId = "202310030504";
            	Optional<Document> wrappedDocs = raceService.getByOriginalId(raceOriginalId);
            	Document endedRace = new Document();
            	if(wrappedDocs.isPresent()) {
            		endedRace = wrappedDocs.get();
            	} else {
            		return;
            	}
	            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
	            //레이스 정보 스크래핑
	        	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
	        	endedRace = documentUtil.replaceOrAddElement(endedRace, "name", 
	        			translateService.translate(TranslateDataType.RACE, raceNameAndGrade.getText()
	        			.replace("以上", "이상 ")
	        			.replace("障害", "장애물 ")
    					.replace("歳", "세 ")
						.replace("新馬", "신마전")
						.replace("未勝利", "미승리전")
						.replace("オープン", "오픈")
    					.replace("勝クラス", "승 클래스")
    					.replace("万下", "만엔 이하"), true));
	        	
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
	        					listHorse.append("rpos", commonUtil.convertToInteger(rposString));
	        					break;
	            			case 1: 
	            				String wakuString = horseDatas.get(i).getText();
	            				listHorse.append("waku", commonUtil.convertToInteger(wakuString));
	            				break;
	            			case 2: 
	            				String horseNumberString = horseDatas.get(i).getText();
	            				listHorse.append("horse_number", commonUtil.convertToInteger(horseNumberString));
	            				break;
	            			case 3: 
	            				Document horse = new Document();
                				Optional<Document> existingHorse = horseService.findByOriginalId(horse.getString("original_id"));
                				if(existingHorse.isPresent()) { 
                					horse = existingHorse.get();
                					listHorse.append("horse_id", horse.getString("horse_id"));
                				} else {
                					String newUuid = UUID.randomUUID().toString();
                					horse.append("horse_id", newUuid);
                					listHorse.append("horse_id", newUuid);
                				}
                				
                				WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
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
                				horse = documentUtil.replaceOrAddElement(horse, "need_to_scrap", false);
                				horse = documentUtil.replaceOrAddElement(horse, "original_id", horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", ""));
                				horseService.saveDocs(horse);
	            				break;
	            			case 4: 
	            				String sexAndAge = horseDatas.get(i).getText();
                				listHorse.append("gender", sexAndAge.substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마").replace("セ", "거세마"));
                				String ageString = sexAndAge.substring(1);
                				listHorse.append("age", commonUtil.convertToInteger(ageString));
	            				break;
	            			case 5: 
	            				String loadWeightString = horseDatas.get(i).getText();
	            				listHorse.append("load_weight", commonUtil.convertToDouble(loadWeightString));
	            				break;
	            			case 6: 
	            				listHorse.append("jockey", translateService.translate(TranslateDataType.JOCKEY, commonUtil.removeMark(horseDatas.get(i).getText()), true));
	            				break;
	            			case 7: 
	            				listHorse.append("time", horseDatas.get(i).getText());
	            				break;
	            			case 8: 
	            				listHorse.append("interval", commonUtil.convertToDouble(horseDatas.get(i).getText()));
	            				break;
	            			case 9: 
	            				String expectedString = horseDatas.get(i).getText();
	            				listHorse.append("expected", commonUtil.convertToInteger(expectedString));
	            				break;
	            			case 10: 
	            				String ownesString = horseDatas.get(i).getText();
	            				listHorse.append("ownes", commonUtil.convertToDouble(ownesString));
	            				break;
	            			case 11: 
	            				String last3fString = horseDatas.get(i).getText();
	            				listHorse.append("last_3f", commonUtil.convertToDouble(last3fString));
	            				break;
	            			case 12: 
	            				listHorse.append("conner_throughs", horseDatas.get(i).getText());
	            				break;
	            			case 13: 
	            				WebElement home = horseDatas.get(i);
                				listHorse.append("region", home.findElement(By.cssSelector("span")).getText().replace("美浦", "미호").replace("栗東", "릿토"));
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
                                		listHorse.append("weight", commonUtil.convertToInteger(weightString));
                                		listHorse.append("weight_change", commonUtil.convertToInteger(weightChangeString));
                                	} else {
                                		listHorse.append("weight", commonUtil.convertToInteger(weightAndChanges));
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
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
		log.info("scheduler end");
	}
	*/
	
	@Scheduled(cron = "0 30 19 * * *")
	public void syncUpcomingRaces() {
		
		String driverName = "upcomingRaceDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			ObjectMapper objectMapper = new ObjectMapper();
			RestTemplate restTemplate = new RestTemplate();
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.set("X-Naver-Client-Id", translateClientId);
	        headers.set("X-Naver-Client-Secret", translateClientSecret);
	        
	        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
			
			//경기 하루 전날 데이터 스크래핑
			LocalDate now = LocalDate.now();
			//WebDriver driver = scrapperUtil.getChromeDriver();
			WebDriver driver = scrapperUtil.getEdgeDriver();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
			if(now.getDayOfWeek() == DayOfWeek.FRIDAY || now.getDayOfWeek() == DayOfWeek.SATURDAY) {
				now = now.plusDays(1);
			} else if(now.getDayOfWeek() == DayOfWeek.SUNDAY || now.getDayOfWeek() == DayOfWeek.MONDAY) {
				now = now.plusDays(6);
			} else {
				driver.close();	//탭 닫기
		        driver.quit();	//브라우저 닫기
				return;
			}
			String listPage = String.format("https://race.netkeiba.com/top/race_list.html?kaisai_date=%s", now.format(formatter));
			
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			
			try {
	        	driver.get(listPage);
	            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
	            List<WebElement> dataLists = driver.findElements(By.cssSelector(".RaceList_DataList"));
	            List<String> raceLinks = new ArrayList<>();
	            for(WebElement dataList : dataLists) {
	            	List<WebElement> elements = dataList.findElements(By.cssSelector(".RaceList_DataItem a"));
	            	for(WebElement element : elements)
	            		if(element.getAttribute("href").contains("shutuba") && element.findElement(By.cssSelector(".RaceList_Itemtime")).getText().contains(":")) 
	            			raceLinks.add(element.getAttribute("href"));
	            }
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
	    					.replace("万下", "만엔 이하"), true));
	            	
	            	
	            	if(raceNameAndGrade.findElements(By.cssSelector("span")).size() > 0) {
	            		String gradeIconClasses = raceNameAndGrade.findElement(By.cssSelector("span")).getAttribute("class");
	            		if(gradeIconClasses.contains("Icon_GradeType18")) {
	            			document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.ONE);
	                	} else if(gradeIconClasses.contains("Icon_GradeType17")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.TWO);
	                	} else if(gradeIconClasses.contains("Icon_GradeType16")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.THREE);
	                	} else if(gradeIconClasses.contains("Icon_GradeType15")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.L);
	                	} else if(gradeIconClasses.contains("Icon_GradeType5")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.OP);
	                	} else if(gradeIconClasses.contains("Icon_GradeType3") || gradeIconClasses.contains("Icon_GradeType12")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G3);
	                	} else if(gradeIconClasses.contains("Icon_GradeType2") || gradeIconClasses.contains("Icon_GradeType11")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G2);
	                	} else if(gradeIconClasses.contains("Icon_GradeType1") || gradeIconClasses.contains("Icon_GradeType10")) {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G1);
	                	} else {
	                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.NONE);
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
		        				if(raceDataLine2Spans.get(i).getText().contains("牝")) document.append("lady_only", true);
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
	                				
	                				
	                				String horseNameString = horseNameElement.getText().trim();
	                				if(horseDatas.get(i).findElements(By.cssSelector("div")).size() > 0) 
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
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
	//시작된지 30분 이상 지난 레이스를 찾아서 결과를 저장합니다.
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncEndedRace() {
		String driverName = "endedRaceDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			ObjectMapper objectMapper = new ObjectMapper();
			RestTemplate restTemplate = new RestTemplate();
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.set("X-Naver-Client-Id", translateClientId);
	        headers.set("X-Naver-Client-Secret", translateClientSecret);
	        
	        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
			
			LocalDateTime searchTime = LocalDateTime.now().minusMinutes(30).withSecond(0).withNano(0);
			Date startSearchDate = Date.from(searchTime.atZone(ZoneOffset.UTC).toInstant());
			
			Document query = new Document("start_time", new Document("$lte", startSearchDate)).append("is_ended", false);
			MongoCursor<Document> endedRaces = raceService.getDocsByDocumentQuery(query);
			
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://www.google.com");
			
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			
			while(endedRaces.hasNext()) {
				Document endedRace = endedRaces.next();
				try {
		        	driver.navigate().to("https://race.netkeiba.com/race/result.html?race_id=" + endedRace.getString("original_id"));
		            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
		        	
		        	//레이스 정보 스크래핑
		        	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
		        	endedRace = documentUtil.replaceOrAddElement(endedRace, "name", 
		        			translateService.translate(TranslateDataType.RACE, raceNameAndGrade.getText()
		        			.replace("以上", "이상 ")
		        			.replace("障害", "장애물 ")
	    					.replace("歳", "세 ")
							.replace("新馬", "신마전")
							.replace("未勝利", "미승리전")
							.replace("オープン", "오픈")
	    					.replace("勝クラス", "승 클래스")
	    					.replace("万下", "만엔 이하"), true));
		        	
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
		            				Document horse = new Document();
		            				
	                				WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
	                				String originalId = horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", "");
	                				
	                				Optional<Document> existingHorse = horseService.findByOriginalId(originalId);
	                				if(existingHorse.isPresent()) { 
	                					horse = existingHorse.get();
	                					listHorse.append("horse_id", horse.getString("horse_id"));
	                				} else {
	                					String newUuid = UUID.randomUUID().toString();
	                					horse.append("horse_id", newUuid);
	                					listHorse.append("horse_id", newUuid);
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
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
			}
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
	        
		}
		
	}
	
	private void scrapHorseData(WebDriver driver, Document horseData, boolean isEnded) {
		try {
			//경주마 프로필
            WebElement horseTitle = driver.findElement(By.cssSelector(".horse_title"));
            if(horseTitle.findElements(By.cssSelector(".eng_name a")).size() > 0)
            	horseData = documentUtil.replaceOrAddElement(horseData, "english_name", horseTitle.findElement(By.cssSelector(".eng_name a")).getText().trim());
            String[] horseBaseDatas = horseTitle.findElement(By.cssSelector(".txt_01")).getText().split("　");
			horseData = documentUtil.replaceOrAddElement(horseData, "gender", horseBaseDatas[1].trim().substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마").replace("セ", "거세마"));
			String ageString = horseBaseDatas[1].trim().substring(1);
			horseData = documentUtil.replaceOrAddElement(horseData, "age", documentUtil.convertToInteger(ageString.replace("歳", "세")));
			if(horseBaseDatas.length > 2) {
				horseData = documentUtil.replaceOrAddElement(horseData, "color", 
						translateService.translate(TranslateDataType.COLOR, horseBaseDatas[2], true));
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
	            				salePriceString.substring(0, indexOfPar).replace("億", "억").replace("万円", "만엔")); 
            		} else {
            			horseData = documentUtil.replaceOrAddElement(horseData, "sale_price", salePriceString.replace("億", "억").replace("万円", "만엔")); 
            		}
            		
            	} else if(header.equals("獲得賞金")) {
            		String[] earnings = horseProp.findElement(By.cssSelector("td")).getText().split("/");
            		for(String earning : earnings) {
                    	if(earning.contains("(中央)")) {
                    		horseData = documentUtil.replaceOrAddElement(horseData, "jra_price", earning.replace("億", "억 ").replace("万円", "만 엔").replace(" (中央)", "")); 
                    	} else if(earning.contains("(地方)")) {
                    		horseData = documentUtil.replaceOrAddElement(horseData, "local_price", earning.replace("億", "억 ").replace("万円", "만 엔").replace(" (地方)", "")); 
                    	}
                    }
            	} else if(header.equals("通算成績")) {
            		String[] records = horseProp.findElement(By.cssSelector("td")).getText().replace("[", "").replace("]", "").split(" ");
                    horseData = documentUtil.replaceOrAddElement(horseData, "overalls", records[0].replace("戦", "전 ").replace("勝", "승").trim()); 
                    horseData = documentUtil.replaceOrAddElement(horseData, "arrivals", records[1].trim()); 
            	}
            }
            
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
                String raceString = raceResultProperties.get(4).getText();
                if(raceString.contains("(")) {
            		raceResult.append("name", 
            				translateService.translate(TranslateDataType.RACE, raceString.substring(0, raceString.indexOf("(")), true)
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
                raceResult.append("first_or_second_place", translateService.translate(TranslateDataType.HORSE, raceResultProperties.get(26).getText().replace("(", "").replace(")", ""), true));
                raceResults.add(raceResult);
            }
            horseData = documentUtil.replaceOrAddElement(horseData, "race_results", raceResults);
            if(!horseService.isBloodlineExist(horseData.getString("horse_id")) || !horseData.containsKey("bloodline_male_1")) {
            	//혈통표
	            driver.navigate().to("https://db.netkeiba.com/horse/ped/" + horseData.getString("original_id"));
	            Thread.sleep(500);
	            List<WebElement> bloodLine = driver.findElements(By.cssSelector(".blood_table tr"));
	            //부계 혈통
	            horseData = documentUtil.replaceOrAddElement(horseData, "bloodline_male_1", //부
	            	translateService.translateJapaneseOnly(TranslateDataType.STALION, documentUtil.cutBeforePar(bloodLine.get(0).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
	            horseData = documentUtil.replaceOrAddElement(horseData, "bloodline_female_1", //모
	            	translateService.translateJapaneseOnly(TranslateDataType.MARE, documentUtil.cutBeforePar(bloodLine.get(16).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
	            Document bloodlines = documentUtil.scrapBloodLineDetail(bloodLine, translateService, documentUtil);
	            bloodlines.append("horse_id", horseData.get("horse_id"));
	            horseService.saveBloodLine(bloodlines);
            }
            
            horseData = documentUtil.replaceOrAddElement(horseData, "need_to_scrap", false);
            if(isEnded) horseData.remove("last_race_time");
            horseService.saveDocs(horseData);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncHorseDataDetail1() {
		String driverName = "horseDetailScrapDriver1";
		LocalDateTime now = LocalDateTime.now();
		//운영시엔 false로 변경하고 아래 if문 주석 해제.
		boolean scrapFlag = true;
		//if(now.getHour() <= 9 || now.getHour() >= 22) scrapFlag = true;
		if(!scrapperUtil.isDriverIsRunning(driverName) && scrapFlag) {
			Map<String, Object> conditions = new HashMap<>();
			conditions.put("need_to_scrap", true);
			
			MongoCursor<Document> horseDatas = horseService.getRandom20HorsesByConditions(conditions);
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://www.google.com");
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
			//int count = 0;
			while(horseDatas.hasNext()) {
				Document horseData = horseDatas.next();
				try {
		        	driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
		            Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
		            scrapHorseData(driver, horseData, false);
		            
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
				//count ++;
				//if(count >= 20) break;
			}
			
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncHorseDataDetail2() {
		String driverName = "horseDetailScrapDriver2";
		LocalDateTime now = LocalDateTime.now();
		//운영시엔 false로 변경하고 아래 if문 주석 해제.
		boolean scrapFlag = true;
		//if(now.getHour() <= 9 || now.getHour() >= 22) scrapFlag = true;
		if(!scrapperUtil.isDriverIsRunning(driverName) && scrapFlag) {
			Map<String, Object> conditions = new HashMap<>();
			conditions.put("need_to_scrap", true);
			
			long count = horseService.getDocsCountByConditions(conditions);
			if(count > 50) {
				MongoCursor<Document> horseDatas = horseService.getRandom20HorsesByConditions(conditions);
				WebDriver driver = scrapperUtil.getEdgeDriver();
				driver.get("https://www.google.com");
				scrapperUtil.setIsDriverIsRunning(driverName, true);
				//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
				while(horseDatas.hasNext()) {
					Document horseData = horseDatas.next();
					try {
			        	driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
			            Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
			            scrapHorseData(driver, horseData, false);
			            
			        } catch (Exception e) {
			            e.printStackTrace();
			        }
				}
				
				driver.close();	//탭 닫기
		        driver.quit();	//브라우저 닫기
		        scrapperUtil.setIsDriverIsRunning(driverName, false);
			}
		}
	}
	
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncHorseDataDetail3() {
		String driverName = "horseDetailScrapDriver3";
		//운영시엔 false로 변경하고 아래 if문 주석 해제.
		boolean scrapFlag = true;
		//if(now.getHour() <= 9 || now.getHour() >= 22) scrapFlag = true;
		if(!scrapperUtil.isDriverIsRunning(driverName) && scrapFlag) {
			Map<String, Object> conditions = new HashMap<>();
			conditions.put("need_to_scrap", true);
			
			long count = horseService.getDocsCountByConditions(conditions);
			if(count > 50) {
				MongoCursor<Document> horseDatas = horseService.getRandom20HorsesByConditions(conditions);
				WebDriver driver = scrapperUtil.getEdgeDriver();
				driver.get("https://www.google.com");
				scrapperUtil.setIsDriverIsRunning(driverName, true);
				//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
				while(horseDatas.hasNext()) {
					Document horseData = horseDatas.next();
					try {
			        	driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
			            Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
			            scrapHorseData(driver, horseData, false);
			            
			        } catch (Exception e) {
			            e.printStackTrace();
			        }
				}
				
				driver.close();	//탭 닫기
		        driver.quit();	//브라우저 닫기
		        scrapperUtil.setIsDriverIsRunning(driverName, false);
			}
		}
	}
	
	@Scheduled(cron = "0 0 21 * * *")
	public void syncRaceEndedHorseDataDetail() {
		String driverName = "raceEndedHorseDetailScrapDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			Map<String, Object> conditions = new HashMap<>();
			LocalDateTime searchTime = LocalDateTime.now().minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
			Date startSearchDate = Date.from(searchTime.atZone(ZoneOffset.UTC).toInstant());
			
			Document query = new Document("last_race_time", new Document("$lte", startSearchDate)).append("is_ended", false);
			MongoCursor<Document> raceEndedHorseDatas = horseService.getDocsByDocumentQuery(query);
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://www.google.com");
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
			while(raceEndedHorseDatas.hasNext()) {
				Document horseData = raceEndedHorseDatas.next();
				try {
		        	driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
		            Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
		            scrapHorseData(driver, horseData, true);
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
			}
			
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	/*
	@Scheduled(cron = "0 36 21 * * *")
	public void testHorseSync() {
		String driverName = "testHorseScrapDriver";
		WebDriver driver = scrapperUtil.getEdgeDriver();
		driver.get("https://www.google.com");
		scrapperUtil.setIsDriverIsRunning(driverName, true);
		try {
			Document horseData = new Document();
			Optional<Document> wrappedDocument = horseService.findByOriginalId("2018105204");
			if(wrappedDocument.isPresent()) {
				horseData = wrappedDocument.get();
	            driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
	            Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
	            scrapHorseData(driver, horseData, false);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		driver.close();	//탭 닫기
        driver.quit();	//브라우저 닫기
        scrapperUtil.setIsDriverIsRunning(driverName, false);
	}
	*/
	
	@Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(6); // 스레드 풀 크기 설정
        taskScheduler.setThreadNamePrefix("task-");
        return taskScheduler;
    }

    @Bean
    public TaskSchedulerCustomizer taskSchedulerCustomizer(ThreadPoolTaskScheduler taskScheduler) {
        return taskScheduler1 -> {
            taskScheduler1.setPoolSize(6); // 스레드 풀 크기 설정
            taskScheduler1.setThreadNamePrefix("task-");
        };
    }
}
