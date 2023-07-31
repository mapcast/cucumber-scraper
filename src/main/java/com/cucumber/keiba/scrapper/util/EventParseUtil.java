package com.cucumber.keiba.scrapper.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EventParseUtil {
	
	private final NullUtil nullUtil;
	
	public List<Document> stringEventParser(String eventText, String eventName, List<Document> docs, String homeName, String awayName) {
		Document doc = new Document();
		doc.append("time", eventText.substring(0, eventText.indexOf('-') - 1).replace(" ", ""));
		String timeAfter = eventText.substring(eventText.indexOf(eventName + " -") + eventName.length() + 2);
		String player = timeAfter.substring(0, timeAfter.indexOf(" ("));
		if(!player.replace(" ", "").equals("")) 
			doc.append("player", player);
		String team = timeAfter.substring(timeAfter.indexOf(" (") + 2, timeAfter.length() - 1);
		if(team.equals(homeName)) {
			doc.append("home_away", "home");
		} else if(team.equals(awayName)) {
			doc.append("home_away", "away");
		}
		docs.add(doc);
		return docs;
	}
	
	public Document scoresParser(Document details, JsonNode eventDetail) {
		if(eventDetail.has("scores")) {
			Iterator<String> fieldNames = eventDetail.get("scores").fieldNames();
			while(fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				try {
					//field가 number가 아닐 경우 추가하지 않음.
					Integer.parseInt(fieldName);
					details.append("score_home" + fieldName, Integer.parseInt(eventDetail.get("scores").get(fieldName).get("home").textValue()));
					details.append("score_away" + fieldName, Integer.parseInt(eventDetail.get("scores").get(fieldName).get("away").textValue()));
				} catch(NumberFormatException e) {}
				
			}
		}
		
		return details;
	}
	
	public Document eventDefaultCreator(JsonNode event, int leagueKey, Integer homeKey, Integer awayKey) {
		Document document = new Document();
		LocalDateTime now = LocalDateTime.now();
		
		document.append("regdate", now);
		document.append("last_update", now);
		document.append("game_key", UUID.randomUUID().toString());
		
		document.append("sport_id", event.get("sport_id").textValue());
		document.append("api_gameid", event.get("id").textValue());
		document.append("game_date", Long.parseLong(event.get("time").textValue()));
		LocalDateTime gameDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(event.get("time").textValue())), ZoneId.systemDefault());
		document.append("game_date_parsed", gameDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		document = nullUtil.appendIfNotNullString(document, event.get("time_status"), "status");
		
		document.append("league_key", leagueKey);
		document.append("league_id", event.get("league").get("id").textValue());
		if(!event.get("league").get("cc").isNull())
			document.append("league_country_code", event.get("league").get("cc").textValue());
		
		if(homeKey != null) {
			document.append("home_key", homeKey);
			document.append("home_id", event.get("home").get("id").textValue());
		}
		
		if(awayKey != null) {
			document.append("away_key", awayKey);
			document.append("away_id", event.get("away").get("id").textValue());
		}
		
		if(event.get("ss") != null) {
			if(!event.get("ss").isNull()) {
				String[] parsed_scores = event.get("ss").textValue().split("-");
				if(parsed_scores.length == 2) {
					document.append("home_score", parsed_scores[0]);
					document.append("away_score", parsed_scores[1]);
				}
				
			}
		}
		
		if(event.has("timer")) {
			document.append("tm", event.get("timer").get("tm"));
			document.append("ts", event.get("timer").get("ts"));
			document.append("tt", event.get("timer").get("tt"));
			document.append("ta", event.get("timer").get("ta"));
			document.append("md", event.get("timer").get("md"));
		}
		
		return document;
	}
	
	public Document eventDefaultReplacer(JsonNode event, Document original, int leagueKey, Integer homeKey, Integer awayKey) {
		//SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		//String nowString = sdf.format(Calendar.getInstance().getTime());
		Date now = new Date();
		original.replace("last_update", now);
		original.append("game_date", Long.parseLong(event.get("time").textValue()));
		original = nullUtil.appendIfNotNullString(original, event.get("time_status"), "status");
		
		original.replace("league_key", leagueKey);
		original.replace("league_id", event.get("league").get("id").textValue());
		if(!event.get("league").get("cc").isNull()) {
			if(original.containsKey("league_country_code")) {
				original.replace("league_country_code", event.get("league").get("cc").textValue());
			} else {
				original.append("league_country_code", event.get("league").get("cc").textValue());
			}
		}
			
		
		if(homeKey != null) {
			if(original.containsKey("home_key")) {
				original.replace("home_key", homeKey);
				original.replace("home_id", event.get("home").get("id").textValue());
			} else {
				original.append("home_key", homeKey);
				original.append("home_id", event.get("home").get("id").textValue());
			}
		}
		
		if(awayKey != null) {
			if(original.containsKey("away_key")) {
				original.replace("away_key", awayKey);
				original.replace("away_id", event.get("away").get("id").textValue());
			} else {
				original.append("away_key", awayKey);
				original.append("away_id", event.get("away").get("id").textValue());
			}
		}
		
		if(event.get("ss") != null) {
			if(!event.get("ss").isNull()) {
				String[] parsed_scores = event.get("ss").textValue().split("-");
				if(parsed_scores.length == 2) {
					if(original.containsKey("home_score")) {
						original.replace("home_score", parsed_scores[0]);
						original.replace("away_score", parsed_scores[1]);
					} else {
						original.append("home_score", parsed_scores[0]);
						original.append("away_score", parsed_scores[1]);
					}
				}
			}
		}
		
		if(event.has("timer")) {
			if(original.containsKey("tm")) {
				original.replace("tm", event.get("timer").get("tm"));
				original.replace("ts", event.get("timer").get("ts"));
				original.replace("tt", event.get("timer").get("tt"));
				original.replace("ta", event.get("timer").get("ta"));
				original.replace("md", event.get("timer").get("md"));
			} else {
				original.append("tm", event.get("timer").get("tm"));
				original.append("ts", event.get("timer").get("ts"));
				original.append("tt", event.get("timer").get("tt"));
				original.append("ta", event.get("timer").get("ta"));
				original.append("md", event.get("timer").get("md"));
			}
		}
		return original;
	}
	
	public Document eventDetailParser(JsonNode eventDetail, Document original, Integer leagueKey, Integer homeKey, Integer awayKey) {
		Document document = new Document();
		if(original == null) {
			document = eventDefaultCreator(eventDetail, leagueKey, homeKey, awayKey);
		} else {
			document = eventDefaultReplacer(eventDetail, original, leagueKey, homeKey, awayKey);
		}
		
		nullUtil.appendIfNotNullString(document, eventDetail.get("inplay_created_at"), "inplay_created_at");
		nullUtil.appendIfNotNullString(document, eventDetail.get("inplay_updated_at"), "inplay_updated_at");
		nullUtil.appendIfNotNullString(document, eventDetail.get("confirmed_at"), "confirmed_at");
		nullUtil.appendIfNotNull(document, eventDetail.get("extra"), "extra");
		if(eventDetail.has("extra")) {
			if(eventDetail.get("extra").has("stadium_data")) {
				if(document.containsKey("stadium_id")) {
					document.replace("stadium_id", eventDetail.get("extra").get("stadium_data").get("id").textValue());
				} else {
					document.append("stadium_id", eventDetail.get("extra").get("stadium_data").get("id").textValue());
				}
			}
		}
		
		document = nullUtil.appendIfNotNull(document, eventDetail.get("stats"), "stats");
		
		if(eventDetail.has("has_lineup")) {
			if(document.containsKey("has_lineup")) {
				document.replace("is_lineup", eventDetail.get("has_lineup").textValue());
			} else {
				document.append("is_lineup", eventDetail.get("has_lineup").textValue());
			}
		}
		
		if(eventDetail.get("sport_id").textValue().equals("1")) {
			document = soccerParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("2") || eventDetail.get("sport_id").textValue().equals("4")) {
			document = racingParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("3")) {
			document = cricketParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("8")) {
			document = rugbyunionParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("9")) {
			//boxing
			//document = boxingParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("12")) {
			document = americanFootballParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("13")) {
			document = tennisParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("14")) {
			document = snookerParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("15")) {
			document = dartParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("16")) {
			document = baseballParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("17")) {
			document = icehockeyParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("18")) {
			document = basketballParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("19")) {
			document = rugbyleagueParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("36")) {
			document = austrailianruleParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("66")) {
			document = bowlParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("75")) {
			document = gaelicsportParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("78")) {
			document = handballParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("83")) {
			document = futsalParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("90")) {
			document = floorballParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("92")) {
			document = tabletennisParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("94")) {
			document = badmintonParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("95")) {
			document = beachvolleyballParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("107")) {
			document = squashParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("110")) {
			document = waterpoloParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("148")) {
			document = surfingParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("151")) {
			document = esportParser(document, eventDetail);
		} else if(eventDetail.get("sport_id").textValue().equals("162")) {
			document = mmaParser(document, eventDetail);
		}
		
		return document;
	}
	
	public Document soccerParser(Document original, JsonNode eventDetail) {
		
		Document details = new Document();
				
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		details = scoresParser(details, eventDetail);
		
		if(eventDetail.has("events")) {
			List<Document> goals = new ArrayList<>();
			List<Document> cards = new ArrayList<>();
			List<Document> substitutions = new ArrayList<>();
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText != null) {
					if(eventText.contains("Goal -")) {
						Document goal = new Document();
						goal.append("time", eventText.substring(0, eventText.indexOf('-') - 1).replace(" ", ""));
						String goalAfter = eventText.substring(eventText.indexOf("Goal -") + 6);
						String player = goalAfter.substring(0, goalAfter.indexOf(" ("));
						if(!player.replace(" ", "").equals("")) 
							goal.append("player", player);
						else {
							//득점 정보가 제공되지 않는 경기가 있음.
							//break;
						}
						String scoredTeam = goalAfter.substring(goalAfter.indexOf(" (") + 2, goalAfter.indexOf(") -"));
						
						//String test = "Dibala (Nantong Zhiyun) - (Assist: Alonso)";
						//System.out.println("test - player: " + test.substring(0, test.indexOf(" (")));
						//System.out.println("test - scoredTeam: " + test.substring(test.indexOf(" (") + 2, test.indexOf(") -")));
						//System.out.println("test - assist: " + test.substring(test.indexOf("(Assist:") + 7, test.length() - 1));
						if(scoredTeam.equals(homeName)) {
							goal.append("home_away", "home");
						} else if(scoredTeam.equals(awayName)) {
							goal.append("home_away", "away");
						}
						if(goalAfter.indexOf("(Assist:") > -1) {
							goal.append("assist_player", goalAfter.substring(goalAfter.indexOf("(Assist:") + 7, goalAfter.length() - 1));
						}
						goals.add(goal);
					} 
					if(eventText.contains("Card -")) {
						Document card = new Document();
						card.append("time", eventText.substring(0, eventText.indexOf('-') - 1).replace(" ", ""));
						String cardAfter = eventText.substring(eventText.indexOf("Card -") + 6);
						String player = cardAfter.substring(0, cardAfter.indexOf(" ("));
						if(!player.replace(" ", "").equals("")) 
							card.append("player", player);
						else {
							//break;
						}
						String cardTeam = cardAfter.substring(cardAfter.indexOf(" (") + 2, cardAfter.length() - 1);
						if(cardTeam.equals(homeName)) {
							card.append("home_away", "home");
						} else if(cardTeam.equals(awayName)) {
							card.append("home_away", "away");
						}
						cards.add(card);
					}
					if(eventText.contains("Substitution -")) {
						Document substitute = new Document();
						substitute.append("time", eventText.substring(0, eventText.indexOf('-') - 1).replace(" ", ""));
						String subAfter = eventText.substring(eventText.indexOf("Card -") + 6);
						String player = subAfter.substring(0, subAfter.indexOf(" ("));
						if(!player.replace(" ", "").equals("")) 
							substitute.append("player", player);
						else {
							//break;
						}
						String cardTeam = subAfter.substring(subAfter.indexOf(" (") + 2, subAfter.length() - 1);
						if(cardTeam.equals(homeName)) {
							substitute.append("home_away", "home");
						} else if(cardTeam.equals(awayName)) {
							substitute.append("home_away", "away");
						}
						substitutions.add(substitute);
					}
				}
			}
			details.append("goals", goals);
			details.append("cards", cards);
			details.append("substitutions", substitutions);
		}
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document racingParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		
		List<Document> runners  = new ArrayList<>();
		
		for(JsonNode runnerJson : eventDetail.get("teams")) {
			Document runner = new Document();
			runner.append("order", runnerJson.get("nc").textValue());
			runner.append("rpos", runnerJson.get("rpos").textValue());
				
			runner.append("name", runnerJson.get("team").get("name").textValue());
			if(runnerJson.has("trainer"))
				runner.append("trainer", runnerJson.get("trainer").get("name").textValue());
			if(runnerJson.has("jockey"))
				runner.append("jockey", runnerJson.get("jy").get("name").textValue());
			runners.add(runner);
		}
		details.append("runners", runners);
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document cricketParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		
		if(eventDetail.has("scores")) {
			int count = 1;
			while(true) {
				if(eventDetail.get("scores").has(Integer.toString(count))) {
					details.append("score_home" + count, Integer.parseInt(eventDetail.get("scores").get(Integer.toString(count)).get("home").textValue()));
					details.append("score_away" + count, Integer.parseInt(eventDetail.get("scores").get(Integer.toString(count)).get("away").textValue()));
					count ++;
				} else {
					break;
				}
				
			}
		}
		
		original.append("details", details);
		return original;
	}
	
	public Document rugbyunionParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		details = scoresParser(details, eventDetail);
		
		
		
		if(eventDetail.has("events")) {
			List<Document> panalties = new ArrayList<>();
			List<Document> cards = new ArrayList<>();
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText != null) {
					if(eventText.contains("Panalty Kick -")) {
						panalties = stringEventParser(eventText, "Panalty Kick", panalties, homeName, awayName);
					} 
					if(eventText.contains("Card -")) {
						cards = stringEventParser(eventText, "Card", cards, homeName, awayName);
					}
				}
			}
			details.append("panalties", panalties);
			details.append("cards", cards);
		}
		
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document americanFootballParser(Document original, JsonNode eventDetail) {
				
		Document details = new Document();
				
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		details = scoresParser(details, eventDetail);
		
		//이해 부족으로 인해 아직 이벤트 분석 코드 작성 불가능.
		
		original.append("details", details);
		return original;
	}
	
	public Document tennisParser(Document original, JsonNode eventDetail) {
		
		Document details = new Document();
				
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		/*
		String[] sss = eventDetail.get("ss").textValue().split("\\,");
		for(int i = 1; i <= sss.length; i++) {
			String[] scores = sss[i - 1].split("-");
			details.append(i + "_score_home", Integer.parseInt(scores[0]));
			details.append(i + "_score_away", Integer.parseInt(scores[1]));
		}*/
		
		details = scoresParser(details, eventDetail);
		
		
		if(eventDetail.has("events")) {
			List<Document> gameWinners = new ArrayList<>();
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText == null) continue;
				if(eventText.contains("Game")) {
					Document gameWinner = new Document();
					
					Document goal = new Document();
					int firstHyphen = eventText.indexOf('-');
					int secondHyphen = eventText.indexOf('-', firstHyphen + 1);
					String scored = eventText.substring(firstHyphen + 2, secondHyphen - 1);
					if(scored.equals(homeName)) {
						gameWinner.append("winner", "home");
					} else if(scored.equals(awayName)) {
						gameWinner.append("winner", "away");
					}
					gameWinners.add(gameWinner);
				}
			}
			details.append("game_winners", gameWinners);
		}
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document snookerParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//이벤트가 있지만 크게 필요할거같진 않음.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document dartParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//15
		//특별한 처리기 필요하지 않아보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	
	public Document baseballParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//16
		//id - 6256485
		
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		details = scoresParser(details, eventDetail);
		if(eventDetail.has("scores")) {
			if(eventDetail.get("scores").has("ot")) {
				details.append("score_home_ot", Integer.parseInt(eventDetail.get("scores").get("ot").get("home").textValue()));
				details.append("score_away_ot", Integer.parseInt(eventDetail.get("scores").get("ot").get("away").textValue()));
			}
			if(eventDetail.get("scores").has("hit")) {
				details.append("home_hit", Integer.parseInt(eventDetail.get("scores").get("hit").get("home").textValue()));
				details.append("away_hit", Integer.parseInt(eventDetail.get("scores").get("hit").get("away").textValue()));
			}
			if(eventDetail.get("scores").has("run")) {
				details.append("home_run", Integer.parseInt(eventDetail.get("scores").get("run").get("home").textValue()));
				details.append("away_run", Integer.parseInt(eventDetail.get("scores").get("run").get("away").textValue()));
			}
		}
		
		
		if(eventDetail.has("events")) {
			List<Document> homeruns = new ArrayList<>();
			List<Document> inningDetails = new ArrayList<>();
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText != null) {
					if(eventText.startsWith("End of")) {
						Document inning = new Document();
						String removed = eventText.substring(7);
						if(removed.startsWith(homeName)) {
							inning.append("home_away", "home");
							removed = removed.substring(homeName.length() + 1);
						} else if(removed.startsWith(awayName)) {
							inning.append("home_away", "away");
							removed = removed.substring(awayName.length() + 1);
						}
						inning.append("inning", Character.getNumericValue(removed.charAt(0)));
						
						int firstComma = removed.indexOf(',');
						int secondComma = removed.indexOf(',', firstComma + 1);
						
						inning.append("hits", removed.substring(firstComma + 1, firstComma + 2).trim());
						inning.append("runs", removed.substring(secondComma + 1, secondComma + 2).trim());
						
						inningDetails.add(inning);
						
					} else if(eventText.contains("HR scored by")) {
						Document homerun = new Document();
						String[] splitted = eventText.split("HR scored by");
						if(splitted[0].trim().equals("Solo")) {
							homerun.append("score", 1);
						} else if(splitted[0].trim().equals("2-run")) {
							homerun.append("score", 2);
						} else if(splitted[0].trim().equals("3-run")) {
							homerun.append("score", 3);
						} else {
							//자료 부족으로 상세한 텍스트를 확인하지 못함.
							homerun.append("score", 4);
						}
						String player = splitted[1].substring(0, splitted[1].indexOf(" ("));
						if(!player.replace(" ", "").equals("")) 
							homerun.append("player", player);
						String team = splitted[1].substring(splitted[1].indexOf(" (") + 2, splitted[1].length() - 1);
						if(team.trim().equals(homeName)) {
							homerun.append("home_away", "home");
						} else if(team.trim().equals(awayName)) {
							homerun.append("home_away", "away");
						}
						homeruns.add(homerun);
					}
				}
			}
			details.append("inning_details", inningDetails);
			details.append("homeruns", homeruns);
		}
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document icehockeyParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//17
		//6576832
		
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		details = scoresParser(details, eventDetail);
		
		List<Document> goals = new ArrayList<>();
		if(eventDetail.has("events")) {
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText != null) {
					if(eventText.contains("Goal ")) {
						Document goal = new Document();
						int firstHyphen = eventText.indexOf('-');
						int secondHyphen = eventText.indexOf('-', firstHyphen + 1);
						int thirdHyphen = eventText.indexOf('-', secondHyphen + 1);
						goal.append("time", eventText.substring(0, firstHyphen - 1).replace(" ", ""));
						String scoredTeam = eventText.substring(secondHyphen + 2, thirdHyphen - 1);
						if(scoredTeam.equals(homeName)) {
							goal.append("home_away", "home");
						} else if(scoredTeam.equals(awayName)) {
							goal.append("home_away", "away");
						}
						goals.add(goal);
					} 
				}
				
			}
		}
		details.append("goals", goals);
		
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document basketballParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//18
		//6834509 
		details = scoresParser(details, eventDetail);
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document rugbyleagueParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//19
		//6597171
		details = scoresParser(details, eventDetail);
		//지식 부족으로 이벤트 파싱이 힘든 상태.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document austrailianruleParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//36
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document bowlParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//66
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document gaelicsportParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//75
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document handballParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//78
		//6879619details = scoresParser(details, eventDetail);
		
		details = scoresParser(details, eventDetail);
		
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		if(eventDetail.has("events")) {
			List<Document> goals = new ArrayList<>();
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText != null) {
					if(eventText.contains("Goal ")) {
						Document goal = new Document();
						goal.append("time", eventText.substring(0, eventText.indexOf('-') - 1).replace(" ", ""));
						String team = eventText.substring(eventText.indexOf("Goal -") + 6).trim();
						if(team.equals(homeName)) {
							goal.append("home_away", "home");
						} else if(team.equals(awayName)) {
							goal.append("home_away", "away");
						}
						goals.add(goal);
					}
				}
			}
		}
		
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document futsalParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//83
		//6878865
		
		details = scoresParser(details, eventDetail);
		
		String homeName = eventDetail.get("home").get("name").textValue();
		String awayName = eventDetail.get("away").get("name").textValue();
		
		if(eventDetail.has("events")) {
			List<Document> goals = new ArrayList<>();
			for(JsonNode event : eventDetail.get("events")) {
				String eventText = event.get("text").textValue();
				if(eventText != null) {
					if(eventText.contains("Goal ")) {
						Document goal = new Document();
						goal.append("time", eventText.substring(0, eventText.indexOf('-') - 1).replace(" ", ""));
						String team = eventText.substring(eventText.indexOf("Goal -") + 6).trim();
						if(team.equals(homeName)) {
							goal.append("home_away", "home");
						} else if(team.equals(awayName)) {
							goal.append("home_away", "away");
						}
						goals.add(goal);
					}
				}
			}
		}
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document floorballParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//90
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document tabletennisParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//92
		//상세 이벤트 제공 등은 없는것으로 보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document badmintonParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//94
		//상세 이벤트 제공이 없진 않은것으로 보이나, 예제를 찾지 못한 상태.
		details = scoresParser(details, eventDetail);
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document beachvolleyballParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//95
		//상세 이벤트 제공 등은 없는것으로 보임.
		details = scoresParser(details, eventDetail);
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document squashParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//107
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document waterpoloParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//110
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document surfingParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//148
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document esportParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//151
		//6855190(LOL)
		details = scoresParser(details, eventDetail);
		//상세 이벤트가 제공되기는 하지만 esport 내에서 종목이 나뉘기때문에 애매
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	public Document mmaParser(Document original, JsonNode eventDetail) {
		Document details = new Document();
		//162
		//매우 제한적인 수준의 정보 제공으로, 특별히 분석할 내용이 없어보임.
		if(original.containsKey("details")) {
			original.replace("details", details);
		} else {
			original.append("details", details);
		}
		return original;
	}
	
	
}
