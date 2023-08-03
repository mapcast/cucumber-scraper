package com.cucumber.keiba.scrapper.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cucumber.keiba.scrapper.model.TranslateData;
import com.cucumber.keiba.scrapper.model.TranslateDataType;
import com.google.common.base.Optional;

public interface TranslateDataRepository extends JpaRepository<TranslateData, Long> {
	Optional<TranslateData> findByTypeAndOriginal(TranslateDataType type, String original);
	
	List<TranslateData> findByTypeAndOriginalContaining(TranslateDataType type, String original);
}
