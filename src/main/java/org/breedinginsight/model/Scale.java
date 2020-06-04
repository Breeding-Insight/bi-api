package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.brapi.v2.phenotyping.model.BrApiScale;
import org.brapi.v2.phenotyping.model.BrApiScaleCategories;
import org.brapi.v2.phenotyping.model.BrApiScaleValidValues;
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
@JsonIgnoreProperties(value = { "id", "programOntologyId", "updatedBy", "createdBy", "updatedAt", "createdAt" })
public class Scale extends ScaleEntity {

    ProgramOntology programOntology;

    // BrAPI properties
    Integer validValueMax;
    Integer validValueMin;
    Integer decimalPlaces;
    List<BrApiScaleCategories> categories;

    public Scale(ScaleEntity scaleEntity){
        this.setId(scaleEntity.getId());
        this.setScaleName(scaleEntity.getScaleName());
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
            .programOntologyId(record.getValue(SCALE.PROGRAM_ONTOLOGY_ID))
            .dataType(record.getValue(SCALE.DATA_TYPE))
            .createdAt(record.getValue(SCALE.CREATED_AT))
            .updatedAt(record.getValue(SCALE.UPDATED_AT))
            .createdBy(record.getValue(SCALE.CREATED_BY))
            .updatedBy(record.getValue(SCALE.UPDATED_BY))
            .build();
    }

    public void setBrAPIProperties(BrApiScale brApiScale) {
        this.setDecimalPlaces(brApiScale.getDecimalPlaces());
        if (brApiScale.getValidValues() != null){
            BrApiScaleValidValues validValues = brApiScale.getValidValues();
            this.setValidValueMax(validValues.getMax());
            this.setValidValueMin(validValues.getMin());
            this.setCategories(validValues.getCategories());
        }
    }

}
