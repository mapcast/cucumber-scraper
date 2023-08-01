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

import com.cucumber.keiba.scrapper.util.WebScrapperUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@Component
@RequiredArgsConstructor
public class WebScrappingScheduler {
	
	private final WebScrapperUtil scrapperUtil;
		
	@Scheduled(cron = "0 0 7 * * *")
	public void syncEventsInplay() {
		WebDriver driver = scrapperUtil.getChromeDriver();
		try {
        	driver.get("https://race.netkeiba.com/top/race_list.html?kaisai_date=20230805");
            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.

            //List<WebElement> elements = driver.findElements(By.cssSelector(".Horse_Name a span"));
            List<WebElement> dataLists = driver.findElements(By.cssSelector(".RaceList_DataList"));
            //Map<String, Integer> raceMap = new HashMap<>();
            List<String> raceLinks = new ArrayList<>();
            for(WebElement dataList : dataLists) {
            	List<WebElement> elements = dataList.findElements(By.cssSelector(".RaceList_DataItem a"));
            	int count = 0;
            	for(WebElement element : elements)
            		if(element.getAttribute("href").contains("shutuba")) raceLinks.add(element.getAttribute("href"));
            	//raceMap.put(elements.get(0).getAttribute("href"), count);
            }
            for(String raceLink : raceLinks) {
            	driver.navigate().to(raceLink);
            	
            	System.out.println("레이스 정보 링크: " + raceLink);
            	
            	//레이스 정보 스크래핑
            	WebElement raceNameAndGrade = driver.findElement(By.cssSelector(".RaceName"));
            	String raceName = raceNameAndGrade.getText().replace("\"", "");
            	System.out.println("레이스 명: " + raceName);
            	
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
        				System.out.println("경기장: " + raceDataLine2Spans.get(i).getText());
        				break;
        			case 2: 
        				System.out.println("회차: " + raceDataLine2Spans.get(i).getText());
        				break;
        			case 3: 
        				System.out.println("마령/조건: " + raceDataLine2Spans.get(i).getText());
        				break;
        			case 4: 
        				System.out.println("클래스: " + raceDataLine2Spans.get(i).getText());
        				break;
        			case 6: 
        				System.out.println("핸디캡: " + raceDataLine2Spans.get(i).getText());
        				break;
        			case 7:
        				System.out.println("출주두수: " + raceDataLine2Spans.get(i).getText());
        				break;
        			case 8: 
        				System.out.println("본상금: " + raceDataLine2Spans.get(i).getText());
        				break;
            		}
            	}
            	
            	
            	
            	Thread.sleep(5000);
            	//출주마 목록 스크래핑
            	List<WebElement> horses = driver.findElements(By.cssSelector(".HorseList"));
            	for(WebElement horse : horses) {
            		List<WebElement> horseDatas = horse.findElements(By.cssSelector("td"));
            		for(int i = 0; i < horseDatas.size(); i++) {
            			switch(i) {
                			case 0: break;
                			case 1: break;
                			case 3: 
                				WebElement horseName = horseDatas.get(i).findElement(By.cssSelector("a"));
                				System.out.println("출주마 정보 링크: " + horseName.getAttribute("href"));
                				System.out.println("마명: " + horseName.getText().replace("\"", ""));
                				break;
                			case 4: 
                				System.out.println("성별/나이: " + horseDatas.get(i).getText());
                				break;
                			case 5: 
                				System.out.println("근량: " + horseDatas.get(i).getText());
                				break;
                			case 6: 
                				System.out.println("기수: " + horseDatas.get(i).findElement(By.cssSelector("a")).getText());
                				break;
                			case 7: 
                				WebElement home = horseDatas.get(i);
                				System.out.println("지역: " + home.findElement(By.cssSelector("span")).getText());
                				System.out.println("구사: " + home.findElement(By.cssSelector("a")).getText());
                				break;
                			case 8: 
                				//System.out.println("마체중: " + horseDatas.get(j).getText());
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
            	/*
            	System.out.println("레이스 수: " + raceMap.get(firstRaceLink));
                for(int i = 1; i <= 12; i++) {
                	String raceNumber = Integer.toString(i);
                	if(raceNumber.length() == 1) raceNumber = "0" + raceNumber;
                    String modified = firstRaceLink.substring(0, firstRaceLink.length() - 15) + raceNumber + "&rf=race_list";
                	driver.navigate().to(modified);
                }
                */
            }
            
            
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
