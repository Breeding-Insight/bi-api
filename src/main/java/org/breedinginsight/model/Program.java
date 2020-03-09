package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.*;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "createdBy", "updatedBy", "speciesId" })
public class Program extends ProgramEntity {
    private SpeciesEntity species;
    private User createdByUser;
    private User updatedByUser;

    public Program(ProgramEntity programEntity){

        this.setId(programEntity.getId());
        this.setSpeciesId(programEntity.getSpeciesId());
        this.setName(programEntity.getName());
        this.setAbbreviation(programEntity.getAbbreviation());
        this.setObjective(programEntity.getObjective());
        this.setDocumentationUrl(programEntity.getDocumentationUrl());
        this.setCreatedAtUtc(programEntity.getCreatedAtUtc());
        this.setUpdatedAtUtc(programEntity.getUpdatedAtUtc());
        this.setCreatedBy(programEntity.getCreatedBy());
        this.setUpdatedBy(programEntity.getUpdatedBy());

    }

    public static Program parseSQLRecord(Record record){

        // Generate our program record
        Program program = Program.builder()
                .id(record.getValue(PROGRAM.ID))
                .name(record.getValue(PROGRAM.NAME))
                .abbreviation(record.getValue(PROGRAM.ABBREVIATION))
                .objective(record.getValue(PROGRAM.OBJECTIVE))
                .documentationUrl(record.getValue(PROGRAM.DOCUMENTATION_URL))
                .createdAtUtc(record.getValue(PROGRAM.CREATED_AT_UTC))
                .updatedAtUtc(record.getValue(PROGRAM.UPDATED_AT_UTC))
                .createdBy(record.getValue(PROGRAM.CREATED_BY))
                .updatedBy(record.getValue(PROGRAM.UPDATED_BY))
                .active(record.getValue(PROGRAM.ACTIVE))
                .build();

        return program;
    }

}
