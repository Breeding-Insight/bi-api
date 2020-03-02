package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
public class Program extends ProgramEntity {
    private SpeciesEntity species;

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
}
