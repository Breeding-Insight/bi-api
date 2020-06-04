package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.brapi.v2.phenotyping.model.BrApiVariable;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.jooq.Record;

import java.util.List;

import static org.breedinginsight.dao.db.Tables.TRAIT;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "methodId", "scaleId",
        "programOntologyId", "programObservationLevelId" })
public class Trait extends TraitEntity {

    ProgramObservationLevel programObservationLevel;
    Method method;
    Scale scale;
    ProgramOntology programOntology;
    User createdByUser;
    User updatedByUser;

    // Properties from brapi
    String description;
    String traitClass;
    String attribute;
    String defaultValue;
    String entity;
    String mainAbbreviation;
    List<String> abbreviations;
    List<String> synonyms;

    public Trait(TraitEntity traitEntity) {
        this.setId(traitEntity.getId());
        this.setMethodId(traitEntity.getMethodId());
        this.setScaleId(traitEntity.getScaleId());
        this.setTraitName(traitEntity.getTraitName());
        this.setProgramOntologyId(traitEntity.getProgramOntologyId());
        this.setProgramObservationLevelId(traitEntity.getProgramObservationLevelId());
        this.setCreatedAt(traitEntity.getCreatedAt());
        this.setCreatedBy(traitEntity.getCreatedBy());
        this.setUpdatedAt(traitEntity.getUpdatedAt());
        this.setUpdatedBy(traitEntity.getUpdatedBy());
        this.setActive(traitEntity.getActive());
    }

    public static Trait parseSqlRecord(Record record) {
        return Trait.builder()
            .id(record.getValue(TRAIT.ID))
            .methodId(record.getValue(TRAIT.METHOD_ID))
            .scaleId(record.getValue(TRAIT.SCALE_ID))
            .traitName(record.getValue(TRAIT.TRAIT_NAME))
            .programOntologyId(record.getValue(TRAIT.PROGRAM_ONTOLOGY_ID))
            .programObservationLevelId(record.getValue(TRAIT.PROGRAM_OBSERVATION_LEVEL_ID))
            .createdAt(record.getValue(TRAIT.CREATED_AT))
            .createdBy(record.getValue(TRAIT.CREATED_BY))
            .updatedAt(record.getValue(TRAIT.UPDATED_AT))
            .updatedBy(record.getValue(TRAIT.UPDATED_BY))
            .active(record.getValue(TRAIT.ACTIVE))
            .build();
    }

    public void setBrAPIProperties(BrApiVariable brApiVariable) {
        if (brApiVariable.getTrait() != null){
            this.setDescription(brApiVariable.getTrait().getTraitDescription());
            this.setTraitClass(brApiVariable.getTrait().getTraitClass());
            this.setAttribute(brApiVariable.getTrait().getAttribute());
            this.setEntity(brApiVariable.getTrait().getEntity());
            this.setMainAbbreviation(brApiVariable.getTrait().getMainAbbreviation());
            this.setAbbreviations(brApiVariable.getTrait().getAlternativeAbbreviations());
            this.setSynonyms(brApiVariable.getTrait().getSynonyms());
        }

        this.setDefaultValue(brApiVariable.getDefaultValue());
    }

}
