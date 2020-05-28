package org.breedinginsight.model;

import org.breedinginsight.dao.db.tables.pojos.TraitEntity;

import java.util.List;

public class Trait extends TraitEntity {

    List<ProgramObservationLevel> observationLevels;
    Method method;
    Scale scale;
    ProgramOntology programOntology;

    public Trait(TraitEntity traitEntity) {
        this.setId(traitEntity.getId());
        this.setMethodId(traitEntity.getMethodId());
        this.setScaleId(traitEntity.getScaleId());
        this.setTraitName(traitEntity.getTraitName());
        this.setProgramOntologyId(traitEntity.getProgramOntologyId());
        this.setCreatedAt(traitEntity.getCreatedAt());
        this.setCreatedBy(traitEntity.getCreatedBy());
        this.setUpdatedAt(traitEntity.getUpdatedAt());
        this.setUpdatedBy(traitEntity.getUpdatedBy());
    }
}
