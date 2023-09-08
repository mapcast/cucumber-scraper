package com.cucumber.keiba.scrapper.util;

import java.util.List;

import org.bson.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import com.cucumber.keiba.scrapper.enums.TranslateDataType;
import com.cucumber.keiba.scrapper.service.translate.TranslateService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Component
public class DocumentUtil extends CommonUtil {
	
	public Document appendIfNotNull(Document document, JsonNode jsonNode, String name) {
		if(jsonNode != null && !jsonNode.isNull()) {
			replaceOrAddElement(document, name, Document.parse(jsonNode.toString()));
			return document;
		} else {
			return document;
		}
	}
	
	public Document appendIfNotNullString(Document document, JsonNode jsonNode, String name) {
		if(jsonNode != null && !jsonNode.isNull()) {
			replaceOrAddElement(document, name, Document.parse(jsonNode.toString()));
			return document;
		} else {
			return document;
		}
	}
	
	public Document replaceOrAddElement(Document document, String key, Object value) {
        if (document.containsKey(key)) {
            document.replace(key, value);
        } else {
            document.append(key, value);
        }
        return document;
    }
	
	private String readYearAndColorLine(WebElement element) {
		for(String line : element.getText().split("\n")) {
			if(line.contains("20") || line.contains("19") || line.contains("18") || line.contains("毛")) {
				return line;
			}
		}
		return null;
	}
	
	private String translateColor(String colorString) {
		colorString = colorString.trim()
				.replace("黒鹿毛", "흑갈색(쿠로카게)")
				.replace("青鹿毛", "진흑갈색(아오카게)")
				.replace("栃栗毛", "진한 밤색(토치쿠리게)")
				.replace("尾花栗毛", "금발 밤색(오바나쿠리게)");
		return colorString
				.replace("鹿毛", "갈색(카게)")
				.replace("栗毛", "밤색(쿠리게)")
				.replace("芦毛", "회색(아시게)")
				.replace("白毛", "백색(시로게)");
	}
	
	private Document setYearAndColor(String fieldName, Document document, WebElement element) {
		String male1YearAndColor = readYearAndColorLine(element);
        if(male1YearAndColor != null) {
        	String[] splitted = male1YearAndColor.split(" ");
        	if(splitted.length >= 2) {
        		document.append(fieldName + "_year", convertToInteger(splitted[0]));
        		document.append(fieldName + "_color", splitted[1].trim().replace("毛", ""));
        	}
        }
        return document;
	}
	
	public Document scrapBloodLineDetail(List<WebElement> bloodLine, TranslateService translateService, CommonUtil commonUtil) {
		Document bloodlineData = new Document();
		//부계 혈통
		WebElement male1Element = bloodLine.get(0).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_male_1", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male1Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_1", bloodlineData, male1Element);
        
        WebElement male2Element = bloodLine.get(0).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_2", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male2Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_2", bloodlineData, male2Element);
        WebElement male4Element = bloodLine.get(0).findElements(By.cssSelector("td")).get(2);
        bloodlineData.append("bloodline_male_4", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male4Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_4", bloodlineData, male4Element);
        WebElement male8Element = bloodLine.get(0).findElements(By.cssSelector("td")).get(3);
        bloodlineData.append("bloodline_male_8", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male8Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_8", bloodlineData, male8Element);
        bloodlineData.append("bloodline_male_16", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(0).findElements(By.cssSelector("td")).get(4).findElements(By.cssSelector("a")).get(0).getText())));
        
        bloodlineData.append("bloodline_female_16", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(1).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female8Element = bloodLine.get(2).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_8", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female8Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_8", bloodlineData, female8Element);
        bloodlineData.append("bloodline_male_17", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(2).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_17", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(3).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
        
        WebElement female4Element = bloodLine.get(4).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_4", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female4Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_4", bloodlineData, female4Element);
        WebElement male9Element = bloodLine.get(4).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_9", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male9Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_9", bloodlineData, male9Element);
        bloodlineData.append("bloodline_male_18", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(4).findElements(By.cssSelector("td")).get(2).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_18", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(5).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female9Element = bloodLine.get(6).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_9", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female9Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_9", bloodlineData, female9Element);
        bloodlineData.append("bloodline_male_19", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(6).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData.append("bloodline_female_19", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(7).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female2Element = bloodLine.get(8).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_2", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female2Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_2", bloodlineData, female2Element);
        WebElement male5Element = bloodLine.get(8).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_5", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male5Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_5", bloodlineData, male5Element);
        WebElement male10Element = bloodLine.get(8).findElements(By.cssSelector("td")).get(2);
        bloodlineData.append("bloodline_male_10", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male10Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_10", bloodlineData, male10Element);
        bloodlineData.append("bloodline_male_20", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(8).findElements(By.cssSelector("td")).get(3).findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData.append("bloodline_female_20", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(9).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female10Element = bloodLine.get(10).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_10", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female10Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_10", bloodlineData, female10Element);
        bloodlineData.append("bloodline_male_21", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(10).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_21", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(11).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female5Element = bloodLine.get(12).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_5", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female5Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_5", bloodlineData, female5Element);
        WebElement male11Element = bloodLine.get(12).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_11", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male11Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_11", bloodlineData, male11Element);
        bloodlineData.append("bloodline_male_22", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(12).findElements(By.cssSelector("td")).get(2).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_22", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(13).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female11Element = bloodLine.get(14).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_11", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(14).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_11", bloodlineData, female11Element);
        bloodlineData.append("bloodline_male_23", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(14).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_23", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(15).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female1Element = bloodLine.get(16).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_1", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female1Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_1", bloodlineData, female1Element);
        WebElement male3Element = bloodLine.get(16).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_3", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male3Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_3", bloodlineData, male3Element);
        WebElement male6Element = bloodLine.get(16).findElements(By.cssSelector("td")).get(2);
        bloodlineData.append("bloodline_male_6", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male6Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_6", bloodlineData, male6Element);
        WebElement male12Element = bloodLine.get(16).findElements(By.cssSelector("td")).get(3);
        bloodlineData.append("bloodline_male_12", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male12Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_12", bloodlineData, male6Element);
        bloodlineData.append("bloodline_male_24", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(16).findElements(By.cssSelector("td")).get(4).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_24", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(17).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female12Element = bloodLine.get(18).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_12", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female12Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_12", bloodlineData, female12Element);
        bloodlineData.append("bloodline_male_25", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(18).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_25", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(19).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female6Element = bloodLine.get(20).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_6", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female6Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_6", bloodlineData, female6Element);
        WebElement male13Element = bloodLine.get(20).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_13", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male13Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_13", bloodlineData, male13Element);
        bloodlineData.append("bloodline_male_26", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(20).findElements(By.cssSelector("td")).get(2).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_26", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(21).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female13Element = bloodLine.get(22).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_13", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female13Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_13", bloodlineData, female13Element);
        bloodlineData.append("bloodline_male_27", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(22).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_27", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(23).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female3Element = bloodLine.get(24).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_3", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female3Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_3", bloodlineData, female3Element);
        WebElement male7Element = bloodLine.get(24).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_7", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male7Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_7", bloodlineData, male7Element);
        WebElement male14Element = bloodLine.get(24).findElements(By.cssSelector("td")).get(2);
        bloodlineData.append("bloodline_male_14", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male14Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_14", bloodlineData, male14Element);
        bloodlineData.append("bloodline_male_28", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(24).findElements(By.cssSelector("td")).get(3).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_28", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(25).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female14Element = bloodLine.get(26).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_14", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female14Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_14", bloodlineData, female14Element);
        bloodlineData.append("bloodline_male_29", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(26).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_29", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(27).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female7Element = bloodLine.get(28).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_7", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female7Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_7", bloodlineData, female7Element);
        WebElement male15Element = bloodLine.get(28).findElements(By.cssSelector("td")).get(1);
        bloodlineData.append("bloodline_male_15", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(male15Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_male_15", bloodlineData, male15Element);
        bloodlineData.append("bloodline_male_30", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(28).findElements(By.cssSelector("td")).get(2).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_30", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(29).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));

        WebElement female15Element = bloodLine.get(30).findElements(By.cssSelector("td")).get(0);
        bloodlineData.append("bloodline_female_15", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(female15Element.findElements(By.cssSelector("a")).get(0).getText())));
        bloodlineData = setYearAndColor("bloodline_female_15", bloodlineData, female15Element);
        bloodlineData.append("bloodline_male_31", translateService.translateJapaneseOnly(TranslateDataType.STALION, commonUtil.cutBeforePar(bloodLine.get(30).findElements(By.cssSelector("td")).get(1).findElements(By.cssSelector("a")).get(0).getText())));

        bloodlineData.append("bloodline_female_31", translateService.translateJapaneseOnly(TranslateDataType.MARE, commonUtil.cutBeforePar(bloodLine.get(31).findElements(By.cssSelector("td")).get(0).findElements(By.cssSelector("a")).get(0).getText())));
        return bloodlineData;
	}
	
}
