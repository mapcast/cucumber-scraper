package com.cucumber.keiba.scrapper.scheduler;

import java.net.URL;
import java.text.SimpleDateFormat;
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
import com.cucumber.keiba.scrapper.service.leading.LeadingService;
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
	
	private final LeadingService leadingService;
	
	@Value("${translate.client-id}")
	private String translateClientId;
	
	@Value("${translate.client-secret}")
	private String translateClientSecret;
	private void parseUpcomingRaces(LocalDate now, List<String> raceLinks, WebDriver driver) throws Exception {
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
	
	@Scheduled(cron = "0 56 22 * * *")
	public void syncNextWeekRaces() {
		String driverName = "nextWeekRaceDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			//경기 일주일 전 데이터 스크래핑
			LocalDate now = LocalDate.now();
			now = now.plusDays(5);
			//WebDriver driver = scrapperUtil.getChromeDriver();
			WebDriver driver = scrapperUtil.getEdgeDriver();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
			
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
	            parseUpcomingRaces(now, raceLinks, driver);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
	@Scheduled(cron = "0 30 14 * * *")
	public void syncNextDayRaces() {
		String driverName = "nextDayRaceDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			//경기 하루 전날 데이터 스크래핑
			LocalDate now = LocalDate.now();
			now = now.plusDays(1);
			//WebDriver driver = scrapperUtil.getChromeDriver();
			WebDriver driver = scrapperUtil.getEdgeDriver();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
			
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
	            parseUpcomingRaces(now, raceLinks, driver);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
	private void scrapEndedRaceData(WebDriver driver, Document endedRace, String apiUrl, ObjectMapper objectMapper, RestTemplate restTemplate, HttpHeaders headers) {
		
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
        		LocalDateTime startTime = LocalDateTime.now()
        			.withYear(documentUtil.convertToInteger(endedRace.getString("original_id").substring(0, 4)))
        			.withMonth(documentUtil.convertToInteger(endedRace.getString("original_id").substring(4, 6)))
        			.withDayOfMonth(documentUtil.convertToInteger(endedRace.getString("original_id").substring(6, 8)))
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
        				} else {
        					WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
	        				String originalId = horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", "");
        					Optional<Document> existingHorse = horseService.findByOriginalId(originalId);
	        				if(existingHorse.isPresent()) { 
	        					listHorse.append("horse_id", existingHorse.get().getString("horse_id"));
	        				} else {
	        					String newUuid = UUID.randomUUID().toString();
	        					listHorse.append("horse_id", newUuid);
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
		            Thread.sleep(1000); //브라우저 로딩될때까지 잠시 기다린다.
		            scrapEndedRaceData(driver, endedRace, apiUrl, objectMapper, restTemplate, headers);
		        	
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
			}
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
	        
		}
	}
	
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncRequestedRaces() {
		String driverName = "syncRequestedRacesDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			
			ObjectMapper objectMapper = new ObjectMapper();
			RestTemplate restTemplate = new RestTemplate();
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.set("X-Naver-Client-Id", translateClientId);
	        headers.set("X-Naver-Client-Secret", translateClientSecret);
	        
	        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
			
			Document query = new Document("scrap_requested", true);
			MongoCursor<Document> requestRaces = raceService.get20RequestRaces(query);
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://www.google.com");
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
			while(requestRaces.hasNext()) {
				Document raceData = requestRaces.next();
				try {
		        	driver.navigate().to("https://race.netkeiba.com/race/result.html?race_id=" + raceData.getString("original_id"));
		            //Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
		            scrapEndedRaceData(driver, raceData, apiUrl, objectMapper, restTemplate, headers);
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
            		if(horseBaseData.contains("牡")) {
            			horseData.append("gender", "숫말");
            		} else if(horseBaseData.contains("牝")) {
            			horseData.append("gender", "암말");
            		} else if(horseBaseData.contains("騸") || horseBaseData.contains("セ")) {
            			horseData.append("gender", "거세마");
            		}
            		horseData.append("age", documentUtil.convertToInteger(horseBaseData));
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
	
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncHorseDataDetail1() {
		String driverName = "horseDetailScrapDriver1";
		//운영시엔 false로 변경하고 아래 if문 주석 해제.
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
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
		            //Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
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
		//운영시엔 false로 변경하고 아래 if문 주석 해제.
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
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
			            //Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
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
	public void syncRaceEndedHorseDataDetail() {
		String driverName = "raceEndedHorseDetailScrapDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			LocalDateTime searchTime = LocalDateTime.now().minusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0);
			Date startSearchDate = Date.from(searchTime.atZone(ZoneOffset.UTC).toInstant());
			
			Document query = new Document("last_race_time", new Document("$lte", startSearchDate));
			MongoCursor<Document> raceEndedHorseDatas = horseService.getRandom20HorsesByConditions(query);
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://www.google.com");
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
			while(raceEndedHorseDatas.hasNext()) {
				Document horseData = raceEndedHorseDatas.next();
				try {
		        	driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
		            //Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
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
	
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncRequestedHorses() {
		String driverName = "syncRequestedHorsesDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			Document query = new Document("scrap_requested", true);
			MongoCursor<Document> raceEndedHorseDatas = horseService.getRandom20HorsesByConditions(query);
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://www.google.com");
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			//스케줄러 1회 돌때 최대 20건의 경주마 데이터만 변경
			while(raceEndedHorseDatas.hasNext()) {
				Document horseData = raceEndedHorseDatas.next();
				horseData.remove("scrap_requested");
				try {
		        	driver.navigate().to("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
		            //Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
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
	
	@Scheduled(cron = "0 42 0 * * *")
	public void scheduleScheduler() {
		String driverName = "scheduleScrapDriver";
		WebDriver driver = scrapperUtil.getEdgeDriver();
		driver.get("https://race.netkeiba.com/top/schedule.html");
		scrapperUtil.setIsDriverIsRunning(driverName, true);
		try {
			
			Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
			List<WebElement> schedules = driver.findElement(By.cssSelector(".race_table_01")).findElements(By.cssSelector("tr"));
			for(int i = 1; i < schedules.size(); i++) {
				List<WebElement> tds = schedules.get(i).findElements(By.cssSelector("td"));
				Document scheduleData = new Document();
				scheduleData.append("order", i);
				for(int j = 0; j < tds.size(); j++) {
					WebElement td = tds.get(j);
					switch(j) {
					case 0: 
						String monthString = td.getText().substring(0, 2);
						String dateString = td.getText().substring(3, 5);
			            String dayOfWeek = td.getText().substring(6, 7);
			            
						scheduleData.append("month", documentUtil.convertToInteger(monthString));
						scheduleData.append("day_of_month", documentUtil.convertToInteger(dateString));
						scheduleData.append("day_of_week", dayOfWeek
								.replace("日", "일요일")
								.replace("月", "월요일")
								.replace("火", "화요일")
								.replace("水", "수요일")
								.replace("木", "목요일")
								.replace("金", "금요일")
								.replace("土", "토요일"));
						break;
					case 1: 
						scheduleData.append("name", translateService.translate(TranslateDataType.RACE, td.getText(), false));
						break;
					case 2: 
						scheduleData.append("grade", td.getText());
						break;
					case 3: 
						scheduleData.append("stadium", translateService.translate(TranslateDataType.STADIUM, td.getText(), false));
						break;
					case 4: 
						String trackAndDistance = td.getText();
						scheduleData.append("track", translateService.translate(TranslateDataType.TRACK, trackAndDistance.replaceAll("[0-9]", "").replace("m", ""), false));
						scheduleData.append("distance", documentUtil.convertToInteger(trackAndDistance.replaceAll("[^0-9]", "")));
						break;
					case 5: 
						scheduleData.append("restriction", td.getText().replace("歳", "세 ").replace("上", "이상 ").replace("牡牝", "").replace("牡", "").replace("牝", "암말"));
						break;
					case 6: 
						scheduleData.append("weight_standard", td.getText().replace("馬齢", "마령").replace("ハンデ", "핸디캡").replace("別定", "별정").replace("定量", "정량"));
						break;
					default: 
						break;
					}
				}
				
				LocalDateTime startTime = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
				LocalDateTime endTime = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).plusYears(1);
				Date startSearchDate = Date.from(startTime.atZone(ZoneOffset.UTC).toInstant());
				Date endSearchDate = Date.from(endTime.atZone(ZoneOffset.UTC).toInstant());
				Document query = new Document("start_time", new Document("$gt", startSearchDate).append("$lt", endSearchDate)).append("name", scheduleData.getString("name"));
				Document searchResult = raceService.getFirstDocByDocumentQuery(query);
				if(searchResult != null) scheduleData.append("original_id", searchResult.get("original_id"));
				
				raceService.saveSchedule(scheduleData);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		driver.close();	//탭 닫기
        driver.quit();	//브라우저 닫기
        scrapperUtil.setIsDriverIsRunning(driverName, false);
	}
	
	
	@Scheduled(cron = "0 0 21 * * *")
	public void leadingScheduler() {
		//매주 월요일 밤에 실행
		LocalDateTime now = LocalDateTime.now();
		if(now.getDayOfWeek().equals(DayOfWeek.TUESDAY)) {
			String driverName = "leadingScrapDriver";
			WebDriver driver = scrapperUtil.getEdgeDriver();
			driver.get("https://db.netkeiba.com/?pid=jockey_leading");
			scrapperUtil.setIsDriverIsRunning(driverName, true);
			try {
				Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
				List<WebElement> schedules = driver.findElement(By.cssSelector(".race_table_01")).findElements(By.cssSelector("tr"));
				for(int i = 2; i < schedules.size(); i++) {
					List<WebElement> tds = schedules.get(i).findElements(By.cssSelector("td"));
					Document leadingData = new Document();
					leadingData.append("category", "jockey");
					leadingData.append("order", i - 1);
					for(int j = 0; j < tds.size(); j++) {
						WebElement td = tds.get(j);
						switch(j) {
						case 0: 
							leadingData.append("rank", documentUtil.convertToInteger(td.getText()));
							break;
						case 1: 
							leadingData.append("name", translateService.translate(TranslateDataType.JOCKEY, td.getText(), false));
							break;
						case 4: 
							leadingData.append("first", documentUtil.convertToInteger(td.getText()));
							break;
						case 5: 
							leadingData.append("second", documentUtil.convertToInteger(td.getText()));
							break;
						case 6: 
							leadingData.append("third", documentUtil.convertToInteger(td.getText()));
							break;
						case 7: 
							leadingData.append("defeat", documentUtil.convertToInteger(td.getText()));
							break;
						case 8: 
							leadingData.append("biggame_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 9: 
							leadingData.append("biggame_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 10: 
							leadingData.append("special_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 11: 
							leadingData.append("special_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 12: 
							leadingData.append("normal_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 13: 
							leadingData.append("normal_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 14: 
							leadingData.append("turf_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 15: 
							leadingData.append("turf_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 16: 
							leadingData.append("dirt_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 17: 
							leadingData.append("dirt_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 18:
							leadingData.append("win_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10);
							break;
						case 19:
							leadingData.append("second_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10);
							break;
						case 20:
							leadingData.append("third_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10);
							break;
						default: 
							break;
						}
					}
					leadingService.saveDocs(leadingData);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			driver.navigate().to("https://db.netkeiba.com/?pid=trainer_leading");
			try {
				Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
				List<WebElement> schedules = driver.findElement(By.cssSelector(".race_table_01")).findElements(By.cssSelector("tr"));
				for(int i = 2; i < schedules.size(); i++) {
					List<WebElement> tds = schedules.get(i).findElements(By.cssSelector("td"));
					Document leadingData = new Document();
					leadingData.append("category", "trainer");
					leadingData.append("order", i - 1);
					for(int j = 0; j < tds.size(); j++) {
						WebElement td = tds.get(j);
						switch(j) {
						case 0: 
							leadingData.append("rank", documentUtil.convertToInteger(td.getText()));
							break;
						case 1: 
							leadingData.append("name", translateService.translate(TranslateDataType.TRAINER, td.getText(), false));
							break;
						case 4: 
							leadingData.append("first", documentUtil.convertToInteger(td.getText()));
							break;
						case 5: 
							leadingData.append("second", documentUtil.convertToInteger(td.getText()));
							break;
						case 6: 
							leadingData.append("third", documentUtil.convertToInteger(td.getText()));
							break;
						case 7: 
							leadingData.append("defeat", documentUtil.convertToInteger(td.getText()));
							break;
						case 8: 
							leadingData.append("biggame_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 9: 
							leadingData.append("biggame_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 10: 
							leadingData.append("special_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 11: 
							leadingData.append("special_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 12: 
							leadingData.append("normal_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 13: 
							leadingData.append("normal_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 14: 
							leadingData.append("turf_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 15: 
							leadingData.append("turf_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 16: 
							leadingData.append("dirt_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 17: 
							leadingData.append("dirt_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 18:
							leadingData.append("win_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10 );
							break;
						case 19:
							leadingData.append("second_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10 );
							break;
						case 20:
							leadingData.append("third_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10 );
							break;
						default: 
							break;
						}
					}
					leadingService.saveDocs(leadingData);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			driver.navigate().to("https://db.netkeiba.com/?pid=owner_leading");
			try {
				Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
				List<WebElement> schedules = driver.findElement(By.cssSelector(".race_table_01")).findElements(By.cssSelector("tr"));
				for(int i = 2; i < schedules.size(); i++) {
					List<WebElement> tds = schedules.get(i).findElements(By.cssSelector("td"));
					Document leadingData = new Document();
					leadingData.append("category", "owner");
					leadingData.append("order", i - 1);
					for(int j = 0; j < tds.size(); j++) {
						WebElement td = tds.get(j);
						switch(j) {
						case 0: 
							leadingData.append("rank", documentUtil.convertToInteger(td.getText()));
							break;
						case 1: 
							leadingData.append("name", translateService.translate(TranslateDataType.OWNER, td.getText(), false));
							break;
						case 2: 
							leadingData.append("first", documentUtil.convertToInteger(td.getText()));
							break;
						case 3: 
							leadingData.append("second", documentUtil.convertToInteger(td.getText()));
							break;
						case 4: 
							leadingData.append("third", documentUtil.convertToInteger(td.getText()));
							break;
						case 5: 
							leadingData.append("defeat", documentUtil.convertToInteger(td.getText()));
							break;
						case 6: 
							leadingData.append("biggame_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 7: 
							leadingData.append("biggame_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 8: 
							leadingData.append("special_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 9 : 
							leadingData.append("special_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 10: 
							leadingData.append("normal_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 11: 
							leadingData.append("normal_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 12: 
							leadingData.append("turf_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 13: 
							leadingData.append("turf_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 14: 
							leadingData.append("dirt_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 15: 
							leadingData.append("dirt_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 16:
							leadingData.append("win_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10 );
							break;
						case 17:
							leadingData.append("second_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10 );
							break;
						case 18:
							leadingData.append("third_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10 );
							break;
						default: 
							break;
						}
					}
					leadingService.saveDocs(leadingData);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			driver.navigate().to("https://db.netkeiba.com/?pid=sire_leading");
			try {
				Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
				List<WebElement> schedules = driver.findElement(By.cssSelector(".race_table_01")).findElements(By.cssSelector("tr"));
				for(int i = 2; i < schedules.size(); i++) {
					List<WebElement> tds = schedules.get(i).findElements(By.cssSelector("td"));
					Document leadingData = new Document();
					leadingData.append("category", "sire");
					leadingData.append("order", i - 1);
					for(int j = 0; j < tds.size(); j++) {
						WebElement td = tds.get(j);
						switch(j) {
						case 0: 
							leadingData.append("rank", documentUtil.convertToInteger(td.getText()));
							break;
						case 1: 
							leadingData.append("name", translateService.translate(TranslateDataType.STALION, td.getText(), false));
							break;
						case 2: 
							leadingData.append("child_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 3: 
							leadingData.append("winner_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 4: 
							leadingData.append("race_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 5: 
							leadingData.append("race_wins", documentUtil.convertToInteger(td.getText()));
							break;
						case 6: 
							leadingData.append("biggame_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 7: 
							leadingData.append("biggame_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 8: 
							leadingData.append("special_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 9 : 
							leadingData.append("special_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 10: 
							leadingData.append("normal_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 11: 
							leadingData.append("normal_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 12: 
							leadingData.append("turf_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 13: 
							leadingData.append("turf_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 14: 
							leadingData.append("dirt_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 15: 
							leadingData.append("dirt_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 16:
							leadingData.append("win_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10);
							break;
						case 17:
							leadingData.append("aei", documentUtil.convertToDouble(td.getText()));
							break;
						case 18:
							leadingData.append("earnings", documentUtil.convertToDouble(td.getText()));
							break;
						case 19:
							leadingData.append("turf_distance", documentUtil.convertToDouble(td.getText()));
							break;
						case 20:
							leadingData.append("dirt_distance", documentUtil.convertToDouble(td.getText()));
							break;
						default: 
							break;
						}
					}
					leadingService.saveDocs(leadingData);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			driver.navigate().to("https://db.netkeiba.com/?pid=bms_leading");
			try {
				Thread.sleep(500); //브라우저 로딩될때까지 잠시 기다린다.
				List<WebElement> schedules = driver.findElement(By.cssSelector(".race_table_01")).findElements(By.cssSelector("tr"));
				for(int i = 2; i < schedules.size(); i++) {
					List<WebElement> tds = schedules.get(i).findElements(By.cssSelector("td"));
					Document leadingData = new Document();
					leadingData.append("category", "bms");
					leadingData.append("order", i - 1);
					for(int j = 0; j < tds.size(); j++) {
						WebElement td = tds.get(j);
						switch(j) {
						case 0: 
							leadingData.append("rank", documentUtil.convertToInteger(td.getText()));
							break;
						case 1: 
							leadingData.append("name", translateService.translate(TranslateDataType.STALION, td.getText(), false));
							break;
						case 2: 
							leadingData.append("child_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 3: 
							leadingData.append("winner_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 4: 
							leadingData.append("race_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 5: 
							leadingData.append("race_wins", documentUtil.convertToInteger(td.getText()));
							break;
						case 6: 
							leadingData.append("biggame_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 7: 
							leadingData.append("biggame_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 8: 
							leadingData.append("special_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 9 : 
							leadingData.append("special_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 10: 
							leadingData.append("normal_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 11: 
							leadingData.append("normal_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 12: 
							leadingData.append("turf_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 13: 
							leadingData.append("turf_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 14: 
							leadingData.append("dirt_count", documentUtil.convertToInteger(td.getText()));
							break;
						case 15: 
							leadingData.append("dirt_win", documentUtil.convertToInteger(td.getText()));
							break;
						case 16:
							leadingData.append("win_ratio", (double) documentUtil.convertToInteger(td.getText()) / 10);
							break;
						case 17:
							leadingData.append("aei", documentUtil.convertToDouble(td.getText()));
							break;
						case 18:
							leadingData.append("earnings", documentUtil.convertToDouble(td.getText()));
							break;
						case 19:
							leadingData.append("turf_distance", documentUtil.convertToDouble(td.getText()));
							break;
						case 20:
							leadingData.append("dirt_distance", documentUtil.convertToDouble(td.getText()));
							break;
						default: 
							break;
						}
					}
					leadingService.saveDocs(leadingData);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			
			driver.close();	
	        driver.quit();	
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
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
