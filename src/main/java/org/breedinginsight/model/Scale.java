package org.breedinginsight.model;

import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.ScaleEntity;

public class Scale extends ScaleEntity {

    ProgramOntology programOntology;

    public Scale(ScaleEntity scaleEntity){
        this.setId(scaleEntity.getId());
        this.setScaleName(scaleEntity.getScaleName());
        this.setDataType(scaleEntity.getDataType());
        this.setCreatedAt(scaleEntity.getCreatedAt());
        this.setCreatedBy(scaleEntity.getCreatedBy());
        this.setUpdatedAt(scaleEntity.getUpdatedAt());
        this.setUpdatedBy(scaleEntity.getUpdatedBy());
    }

}
