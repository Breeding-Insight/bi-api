package org.breedinginsight.model;

import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramOntologyEntity;

public class ProgramOntology extends ProgramOntologyEntity {

    Program program;

    public ProgramOntology(ProgramOntologyEntity programOntologyEntity){
        this.setId(programOntologyEntity.getId());
        this.setProgramId(programOntologyEntity.getProgramId());
        this.setCreatedAt(programOntologyEntity.getCreatedAt());
        this.setCreatedBy(programOntologyEntity.getCreatedBy());
        this.setUpdatedAt(programOntologyEntity.getUpdatedAt());
        this.setUpdatedBy(programOntologyEntity.getUpdatedBy());
    }
}
