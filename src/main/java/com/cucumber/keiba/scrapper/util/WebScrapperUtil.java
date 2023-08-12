package com.cucumber.keiba.scrapper.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.springframework.stereotype.Component;

@Component
public class WebScrapperUtil {
	
	private Map<String, Boolean> driverRunningMap = new HashMap<>();
	
	public boolean isDriverIsRunning(String name) {
        if (!driverRunningMap.containsKey(name)) {
            return false;
        } else {
        	return driverRunningMap.get(name);
        }
    }
	
	public void setIsDriverIsRunning(String name, boolean isRunning) {
		driverRunningMap.put(name, isRunning);
	}
		
	public WebDriver getChromeDriver() {
		Path path = Paths.get("C:\\webdriver\\chromedriver.exe");
        System.setProperty("webdriver.chrome.driver", path.toString());
		
		ChromeOptions chromeOptions = new ChromeOptions();
    	//chromeOptions.setHeadless(true);
		chromeOptions.addArguments("--remote-allow-origins=*");
    	chromeOptions.addArguments("--lang=ko");
    	chromeOptions.addArguments("--no-sandbox");
    	chromeOptions.addArguments("--disable-dev-shm-usage");
    	chromeOptions.addArguments("--disable-gpu");
    	chromeOptions.addArguments("--blink-settings=imagesEnabled=false");
    	chromeOptions.addArguments("--mute-audio");
    	chromeOptions.addArguments("incognito");
    	chromeOptions.setCapability("ignoreProtectedModeSettings", true);
    	
        WebDriver driver = new ChromeDriver(chromeOptions);
    	driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
	}
	
	public WebDriver getEdgeDriver() {
		Path path = Paths.get("C:\\webdriver\\msedgedriver.exe");
        System.setProperty("webdriver.edge.driver", path.toString());
        
        EdgeOptions edgeOptions = new EdgeOptions();
        edgeOptions.setCapability("ignoreZoomSetting", true);
        edgeOptions.addArguments("--remote-allow-origins=*");
    	
        WebDriver driver = new EdgeDriver(edgeOptions);
    	driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
	}
	
	
}
