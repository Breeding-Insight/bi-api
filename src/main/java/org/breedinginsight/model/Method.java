package org.breedinginsight.model;

import org.breedinginsight.dao.db.tables.pojos.MethodEntity;

public class Method extends MethodEntity {

    ProgramOntology programOntology;

    public Method(MethodEntity methodEntity) {
        this.setId(methodEntity.getId());
        this.setMethodName(methodEntity.getMethodName());
        this.setCreatedAt(methodEntity.getCreatedAt());
        this.setCreatedBy(methodEntity.getCreatedBy());
        this.setUpdatedAt(methodEntity.getUpdatedAt());
        this.setUpdatedBy(methodEntity.getUpdatedBy());
    }
}
