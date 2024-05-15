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

package org.breedinginsight.brapps.importer.model.workflow;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import org.breedinginsight.dao.db.tables.pojos.ImporterMappingWorkflowEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.tables.ImporterMappingWorkflowTable.IMPORTER_MAPPING_WORKFLOW;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@SuperBuilder()
public class ImportMappingWorkflow extends ImporterMappingWorkflowEntity {


    public ImportMappingWorkflow(ImporterMappingWorkflowEntity importMappingWorkflowEntity) {
        this.setId(importMappingWorkflowEntity.getId());
        this.setName(importMappingWorkflowEntity.getName());
        this.setBean(importMappingWorkflowEntity.getBean());
        this.setPosition(importMappingWorkflowEntity.getPosition());
    }
    public static ImportMappingWorkflow parseSQLRecord(Record record) {

        return ImportMappingWorkflow.builder()
                .id(record.getValue(IMPORTER_MAPPING_WORKFLOW.ID))
                .name(record.getValue(IMPORTER_MAPPING_WORKFLOW.NAME))
                .bean(record.getValue(IMPORTER_MAPPING_WORKFLOW.BEAN))
                .position(record.getValue(IMPORTER_MAPPING_WORKFLOW.POSITION))
                .build();
    }
}
