package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ProgramObservationLevelEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.PROGRAM_OBSERVATION_LEVEL;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "updatedBy", "createdBy" })
public class ProgramObservationLevel extends ProgramObservationLevelEntity {

    ProgramObservationLevel parentObservationLevel;
    ProgramObservationLevel childObservationLevel;

    public ProgramObservationLevel(ProgramObservationLevelEntity observationLevelEntity) {
        this.setId(observationLevelEntity.getId());
        this.setName(observationLevelEntity.getName());
        this.setContains(observationLevelEntity.getContains());
        this.setPartOf(observationLevelEntity.getPartOf());
        this.setCreatedAt(observationLevelEntity.getCreatedAt());
        this.setCreatedBy(observationLevelEntity.getCreatedBy());
        this.setUpdatedAt(observationLevelEntity.getUpdatedAt());
        this.setUpdatedBy(observationLevelEntity.getUpdatedBy());
        this.setActive(observationLevelEntity.getActive());
    }

    public static ProgramObservationLevel parseSqlRecord(Record record) {
        return ProgramObservationLevel.builder()
            .id(record.getValue(PROGRAM_OBSERVATION_LEVEL.ID))
            .name(record.getValue(PROGRAM_OBSERVATION_LEVEL.NAME))
            .contains(record.getValue(PROGRAM_OBSERVATION_LEVEL.CONTAINS))
            .partOf(record.getValue(PROGRAM_OBSERVATION_LEVEL.PART_OF))
            .createdAt(record.getValue(PROGRAM_OBSERVATION_LEVEL.CREATED_AT))
            .createdBy(record.getValue(PROGRAM_OBSERVATION_LEVEL.CREATED_BY))
            .updatedAt(record.getValue(PROGRAM_OBSERVATION_LEVEL.UPDATED_AT))
            .updatedBy(record.getValue(PROGRAM_OBSERVATION_LEVEL.UPDATED_BY))
            .active(record.get(PROGRAM_OBSERVATION_LEVEL.ACTIVE))
            .build();
    }
}
