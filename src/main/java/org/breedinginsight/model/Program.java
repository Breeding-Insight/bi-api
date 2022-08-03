/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.dao.db.tables.ProgramTable;
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
@Introspected
@Jacksonized
@JsonIgnoreProperties(value = { "createdBy", "updatedBy", "speciesId" })
public class Program extends ProgramEntity {

    private SpeciesEntity species;
    private User createdByUser;
    private User updatedByUser;
    private int numUsers;

    @JsonIgnore
    private BrAPIProgram brapiProgram;

    public Program(ProgramEntity programEntity){

        this.setId(programEntity.getId());
        this.setSpeciesId(programEntity.getSpeciesId());
        this.setName(programEntity.getName());
        this.setAbbreviation(programEntity.getAbbreviation());
        this.setObjective(programEntity.getObjective());
        this.setDocumentationUrl(programEntity.getDocumentationUrl());
        this.setBrapiUrl(programEntity.getBrapiUrl());
        this.setKey(programEntity.getKey());
        this.setCreatedAt(programEntity.getCreatedAt());
        this.setUpdatedAt(programEntity.getUpdatedAt());
        this.setCreatedBy(programEntity.getCreatedBy());
        this.setUpdatedBy(programEntity.getUpdatedBy());

    }

    public static Program parseSQLRecord(Record record){
        return parseSQLRecord(record, PROGRAM);
    }

    public static Program parseSQLRecord(Record record, ProgramTable programTable) {

        // Generate our program record
        Program program = Program.builder()
                .id(record.getValue(programTable.ID))
                .name(record.getValue(programTable.NAME))
                .abbreviation(record.getValue(programTable.ABBREVIATION))
                .objective(record.getValue(programTable.OBJECTIVE))
                .documentationUrl(record.getValue(programTable.DOCUMENTATION_URL))
                .brapiUrl(record.getValue(programTable.BRAPI_URL))
                .key(record.getValue(programTable.KEY))
                .createdAt(record.getValue(programTable.CREATED_AT))
                .updatedAt(record.getValue(programTable.UPDATED_AT))
                .createdBy(record.getValue(programTable.CREATED_BY))
                .updatedBy(record.getValue(programTable.UPDATED_BY))
                .active(record.getValue(programTable.ACTIVE))
                .germplasmSequence(record.getValue(programTable.GERMPLASM_SEQUENCE))
                .expSequence( record.getValue(programTable.EXP_SEQUENCE))
                .obsUnitSequence(( record.getValue(programTable.OBS_UNIT_SEQUENCE)))
                .build();

        return program;
    }

    public void setBrAPIProperties(BrAPIProgram brapiProgram) {
        this.setBrapiProgram(brapiProgram);
    }

}
