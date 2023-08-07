package com.cucumber.keiba.scrapper.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.cucumber.keiba.scrapper.enums.TranslateDataType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "translateDatas")
public class TranslateData {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_number")
    private long rowNumber;
	
	@Column(name = "type", length = 30)
	@Enumerated(EnumType.STRING)
    private TranslateDataType type;
	
	@Column(name = "original", nullable = false)
    private String original;
	
	@Column(name = "translated")
    private String translated;
	
	@Column(name = "is_translated_by_machine")
    private Boolean isTranslatedByMachine;
}
