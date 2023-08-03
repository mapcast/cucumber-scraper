package com.cucumber.keiba.scrapper.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cucumber.keiba.scrapper.model.TranslateData;
import com.cucumber.keiba.scrapper.model.TranslateDataType;
import com.cucumber.keiba.scrapper.service.user.TranslateService;
import com.cucumber.keiba.scrapper.util.WebScrapperUtil;
import com.google.common.base.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@Component
@RequiredArgsConstructor
public class WebScrappingScheduler {
	
	private final WebScrapperUtil scrapperUtil;
	
	private final TranslateService translateService;
		
	@Scheduled(cron = "0 9 2 * * *")
	public void syncEventsInplay() {
		WebDriver driver = scrapperUtil.getChromeDriver();
		try {
        	driver.get("https://race.netkeiba.com/top/race_list.html?kaisai_date=20230805");
            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.

            List<WebElement> dataLists = driver.findElements(By.cssSelector(".RaceList_DataList"));
            List<String> raceLinks = new ArrayList<>();
            for(WebElement dataList : dataLists) {
            	List<WebElement> elements = dataList.findElements(By.cssSelector(".RaceList_DataItem a"));
            	for(WebElement element : elements)
            		if(element.getAttribute("href").contains("shutuba")) raceLinks.add(element.getAttribute("href"));
            }
            for(String raceLink : raceLinks) {
            	driver.navigate().to(raceLink);
            	
            	System.out.println("레이스 정보 링크: " + raceLink);
            	
            	//레이스 정보 스크래핑
            	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
            	System.out.println("레이스 명: " + translateService.translateContains(TranslateDataType.RACE, raceNameAndGrade.getText().replace("\"", "")));
            	
            	
            	if(raceNameAndGrade.findElements(By.cssSelector("span")).size() > 0) {
            		String gradeIconClasses = raceNameAndGrade.findElement(By.cssSelector("span")).getAttribute("class");
            		if(gradeIconClasses.contains("Icon_GradeType18")) {
            			System.out.println("그레이드: 1승 클래스");
                	} else if(gradeIconClasses.contains("Icon_GradeType17")) {
                		System.out.println("그레이드: 2승 클래스");
                	} else if(gradeIconClasses.contains("Icon_GradeType16")) {
                		System.out.println("그레이드: 3승 클래스");
                	} else if(gradeIconClasses.contains("Icon_GradeType15")) {
                		System.out.println("그레이드: 리스티드");
                	} else if(gradeIconClasses.contains("Icon_GradeType5")) {
                		System.out.println("그레이드: 오픈");
                	} else if(gradeIconClasses.contains("Icon_GradeType3")) {
                		System.out.println("그레이드: G3");
                	} else if(gradeIconClasses.contains("Icon_GradeType2")) {
                		System.out.println("그레이드: G2");
                	} else if(gradeIconClasses.contains("Icon_GradeType1")) {
                		System.out.println("그레이드: G1");
                	}
            	}
            	
            	WebElement raceDataLine1 = driver.findElement(By.cssSelector(".RaceData01"));
            	System.out.println("파싱 테스트: " + raceDataLine1.getText());
            	System.out.println("마장/거리: " + raceDataLine1.findElement(By.cssSelector("span")).getText());
            	
            	List<WebElement> raceDataLine2Spans = driver.findElements(By.cssSelector(".RaceData02 span"));
            	for(int i = 0; i < raceDataLine2Spans.size(); i++) {
            		switch(i) {
	            		case 0: 
	            			System.out.println("회수: " + raceDataLine2Spans.get(i).getText());
	            			break;
	        			case 1: 
	        				System.out.println("경기장: " +  translateService.translate(TranslateDataType.STADIUM, raceDataLine2Spans.get(i).getText()));
	        				break;
	        			case 2: 
	        				System.out.println("회차: " + raceDataLine2Spans.get(i).getText());
	        				break;
	        			case 3: 
	        				System.out.println("마령/조건: " + raceDataLine2Spans.get(i).getText().replace("歳", "세").replace("以上", " 이상"));
	        				break;
	        			case 4: 
	        				System.out.println("클래스: " + 
	        						raceDataLine2Spans.get(i).getText()
	        						.replace("新馬", "신마")
	        						.replace("未勝利", "미승리")
	        						.replace("オープン", "오픈")
	        						.replace("勝クラス", "승 클래스"));
	        				break;
	        			case 6: 
	        				System.out.println("핸디캡: " + 
	        						raceDataLine2Spans.get(i).getText()
	        						.replace("定量", "정량")
	        						.replace("ハンデ", "핸디캡")
	        						.replace("馬齢", "마령"));
	        				break;
	        			case 7:
	        				System.out.println("출주두수: " + raceDataLine2Spans.get(i).getText().replace("頭", ""));
	        				break;
	        			case 8: 
	        				String[] earns = raceDataLine2Spans.get(i).getText().replace("本賞金:", "").replace("万円", "").split("\\,");
	        				for(int place = 1; place <= earns.length; place++) {
	        					System.out.println(place + "착 상금: " + earns[place - 1] + "만엔");
	        				}
	        				break;
            		}
            	}
            	
            	
            	
            	Thread.sleep(3000);
            	//출주마 목록 스크래핑
            	List<WebElement> horses = driver.findElements(By.cssSelector(".HorseList"));
            	for(WebElement horse : horses) {
            		List<WebElement> horseDatas = horse.findElements(By.cssSelector("td"));
            		for(int i = 0; i < horseDatas.size(); i++) {
            			switch(i) {
                			case 0: 
                				if(horseDatas.get(i).findElements(By.cssSelector("div")).size() > 0) 
                					System.out.println("와꾸: " + horseDatas.get(i).findElement(By.cssSelector("div")).getText());
                				break;
                			case 1: 
                				if(horseDatas.get(i).findElements(By.cssSelector("div")).size() > 0) 
                					System.out.println("마번: " + horseDatas.get(i).findElement(By.cssSelector("div")).getText());
                				break;
                			case 3: 
                				WebElement horseName = horseDatas.get(i).findElement(By.cssSelector("a"));
                				System.out.println("출주마 정보 링크: " + horseName.getAttribute("href"));
                				System.out.println("마명: " + horseName.getText().replace("\"", ""));
                				break;
                			case 4: 
                				String sexAndAge = horseDatas.get(i).getText();
                				System.out.println("성별: " + sexAndAge.substring(0, 1).replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마"));
                				System.out.println("나이: " + sexAndAge.substring(1));
                				break;
                			case 5: 
                				System.out.println("근량: " + horseDatas.get(i).getText());
                				break;
                			case 6: 
                				System.out.println("기수: " + translateService.translateContains(TranslateDataType.JOCKEY, horseDatas.get(i).findElement(By.cssSelector("a")).getText()));
                				break;
                			case 7: 
                				WebElement home = horseDatas.get(i);
                				System.out.println("지역: " + home.findElement(By.cssSelector("span")).getText().replace("美浦", "미호").replace("栗東", "릿토"));
                				System.out.println("구사: " + home.findElement(By.cssSelector("a")).getText());
                				break;
                			case 8: 
                				System.out.println("마체중: " + horseDatas.get(i).getText());
                				if(horseDatas.get(i).findElements(By.cssSelector("small")).size() > 0)
                					System.out.println("증감: " + horseDatas.get(i).findElement(By.cssSelector("small")).getText());
                				break;
                			case 9: 
                				System.out.println("예상배당: " + horseDatas.get(i).findElement(By.cssSelector("span")).getText());
                				break;
                			case 10: 
                				System.out.println("인기: " + horseDatas.get(i).findElement(By.cssSelector("span")).getText());
                				break;
                			default: break;
            			}
            		}
            	}
            	Thread.sleep(3000);
            }
            
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        driver.close();	//탭 닫기
        driver.quit();	//브라우저 닫기
	}
	
	@Scheduled(cron = "0 5 4 * * *")
	public void syncHorseDataDetail() {
		WebDriver driver = scrapperUtil.getChromeDriver();
		try {
        	driver.get("https://db.netkeiba.com/horse/2012102013/");
            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.
            
            //경주마 프로필
            WebElement horseTitle = driver.findElement(By.cssSelector(".horse_title"));
            System.out.println("영문명: " + horseTitle.findElement(By.cssSelector(".eng_name a")).getText());
            String[] horseBaseDatas = horseTitle.findElement(By.cssSelector(".txt_01")).getText().split("　");
            System.out.println("등록 상태: " + horseBaseDatas[0].replace("現役", "현역").replace("抹消", "말소"));
            System.out.println("성별/나이: " + horseBaseDatas[1].replace("牡", "숫말").replace("牝", "암말").replace("騸", "거세마"));
            System.out.println("털 색: " + translateService.translate(TranslateDataType.COLOR, horseBaseDatas[2]));
            
            List<WebElement> horseProperties = driver.findElements(By.cssSelector(".db_prof_table tbody tr"));
            System.out.println("생년월일: " + horseProperties.get(0).findElement(By.cssSelector("td")).getText()); 	
            System.out.println("산지: " + horseProperties.get(4).findElement(By.cssSelector("td")).getText());
            System.out.println("경매가: " + horseProperties.get(5).findElement(By.cssSelector("td")).getText());
            System.out.println("수득상금: " + horseProperties.get(6).findElement(By.cssSelector("td")).getText());
            System.out.println("전적: " + horseProperties.get(7).findElement(By.cssSelector("td")).getText().replace("[", "").replace("]", ""));
            System.out.println("전적(착순): " + horseProperties.get(7).findElement(By.cssSelector("td a")).getText());
            
            //출주 기록
            List<WebElement> raceResults = driver.findElements(By.cssSelector(".db_h_race_results tbody tr"));
            for(WebElement raceResult : raceResults) {
            	List<WebElement> raceResultProperties = raceResult.findElements(By.cssSelector("td"));
            	System.out.println("경기개최일: " + raceResultProperties.get(0).findElement(By.cssSelector("a")).getText()); 
            	String stadiumRaw = raceResultProperties.get(1).findElement(By.cssSelector("a")).getText();
            	System.out.println("경기장: " + translateService.translate(TranslateDataType.STADIUM, stadiumRaw.substring(1, stadiumRaw.length() - 2)));
				System.out.println("날씨: " + translateService.translate(TranslateDataType.WEATHER, raceResultProperties.get(2).getText()));
                System.out.println("라운드: " + raceResultProperties.get(3).getText()); 
                
                System.out.println("경주명: " + 
                		translateService.translateContains(TranslateDataType.RACE, 
                				raceResultProperties.get(4).getText()
                				.replace("(G1)", "")
		                		.replace("(G2)", "")
								.replace("(G3)", ""))
						.replace("新馬", "신마")
						.replace("未勝利", "미승리")
						.replace("オープン", "오픈")
						.replace("勝クラス", "승 클래스"));
				
                System.out.println("두수: " + raceResultProperties.get(6).getText()); 
                System.out.println("와꾸: " + raceResultProperties.get(7).getText()); 
                System.out.println("마번: " + raceResultProperties.get(8).getText()); 
                System.out.println("배당: " + raceResultProperties.get(9).getText()); 
                System.out.println("인기: " + raceResultProperties.get(10).getText()); 
                System.out.println("착순: " + raceResultProperties.get(11).getText()); 
                
                System.out.println("기수: " + translateService.translateContains(TranslateDataType.JOCKEY, raceResultProperties.get(12).getText()));
                System.out.println("근량: " + raceResultProperties.get(13).getText()); 
                
                String trackRaw = raceResultProperties.get(14).getText().replaceAll("[0-9]", "");
                String distance = raceResultProperties.get(14).getText().replaceAll("[^0-9]", "");
                System.out.println("트랙: " + translateService.translate(TranslateDataType.TRACK, trackRaw));
				System.out.println("거리: " + distance);
				
				System.out.println("마장상태: " + translateService.translate(TranslateDataType.CONDITION, raceResultProperties.get(15).getText()));
                System.out.println("기록: " + raceResultProperties.get(17).getText()); 
                System.out.println("착차: " + raceResultProperties.get(18).getText()); 
                System.out.println("코너통과순위: " + raceResultProperties.get(20).getText()); 
                System.out.println("라스트3F: " + raceResultProperties.get(22).getText()); 
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
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        driver.close();	//탭 닫기
        driver.quit();	//브라우저 닫기
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
