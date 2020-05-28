package org.breedinginsight.model;

import org.breedinginsight.dao.db.tables.pojos.ProgramObservationLevelEntity;

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
    }
}
