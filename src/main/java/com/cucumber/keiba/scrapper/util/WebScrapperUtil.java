package com.cucumber.keiba.scrapper.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.bson.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

@Component
public class WebScrapperUtil {
		
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
	
	public boolean canConvertToInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
	
	public boolean canConvertToDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
