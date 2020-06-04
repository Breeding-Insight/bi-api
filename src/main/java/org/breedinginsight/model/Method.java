package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.brapi.v2.phenotyping.model.BrApiMethod;
import org.breedinginsight.dao.db.tables.pojos.MethodEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.METHOD;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "id", "programOntologyId", "updatedBy", "createdBy", "updatedAt", "createdAt" })
public class Method extends MethodEntity {

    ProgramOntology programOntology;

    // BrAPI Properties
    String methodClass;
    String description;
    String formula;

    public Method(MethodEntity methodEntity) {
        this.setId(methodEntity.getId());
        this.setMethodName(methodEntity.getMethodName());
        this.setProgramOntologyId(methodEntity.getProgramOntologyId());
        this.setCreatedAt(methodEntity.getCreatedAt());
        this.setCreatedBy(methodEntity.getCreatedBy());
        this.setUpdatedAt(methodEntity.getUpdatedAt());
        this.setUpdatedBy(methodEntity.getUpdatedBy());
    }

    public static Method parseSqlRecord(Record record) {
        return Method.builder()
            .id(record.getValue(METHOD.ID))
            .methodName(record.getValue(METHOD.METHOD_NAME))
            .programOntologyId(record.getValue(METHOD.PROGRAM_ONTOLOGY_ID))
            .createdAt(record.getValue(METHOD.CREATED_AT))
            .createdBy(record.getValue(METHOD.CREATED_BY))
            .updatedAt(record.getValue(METHOD.UPDATED_AT))
            .updatedBy(record.getValue(METHOD.UPDATED_BY))
            .build();
    }

    public void setBrAPIProperties(BrApiMethod brApiMethod){
        this.setMethodClass(brApiMethod.getMethodClass());
        this.setDescription(brApiMethod.getDescription());
        this.setFormula(brApiMethod.getFormula());
    }
}
