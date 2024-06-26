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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.brapi.v2.model.pheno.BrAPIScale;
import org.brapi.v2.model.pheno.BrAPIScaleValidValues;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.dao.db.tables.pojos.ScaleEntity;
import org.jooq.Record;

import java.util.List;

import static org.breedinginsight.dao.db.Tables.SCALE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@Introspected
@Jacksonized
@JsonIgnoreProperties(value = { "id", "programOntologyId", "updatedBy", "createdBy", "updatedAt", "createdAt" })
public class Scale extends ScaleEntity {

    private ProgramOntology programOntology;

    // BrAPI properties
    private Integer validValueMax;
    private Integer validValueMin;
    private Integer decimalPlaces;
    private List<BrAPIScaleValidValuesCategories> categories;

    public Scale(ScaleEntity scaleEntity){
        this.setId(scaleEntity.getId());
        this.setScaleName(scaleEntity.getScaleName());
        this.setUnits(scaleEntity.getUnits());
        this.setProgramOntologyId(scaleEntity.getProgramOntologyId());
        this.setDataType(scaleEntity.getDataType());
        this.setCreatedAt(scaleEntity.getCreatedAt());
        this.setCreatedBy(scaleEntity.getCreatedBy());
        this.setUpdatedAt(scaleEntity.getUpdatedAt());
        this.setUpdatedBy(scaleEntity.getUpdatedBy());
    }

    public static Scale parseSqlRecord(Record record) {
        return Scale.builder()
            .id(record.getValue(SCALE.ID))
            .scaleName(record.getValue(SCALE.SCALE_NAME))
            .units(record.getValue(SCALE.UNITS))
            .programOntologyId(record.getValue(SCALE.PROGRAM_ONTOLOGY_ID))
            .dataType(record.getValue(SCALE.DATA_TYPE))
            .createdAt(record.getValue(SCALE.CREATED_AT))
            .updatedAt(record.getValue(SCALE.UPDATED_AT))
            .createdBy(record.getValue(SCALE.CREATED_BY))
            .updatedBy(record.getValue(SCALE.UPDATED_BY))
            .build();
    }

    public void setBrAPIProperties(BrAPIScale brApiScale) {
        this.setDecimalPlaces(brApiScale.getDecimalPlaces());
        if (brApiScale.getValidValues() != null){
            BrAPIScaleValidValues validValues = brApiScale.getValidValues();
            this.setValidValueMax(validValues.getMax());
            this.setValidValueMin(validValues.getMin());
            this.setCategories(validValues.getCategories());
        }
    }

}
