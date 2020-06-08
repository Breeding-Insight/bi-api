package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ProgramOntologyEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.PROGRAM_ONTOLOGY;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "updatedBy", "createdBy" })
public class ProgramOntology extends ProgramOntologyEntity {

    private Program program;

    public ProgramOntology(ProgramOntologyEntity programOntologyEntity){
        this.setId(programOntologyEntity.getId());
        this.setProgramId(programOntologyEntity.getProgramId());
        this.setCreatedAt(programOntologyEntity.getCreatedAt());
        this.setCreatedBy(programOntologyEntity.getCreatedBy());
        this.setUpdatedAt(programOntologyEntity.getUpdatedAt());
        this.setUpdatedBy(programOntologyEntity.getUpdatedBy());
    }

    public static ProgramOntology parseSqlRecord(Record record) {
        return ProgramOntology.builder()
        .id(record.getValue(PROGRAM_ONTOLOGY.ID))
        .programId(record.getValue(PROGRAM_ONTOLOGY.PROGRAM_ID))
        .createdAt(record.getValue(PROGRAM_ONTOLOGY.CREATED_AT))
        .createdBy(record.getValue(PROGRAM_ONTOLOGY.CREATED_BY))
        .updatedAt(record.getValue(PROGRAM_ONTOLOGY.UPDATED_AT))
        .updatedBy(record.getValue(PROGRAM_ONTOLOGY.UPDATED_BY))
        .build();
    }
}
