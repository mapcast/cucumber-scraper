package com.cucumber.keiba.scrapper.scheduler;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.cucumber.keiba.scrapper.service.horse.HorseService;
import com.cucumber.keiba.scrapper.service.leading.LeadingService;
import com.cucumber.keiba.scrapper.service.race.RaceService;
import com.cucumber.keiba.scrapper.service.translate.TranslateService;
import com.cucumber.keiba.scrapper.util.DocumentUtil;
import com.cucumber.keiba.scrapper.util.ParseSeleniumUtil;
import com.cucumber.keiba.scrapper.util.WebScrapperUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCursor;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

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
	
	private final ParseSeleniumUtil parseSeleniumUtil;
	
	@Value("${translate.client-id}")
	private String translateClientId;
	
	@Value("${translate.client-secret}")
	private String translateClientSecret;
	
	@Scheduled(cron = "0 36 20 * * *")
	public void syncNextDayRaces() {
		String driverName = "nextWeekRaceDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			//경기 일주일 전 데이터 스크래핑
			LocalDate now = LocalDate.now();
			now = now.plusDays(7);
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
	            parseSeleniumUtil.parseUpcomingRaces(raceService, horseService, translateService, now, raceLinks, driver);
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
		            Thread.sleep(1000); //브라우저 로딩될때까지 잠시 기다린다.
		            parseSeleniumUtil.scrapEndedRaceData(raceService, horseService, translateService, driver, endedRace, apiUrl, objectMapper, restTemplate, headers);
		        	
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
			}
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
	        
		}
	}
	
	@Scheduled(cron = "0 0/3 * * * *")
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
		        	parseSeleniumUtil.scrapEndedRaceData(raceService, horseService, translateService, driver, raceData, apiUrl, objectMapper, restTemplate, headers);
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
		        	parseSeleniumUtil.scrapHorseData(raceService, horseService, translateService, driver, horseData, false);
		            
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
	/*
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
	*/
	@Scheduled(cron = "0 0/5 * * * *")
	public void syncRaceEndedHorseDataDetail() {
		String driverName = "raceEndedHorseDetailScrapDriver";
		if(!scrapperUtil.isDriverIsRunning(driverName)) {
			LocalDateTime searchTime = LocalDateTime.now().minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
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
		        	parseSeleniumUtil.scrapHorseData(raceService, horseService, translateService, driver, horseData, true);
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
			}
			
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
	@Scheduled(cron = "0 0/3 * * * *")
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
		            parseSeleniumUtil.scrapHorseData(raceService, horseService, translateService, driver, horseData, true);
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
			}
			
			driver.close();	//탭 닫기
	        driver.quit();	//브라우저 닫기
	        scrapperUtil.setIsDriverIsRunning(driverName, false);
		}
	}
	
	@Scheduled(cron = "0 42 3 * * *")
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
	
	@Scheduled(cron = "0 15 19 * * *")
	public void leadingScheduler() {
		//매주 월요일 밤에 실행
		LocalDateTime now = LocalDateTime.now();
		if(now.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
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
        taskScheduler.setPoolSize(4); // 스레드 풀 크기 설정
        taskScheduler.setThreadNamePrefix("task-");
        return taskScheduler;
    }

    @Bean
    public TaskSchedulerCustomizer taskSchedulerCustomizer(ThreadPoolTaskScheduler taskScheduler) {
        return taskScheduler1 -> {
            taskScheduler1.setPoolSize(4); // 스레드 풀 크기 설정
            taskScheduler1.setThreadNamePrefix("task-");
        };
    }
}
