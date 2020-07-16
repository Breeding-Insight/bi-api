/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.brapi.v2.phenotyping.model.BrApiVariable;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.jooq.Record;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import static org.breedinginsight.dao.db.Tables.TRAIT;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "methodId", "scaleId",
        "programOntologyId", "programObservationLevelId", "createdBy", "updatedBy"})
public class Trait extends TraitEntity {

    @JsonIgnoreProperties({"createdAt", "updatedAt"})
    private ProgramObservationLevel programObservationLevel;
    private Method method;
    private Scale scale;
    @JsonIgnore
    private ProgramOntology programOntology;
    @JsonIgnoreProperties({"systemRoles", "programRoles"})
    private User createdByUser;
    @JsonIgnoreProperties({"systemRoles", "programRoles"})
    private User updatedByUser;

    // Properties from brapi
    private String description;
    private String traitClass;
    private String attribute;
    private String defaultValue;
    @JsonIgnore
    private String entity;
    private String mainAbbreviation;
    private List<String> synonyms;

    public Trait(TraitEntity traitEntity) {
        this.setId(traitEntity.getId());
        this.setMethodId(traitEntity.getMethodId());
        this.setScaleId(traitEntity.getScaleId());
        this.setTraitName(traitEntity.getTraitName());
        this.setAbbreviations(traitEntity.getAbbreviations());
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
            .abbreviations(record.getValue(TRAIT.ABBREVIATIONS))
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
            this.setSynonyms(brApiVariable.getTrait().getSynonyms());
        }

        this.setDefaultValue(brApiVariable.getDefaultValue());
    }

}
