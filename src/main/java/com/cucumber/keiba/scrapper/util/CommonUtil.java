package com.cucumber.keiba.scrapper.util;

import org.springframework.stereotype.Component;

@Component
public class CommonUtil {
	public int convertToInteger(String str) {
        try {
            return Integer.parseInt(str.replaceAll("[^0-9\\-\\+]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
	
	public double convertToDouble(String str) {
        try {
            return Double.parseDouble(str.replaceAll("[^0-9\\.\\-\\+]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
	
	public String cutBeforePar(String str) {
		if(str.contains("(")) {
			return str.substring(0, str.indexOf("(")).trim();
		} else {
			return str;
		}
	}
	
	public String removeMark(String str) {
		return str.replace("★", "").replace("△", "").replace("▲", "").replace("☆", "");
	}
}
