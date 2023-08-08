package com.cucumber.keiba.scrapper.scheduler;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.cucumber.keiba.scrapper.dto.datas.TranslateDto;
import com.cucumber.keiba.scrapper.enums.RaceGrade;
import com.cucumber.keiba.scrapper.enums.TranslateDataType;
import com.cucumber.keiba.scrapper.model.TranslateData;
import com.cucumber.keiba.scrapper.service.horse.HorseService;
import com.cucumber.keiba.scrapper.service.race.RaceService;
import com.cucumber.keiba.scrapper.service.translate.TranslateService;
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
	
	@Scheduled(cron = "0 34 21 * * *")
	public void insertTranslateDatas() {
		log.info("scheduler end");
	}
	
	//13시 0분
	@Scheduled(cron = "0 2 22 * * *")
	public void syncUpcomingRaces() {
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
		} else if(now.getDayOfWeek() == DayOfWeek.SUNDAY || now.getDayOfWeek() == DayOfWeek.TUESDAY) {
			now = now.plusDays(5);
		} else {
			return;
		}
		String listPage = String.format("https://race.netkeiba.com/top/race_list.html?kaisai_date=%s", now.format(formatter));
		
		try {
        	driver.get(listPage);
            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
            List<WebElement> dataLists = driver.findElements(By.cssSelector(".RaceList_DataList"));
            List<String> raceLinks = new ArrayList<>();
            for(WebElement dataList : dataLists) {
            	List<WebElement> elements = dataList.findElements(By.cssSelector(".RaceList_DataItem a"));
            	for(WebElement element : elements)
            		if(element.getAttribute("href").contains("shutuba")) raceLinks.add(element.getAttribute("href"));
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
            	
            	//레이스 정보 스크래핑
            	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
            	document = documentUtil.replaceOrAddElement(document, "name", 
            			translateService.translate(TranslateDataType.RACE, raceNameAndGrade.getText()
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
                	} else if(gradeIconClasses.contains("Icon_GradeType3")) {
                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G3);
                	} else if(gradeIconClasses.contains("Icon_GradeType2")) {
                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G2);
                	} else if(gradeIconClasses.contains("Icon_GradeType1")) {
                		document = documentUtil.replaceOrAddElement(document, "grade", RaceGrade.G1);
                	}
            	}
            	
            	WebElement raceDataLine1 = driver.findElement(By.cssSelector(".RaceData01"));
            	//String startTime = raceDataLine1.getText();
            	String[] times = raceDataLine1.getText().substring(0, raceDataLine1.getText().indexOf("発走")).split(":");
            	if(times.length == 2) {
            		LocalDateTime startTime = LocalDateTime.now()
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
            	document = documentUtil.replaceOrAddElement(document, "distance", raceSpecs.replaceAll("[^0-9]", ""));
            	
            	List<WebElement> raceDataLine2Spans = driver.findElements(By.cssSelector(".RaceData02 span"));
            	for(int i = 0; i < raceDataLine2Spans.size(); i++) {
            		switch(i) {
	        			case 1: 
	        				document = documentUtil.replaceOrAddElement(document, "stadium", 
	        					translateService.translate(TranslateDataType.STADIUM, raceDataLine2Spans.get(i).getText(), false));
	        				break;
	        			case 3: 
	        				document = documentUtil.replaceOrAddElement(document, "ages", 
	        						raceDataLine2Spans.get(i).getText().replace("サラ系", "").replace("歳", "세").replace("以上", " 이상"));
	        				break;
	        			case 4: 
	        				document = documentUtil.replaceOrAddElement(document, "race_class", 
	        						raceDataLine2Spans.get(i).getText()
	        						.replace("歳", "세 ")
	        						.replace("新馬", "신마전")
	        						.replace("未勝利", "미승리전")
	        						.replace("オープン", "오픈")
	        						.replace("勝クラス", "승 클래스")
	        						.replace("万下", "만엔 이하"));
	        				break;
	        			case 7:
	        				try {
	        					document = documentUtil.replaceOrAddElement(document, "max_gates", 
		        					Integer.parseInt(raceDataLine2Spans.get(i).getText().replace("頭", "")));
	        				} catch(NumberFormatException e) {
	        					log.info("출주두수 Integer 변환에 실패했습니다.");
	        				}
	        				break;
	        			case 8: 
	        				String[] earningArray = raceDataLine2Spans.get(i).getText().replace("本賞金:", "").replace("万円", "").split("\\,");
	        				Document earnings = new Document();
	        				for(int place = 1; place <= earningArray.length; place++) {
	        					earnings.append(Integer.toString(place), earningArray[place - 1]);
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
	            				if(scrapperUtil.canConvertToInteger(wakuString)) {
                					listHorse.append("waku", Integer.parseInt(wakuString));
                				} else {
                					listHorse.append("waku", 0);
                				}
                				break;
                			case 1: 
                				String horseNumberString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToInteger(horseNumberString)) {
                					listHorse.append("horse_number", Integer.parseInt(horseNumberString));
                				} else {
                					listHorse.append("horse_number", 0);
                				}
                				break;
                			case 3: 
                				//horses에 넣을 데이터
                				Document horse = new Document();
                				Optional<Document> existingHorseId = horseService.findByOriginalId(horse.getString("original_id"));
                				if(existingHorseId.isPresent()) { 
                					listHorse.append("horse_id", existingHorseId);
                				} else {
                					String newUuid = UUID.randomUUID().toString();
                					horse.append("horse_id", newUuid);
                					listHorse.append("horse_id", newUuid);
                				}
                				
                				WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
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
                    					horse = documentUtil.replaceOrAddElement(horse, "translated_name", translated);
                    					horse = documentUtil.replaceOrAddElement(horse, "translated_by_machine", true);
                    					horse.append("translated_name", translated);
                    					horse.append("translated_by_machine", true);
                    					listHorse.append("translated_name",  translated);
                    				} catch(Exception e) {
                    					log.info(e.toString());
                    				}
                				}
                				horse = documentUtil.replaceOrAddElement(horse, "need_to_scrap", true);
                				horse = documentUtil.replaceOrAddElement(horse, "original_id", horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", ""));
                				horseService.saveDocs(horse);
                				break;
                			case 4: 
                				String sexAndAge = horseDatas.get(i).getText();
                				listHorse.append("gender", sexAndAge.substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마"));
                				String ageString = sexAndAge.substring(1);
                				if(scrapperUtil.canConvertToInteger(ageString)) {
                					listHorse.append("age", Integer.parseInt(ageString));
                				} else {
                					listHorse.append("age", 0);
                				}
                				break;
                			case 5: 
                				String loadString = horseDatas.get(i).getText();
                				if(scrapperUtil.canConvertToInteger(loadString)) {
                					listHorse.append("load_weight", Integer.parseInt(loadString));
                				} else {
                					listHorse.append("load_weight", 0);
                				}
                				break;
                			case 6: 
                				if(horseDatas.get(i).findElements(By.cssSelector("a")).size() > 0) {
                					listHorse.append("jockey", translateService.translate(TranslateDataType.JOCKEY, horseDatas.get(i).findElement(By.cssSelector("a")).getText(), true));
                				} else {
                					listHorse.append("jockey", "미정");
                				}
                				
                				break;
                			case 7: 
                				WebElement home = horseDatas.get(i);
                				listHorse.append("region", home.findElement(By.cssSelector("span")).getText().replace("美浦", "미호").replace("栗東", "릿토"));
                				listHorse.append("trainer", translateService.translate(TranslateDataType.TRAINER, home.findElement(By.cssSelector("a")).getText(), true));
                				break;
                			case 8: 
                				String weightAndChanges = horseDatas.get(i).getText();
                                if(weightAndChanges.trim().equals("計不")) {
                                	System.out.println("마체중: -"); 
                                } else {
                                	if(weightAndChanges.contains("(")) {
                                		String weightString = weightAndChanges.substring(0, weightAndChanges.indexOf("("));
                                		String weightChangeString = weightAndChanges.substring(weightAndChanges.indexOf("(") + 1, weightAndChanges.indexOf(")"));
                                		if(scrapperUtil.canConvertToInteger(weightString)) {
                        					listHorse.append("weight", Integer.parseInt(weightString));
                        				} else {
                        					listHorse.append("weight", 0);
                        				}
                                		if(scrapperUtil.canConvertToInteger(weightChangeString)) {
                        					listHorse.append("weight_change", Integer.parseInt(weightChangeString));
                        				} else {
                        					listHorse.append("weight_change", 0);
                        				}
                                	} else {
                                		if(scrapperUtil.canConvertToInteger(weightAndChanges)) {
                        					listHorse.append("weight", Integer.parseInt(weightAndChanges));
                        				} else {
                        					listHorse.append("weight", 0);
                        				}
                                	}
                                }
                				break;
                			case 9: 
                				String ownesString = horseDatas.get(i).findElement(By.cssSelector("span")).getText();
                				if(scrapperUtil.canConvertToDouble(ownesString)) {
                					listHorse.append("ownes", Double.parseDouble(ownesString));
                				} else {
                					listHorse.append("ownes", 0);
                				}
                				break;
                			case 10: 
                				String expectedString = horseDatas.get(i).findElement(By.cssSelector("span")).getText();
                				if(scrapperUtil.canConvertToInteger(expectedString)) {
                					listHorse.append("expected", Integer.parseInt(expectedString));
                				} else {
                					listHorse.append("expected", 0);
                				}
                				break;
                			default: break;
            			}
            		}
            		horses.add(listHorse);
            	}
            	document = documentUtil.replaceOrAddElement(document, "horses", horses);
            	document = documentUtil.replaceOrAddElement(document, "is_ended", false);
            	raceService.saveDocs(document);
            	Thread.sleep(3000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        driver.close();	//탭 닫기
        driver.quit();	//브라우저 닫기
	}
	
	//시작된지 10분 이상 지난 레이스를 찾아서 결과를 저장합니다.
	@Scheduled(cron = "0 29 22 * * *")
	public void syncEndedRace() {
		ObjectMapper objectMapper = new ObjectMapper();
		RestTemplate restTemplate = new RestTemplate();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Naver-Client-Id", translateClientId);
        headers.set("X-Naver-Client-Secret", translateClientSecret);
        
        String apiUrl = "https://openapi.naver.com/v1/papago/n2mt";
		
		LocalDateTime startTime = LocalDateTime.now();
		
		//테스트용 코드
		//startTime = startTime.minusDays(1);
		startTime = startTime.minusMinutes(10);
		startTime = startTime.minusSeconds(startTime.getSecond());
		startTime = startTime.minusNanos(startTime.getNano());
		MongoCursor<Document> endedRaces = raceService.getDocsByBsonFilter(Filters.and(
            Filters.gte("startTime", startTime), 
            Filters.eq("is_ended", false)
        ));
		
		while(endedRaces.hasNext()) {
			Document endedRace = endedRaces.next();
			try {
				//WebDriver driver = scrapperUtil.getChromeDriver();
				WebDriver driver = scrapperUtil.getEdgeDriver();
	        	driver.get("https://race.netkeiba.com/race/result.html?race_id=" + endedRace.getString("original_id"));
	            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
	        	
	        	//레이스 정보 스크래핑
	        	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
	        	endedRace = documentUtil.replaceOrAddElement(endedRace, "name", 
	        			translateService.translate(TranslateDataType.RACE, raceNameAndGrade.getText()
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
	            				if(scrapperUtil.canConvertToInteger(rposString)) {
                					listHorse.append("expected", Integer.parseInt(rposString));
                				} else {
                					listHorse.append("expected", 0);
                				}
	        				break;
	            			case 1: 
	            				String wakuString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToInteger(wakuString)) {
                					listHorse.append("waku", Integer.parseInt(wakuString));
                				} else {
                					listHorse.append("waku", 0);
                				}
	            				break;
	            			case 2: 
	            				String horseNumberString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToInteger(horseNumberString)) {
                					listHorse.append("horse_number", Integer.parseInt(horseNumberString));
                				} else {
                					listHorse.append("horse_number", 0);
                				}
	            				break;
	            			case 3: 
	            				Document horse = new Document();
                				Optional<Document> existingHorseId = horseService.findByOriginalId(horse.getString("original_id"));
                				if(existingHorseId.isPresent()) { 
                					listHorse.append("horse_id", existingHorseId);
                				} else {
                					String newUuid = UUID.randomUUID().toString();
                					horse.append("horse_id", newUuid);
                					listHorse.append("horse_id", newUuid);
                				}
                				
                				WebElement horseNameElement = horseDatas.get(i).findElement(By.cssSelector("a"));
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
                					log.info("주의: 미번역 데이터 있음");
                					TranslateDto translateDto = new TranslateDto("ja", "ko", horseNameString);
                    				HttpEntity<TranslateDto> translateEntity = new HttpEntity<>(translateDto, headers);
                    				try {
                    					JsonNode result = objectMapper.readTree(restTemplate.postForEntity(apiUrl, translateEntity, String.class).getBody());
                    					String translated = result.get("message").get("result").get("translatedText").textValue();
                    					//System.out.println("번역 결과");
                    					//System.out.println(result.toPrettyString());
                    					translateService.insertTranslateData(TranslateDataType.HORSE, horseNameString, translated, true);
                    					horse = documentUtil.replaceOrAddElement(horse, "translated_name", translated);
                    					horse = documentUtil.replaceOrAddElement(horse, "translated_by_machine", true);
                    					horse.append("translated_name", translated);
                    					horse.append("translated_by_machine", true);
                    					listHorse.append("translated_name",  translated);
                    				} catch(Exception e) {
                    					log.info(e.toString());
                    				}
                				}
                				horse = documentUtil.replaceOrAddElement(horse, "need_to_scrap", true);
                				horse = documentUtil.replaceOrAddElement(horse, "original_id", horseNameElement.getAttribute("href").replace("https://db.netkeiba.com/horse/", ""));
                				horseService.saveDocs(horse);
	            				break;
	            			case 4: 
	            				String sexAndAge = horseDatas.get(i).getText();
                				listHorse.append("gender", sexAndAge.substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마"));
                				String ageString = sexAndAge.substring(1);
	            				if(scrapperUtil.canConvertToInteger(ageString)) {
                					listHorse.append("age", Integer.parseInt(ageString));
                				} else {
                					listHorse.append("age", 0);
                				}
	            				break;
	            			case 5: 
	            				String loadWeightString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToDouble(loadWeightString)) {
                					listHorse.append("load_weight", Double.parseDouble(loadWeightString));
                				} else {
                					listHorse.append("load_weight", 0);
                				}
	            				break;
	            			case 6: 
	            				listHorse.append("jockey", translateService.translate(TranslateDataType.JOCKEY, horseDatas.get(i).getText(), true));
	            				break;
	            			case 7: 
	            				listHorse.append("time", horseDatas.get(i).getText());
	            				break;
	            			case 8: 
	            				listHorse.append("interval", horseDatas.get(i).getText());
	            				break;
	            			case 9: 
	            				String expectedString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToInteger(expectedString)) {
                					listHorse.append("expected", Integer.parseInt(expectedString));
                				} else {
                					listHorse.append("expected", 0);
                				}
	            				System.out.println("인기: " + horseDatas.get(i).getText());
	            				break;
	            			case 10: 
	            				String ownesString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToInteger(ownesString)) {
                					listHorse.append("ownes", Integer.parseInt(ownesString));
                				} else {
                					listHorse.append("ownes", 0);
                				}
	            				break;
	            			case 11: 
	            				String last3fString = horseDatas.get(i).getText();
	            				if(scrapperUtil.canConvertToDouble(last3fString)) {
                					listHorse.append("last_3f", Double.parseDouble(last3fString));
                				} else {
                					listHorse.append("last_3f", 0);
                				}
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
                                	System.out.println("마체중: -"); 
                                } else {
                                	if(weightAndChanges.contains("(")) {
                                		String weightString = weightAndChanges.substring(0, weightAndChanges.indexOf("("));
                                		String weightChangeString = weightAndChanges.substring(weightAndChanges.indexOf("(") + 1, weightAndChanges.indexOf(")"));
                                		if(scrapperUtil.canConvertToInteger(weightString)) {
                        					listHorse.append("weight", Integer.parseInt(weightString));
                        				} else {
                        					listHorse.append("weight", 0);
                        				}
                                		if(scrapperUtil.canConvertToInteger(weightChangeString)) {
                        					listHorse.append("weight_change", Integer.parseInt(weightChangeString));
                        				} else {
                        					listHorse.append("weight_change", 0);
                        				}
                                	} else {
                                		if(scrapperUtil.canConvertToInteger(weightAndChanges)) {
                        					listHorse.append("weight", Integer.parseInt(weightAndChanges));
                        				} else {
                        					listHorse.append("weight", 0);
                        				}
                                	}
                                }
	            				break;
	            			default: break;
	        			}
	        		}
	        	}
	        	
	        	endedRace = documentUtil.replaceOrAddElement(endedRace, "horses", horses);
	        	endedRace = documentUtil.replaceOrAddElement(endedRace, "is_ended", false);
	        	raceService.saveDocs(endedRace);
	        	
	        	driver.close();	//탭 닫기
		        driver.quit();	//브라우저 닫기
		        Thread.sleep(3000);
	            
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
			
	        
		}
		
		
	}
	
	@Scheduled(cron = "0 0 0 * * *")
	public void syncHorseDataDetail() {
		
		Map<String, Object> conditions = new HashMap<>();
		conditions.put("need_to_scrap", true);
		
		MongoCursor<Document> horseDatas = horseService.getDocsByConditions(conditions);
		while(horseDatas.hasNext()) {
			Document horseData = horseDatas.next();
			WebDriver driver = scrapperUtil.getEdgeDriver();
			try {
	        	driver.get("https://db.netkeiba.com/horse/" + horseData.getString("original_id"));
	            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
	            
	            //경주마 프로필
	            WebElement horseTitle = driver.findElement(By.cssSelector(".horse_title"));
	            horseData = documentUtil.replaceOrAddElement(horseData, "english_name", horseTitle.findElement(By.cssSelector(".eng_name a")).getText().trim());
	            String[] horseBaseDatas = horseTitle.findElement(By.cssSelector(".txt_01")).getText().split("　");
				horseData = documentUtil.replaceOrAddElement(horseData, "gender", horseBaseDatas[1].trim().substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마"));
				String ageString = horseBaseDatas[1].trim().substring(1);
				if(scrapperUtil.canConvertToInteger(ageString)) {
					horseData = documentUtil.replaceOrAddElement(horseData, "age", Integer.parseInt(ageString.replace("歳", "세")));
				} else {
					horseData = documentUtil.replaceOrAddElement(horseData, "age", 0);
				}
				
	            
	            List<WebElement> horseProps = driver.findElements(By.cssSelector(".db_prof_table tbody tr"));
	            for(WebElement horseProp : horseProps) {
	            	String header = horseProp.findElement(By.cssSelector("th")).getText().trim();
	            	if(header.equals("生年月日")) {
	            		String pattern = "yyyy-MM-dd";
	            		String birthdayString = horseProp.findElement(By.cssSelector("td")).getText().replace("年", "-").replace("月", "-").replace("日", "").trim();
	            		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
	                    LocalDate birthday = LocalDate.parse(birthdayString, formatter);
	            		horseData = documentUtil.replaceOrAddElement(horseData, "birthday", birthday);
	            	} else if(header.equals("調教師")) {
	            		String trainerAndRegion = horseProp.findElement(By.cssSelector("td")).getText();
	                    if(trainerAndRegion.contains("(美浦)")) horseData = documentUtil.replaceOrAddElement(horseData, "region", "미호");
	                    else if(trainerAndRegion.contains("(栗東)")) horseData = documentUtil.replaceOrAddElement(horseData, "region", "릿토"); 
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
	            		horseData = documentUtil.replaceOrAddElement(horseData, "sale_price", horseProp.findElement(By.cssSelector("td")).getText()); 
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
	            List<WebElement> raceResults = driver.findElements(By.cssSelector(".db_h_race_results tbody tr"));
	            for(WebElement raceResult : raceResults) {
	            	List<WebElement> raceResultProperties = raceResult.findElements(By.cssSelector("td"));
	            	System.out.println("경기개최일: " + raceResultProperties.get(0).findElement(By.cssSelector("a")).getText().replace("/", "-")); 
	            	String stadiumRaw = raceResultProperties.get(1).findElement(By.cssSelector("a")).getText();
	            	System.out.println("경기장: " + translateService.translate(TranslateDataType.STADIUM, stadiumRaw.replaceAll("[0-9]", ""), false));
					System.out.println("날씨: " + translateService.translate(TranslateDataType.WEATHER, raceResultProperties.get(2).getText(), false));
	                System.out.println("라운드: " + raceResultProperties.get(3).getText()); 
	                String raceString = raceResultProperties.get(4).getText();
	                if(raceString.contains("(")) {
	            		System.out.println("경주명:" +
	            			translateService.translate(TranslateDataType.RACE, raceString.substring(0, raceString.indexOf("(")), true)
	            			.replace("歳", "세 ")
							.replace("新馬", "신마전")
							.replace("未勝利", "미승리전")
							.replace("オープン", "오픈")
							.replace("勝クラス", "승 클래스")
							.replace("万下", "만엔 이하"));
	            		System.out.println("그레이드: " + raceString.substring(raceString.indexOf("(") + 1, raceString.indexOf(")")));
	            	} else {
	            		System.out.println("경주명: " + 
	            			translateService.translate(TranslateDataType.RACE, raceString, true)
	            			.replace("歳", "세 ")
							.replace("新馬", "신마전")
							.replace("未勝利", "미승리전")
							.replace("オープン", "오픈")
							.replace("勝クラス", "승 클래스")
							.replace("万下", "만엔 이하"));
	            	}
	                System.out.println("두수: " + raceResultProperties.get(6).getText()); 
	                System.out.println("와꾸: " + raceResultProperties.get(7).getText()); 
	                System.out.println("마번: " + raceResultProperties.get(8).getText()); 
	                System.out.println("배당: " + raceResultProperties.get(9).getText()); 
	                System.out.println("인기: " + raceResultProperties.get(10).getText()); 
	                System.out.println("착순: " + raceResultProperties.get(11).getText()); 
	                System.out.println("기수: " + translateService.translate(TranslateDataType.JOCKEY, raceResultProperties.get(12).getText(), true));
	                System.out.println("근량: " + raceResultProperties.get(13).getText()); 
	                
	                String trackRaw = raceResultProperties.get(14).getText().replaceAll("[0-9]", "");
	                String distance = raceResultProperties.get(14).getText().replaceAll("[^0-9]", "");
	                System.out.println("트랙: " + translateService.translate(TranslateDataType.TRACK, trackRaw, false));
					System.out.println("거리: " + distance);
					
					System.out.println("마장상태: " + translateService.translate(TranslateDataType.CONDITION, raceResultProperties.get(15).getText(), true));
	                System.out.println("기록: " + raceResultProperties.get(17).getText()); 
	                System.out.println("착차: " + raceResultProperties.get(18).getText()); 
	                System.out.println("코너통과순위: " + raceResultProperties.get(20).getText()); 
	                System.out.println("라스트3F: " + raceResultProperties.get(22).getText()); 
	                String weightString = raceResultProperties.get(23).getText();
	                if(weightString.trim().equals("計不")) {
	                	System.out.println("마체중: -"); 
	                } else {
	                	if(weightString.contains("(")) {
	                		System.out.println("마체중: " + weightString.substring(0, weightString.indexOf("(")));
	                		System.out.println("증감: " + weightString.substring(weightString.indexOf("(") + 1, weightString.indexOf(")")));
	                	} else {
	                		System.out.println("마체중: " + weightString);
	                	}
	                }
	                System.out.println("마체중: " + raceResultProperties.get(23).getText()); 
	                System.out.println("우승마(2착마): " + raceResultProperties.get(26).getText()); 
	            }
	            
	            
	            //혈통표
	            driver.navigate().to("https://db.netkeiba.com/horse/ped/2012102013/");
	            Thread.sleep(3000);
	            List<WebElement> bloodLine = driver.findElements(By.cssSelector(".blood_table tr"));
	            
	            //부계 혈통
	            List<WebElement> maleLine1 = bloodLine.get(0).findElements(By.cssSelector(".b_ml"));
	            System.out.println("부: " + translateService.translateJapaneseOnly(
	            		TranslateDataType.STALION, maleLine1.get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("조부: " + translateService.translateJapaneseOnly(TranslateDataType.STALION, 
	            		maleLine1.get(1).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("증조부1: " + translateService.translateJapaneseOnly(TranslateDataType.STALION, 
	            		maleLine1.get(2).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("증조부2: " + translateService.translateJapaneseOnly(TranslateDataType.STALION, 
	            		bloodLine.get(8).findElements(By.cssSelector(".b_ml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("조모: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(8).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("증조모1: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(4).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("증조모2: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(12).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            
	            //모계 혈통
	            List<WebElement> maleLine2 = bloodLine.get(16).findElements(By.cssSelector(".b_ml"));
	            System.out.println("모: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(16).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("외조부: " + translateService.translateJapaneseOnly(TranslateDataType.STALION, 
	            		maleLine2.get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("외증조부1: " + translateService.translateJapaneseOnly(TranslateDataType.STALION, 
	            		maleLine2.get(1).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("외증조부2: " + translateService.translateJapaneseOnly(TranslateDataType.STALION, 
	            		bloodLine.get(24).findElements(By.cssSelector(".b_ml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("외조모: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(24).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("외증조모1: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(20).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            System.out.println("외증조모2: " + translateService.translateJapaneseOnly(TranslateDataType.MARE, 
	            		bloodLine.get(28).findElements(By.cssSelector(".b_fml")).get(0).findElements(By.cssSelector("a")).get(0).getText()));
	            Thread.sleep(1000);
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }

	        driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
		}
		
		
	}
	/*
	@Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        //최대 29  + 1 = 30개
        taskScheduler.setPoolSize(14); // 스레드 풀 크기 설정
        taskScheduler.setThreadNamePrefix("task-");
        return taskScheduler;
    }

    @Bean
    public TaskSchedulerCustomizer taskSchedulerCustomizer(ThreadPoolTaskScheduler taskScheduler) {
        return taskScheduler1 -> {
            taskScheduler1.setPoolSize(14); // 스레드 풀 크기 설정
            taskScheduler1.setThreadNamePrefix("task-");
        };
    }*/
}
