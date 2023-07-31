package com.cucumber.keiba.scrapper.specs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/*
public class TeamSpecs  {
	public static Specification<RxScSportTeam> searchTeam(Map<String, Object> keyword) {
		return ((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			for(String key : keyword.keySet()) {
				if(keyword.get(key).getClass() == String.class) {
					predicates.add(criteriaBuilder.equal(root.get(key), "%" + keyword.get(key) + "%"));
				} else {
					predicates.add(criteriaBuilder.equal(root.get(key), keyword.get(key)));
				}
			}
			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		});
	}
}
*/