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
        this.setCreatedAt(programEntity.getCreatedAt());
        this.setUpdatedAt(programEntity.getUpdatedAt());
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
                .createdAt(record.getValue(PROGRAM.CREATED_AT))
                .updatedAt(record.getValue(PROGRAM.UPDATED_AT))
                .createdBy(record.getValue(PROGRAM.CREATED_BY))
                .updatedBy(record.getValue(PROGRAM.UPDATED_BY))
                .active(record.getValue(PROGRAM.ACTIVE))
                .build();

        return program;
    }

}
