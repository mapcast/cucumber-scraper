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
		
	@Scheduled(cron = "0 19 21 * * *")
	public void syncEventsInplay() {
		WebDriver driver = scrapperUtil.getChromeDriver();
		try {
        	driver.get("https://race.netkeiba.com/top/race_list.html?kaisai_date=20230730");
            Thread.sleep(3000); //브라우저 로딩될때까지 잠시 기다린다.

            //List<WebElement> elements = driver.findElements(By.cssSelector(".Horse_Name a span"));
            List<WebElement> dataLists = driver.findElements(By.cssSelector(".RaceList_DataList"));
            Map<String, Integer> raceMap = new HashMap<>();
            for(WebElement dataList : dataLists) {
            	List<WebElement> elements = dataList.findElements(By.cssSelector(".RaceList_DataItem a"));
            	int count = 0;
            	for(WebElement element : elements)
            		if(element.getAttribute("href").contains("result")) count++;
            	raceMap.put(elements.get(0).getAttribute("href"), count);
            }
            for(String firstRaceLink : raceMap.keySet()) {
            	System.out.println("레이스 수: " + raceMap.get(firstRaceLink));
                for(int i = 1; i <= raceMap.get(firstRaceLink); i++) {
                	String raceNumber = Integer.toString(i);
                	if(raceNumber.length() == 1) raceNumber = "0" + raceNumber;
                    String modified = firstRaceLink.substring(0, firstRaceLink.length() - 15) + raceNumber + "&rf=race_list";
                	driver.navigate().to(modified);
                    Thread.sleep(2000);
                }
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
