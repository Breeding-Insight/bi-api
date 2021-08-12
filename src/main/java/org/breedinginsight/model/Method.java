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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.brapi.v2.model.pheno.BrAPIMethod;
import org.breedinginsight.dao.db.tables.pojos.MethodEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.METHOD;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "id", "programOntologyId", "updatedBy", "createdBy", "updatedAt", "createdAt" })
public class Method extends MethodEntity {

    public static final String COMPUTATION_TYPE = "Computation";

    private ProgramOntology programOntology;

    // BrAPI Properties
    private String methodClass;
    private String description;
    private String formula;

    public Method(MethodEntity methodEntity) {
        this.setId(methodEntity.getId());
        this.setProgramOntologyId(methodEntity.getProgramOntologyId());
        this.setCreatedAt(methodEntity.getCreatedAt());
        this.setCreatedBy(methodEntity.getCreatedBy());
        this.setUpdatedAt(methodEntity.getUpdatedAt());
        this.setUpdatedBy(methodEntity.getUpdatedBy());
    }

    public static Method parseSqlRecord(Record record) {
        return Method.builder()
            .id(record.getValue(METHOD.ID))
            .programOntologyId(record.getValue(METHOD.PROGRAM_ONTOLOGY_ID))
            .createdAt(record.getValue(METHOD.CREATED_AT))
            .createdBy(record.getValue(METHOD.CREATED_BY))
            .updatedAt(record.getValue(METHOD.UPDATED_AT))
            .updatedBy(record.getValue(METHOD.UPDATED_BY))
            .build();
    }

    public void setBrAPIProperties(BrAPIMethod brApiMethod){
        this.setMethodClass(brApiMethod.getMethodClass());
        this.setDescription(brApiMethod.getDescription());
        this.setFormula(brApiMethod.getFormula());
        this.setMethodName(brApiMethod.getDescription().toString() + " " + brApiMethod.getMethodClass().toString());
    }
}
