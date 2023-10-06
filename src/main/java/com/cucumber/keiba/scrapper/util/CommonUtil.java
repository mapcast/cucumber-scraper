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
	
	public long convertToLong(String str) {
        try {
            return Long.parseLong(str.replaceAll("[^0-9\\-\\+]", ""));
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
		return str.replace("★", "").replace("△", "").replace("▲", "").replace("☆", "").replace("◇", "");
	}
	
	public int priceToInteger(String price) {
		int result = 0;
		int okuIndex = price.indexOf("億");
		if(okuIndex > -1) {
			int oku = convertToInteger(price.substring(0, okuIndex));
			result += oku * 10000;
			price = price.substring(okuIndex + 1, price.length() - 1);
		}
		int banIndex = price.indexOf("万");
		if(banIndex > -1) {
			int ban = convertToInteger(price.substring(0, banIndex));
			result += ban;
		}
		return result;
	}
}
