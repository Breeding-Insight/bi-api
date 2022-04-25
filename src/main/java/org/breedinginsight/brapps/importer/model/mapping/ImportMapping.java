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

package org.breedinginsight.brapps.importer.model.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.brapps.importer.model.TableConverter;

import org.breedinginsight.dao.db.tables.pojos.ImporterMappingEntity;
import org.jooq.Record;
import tech.tablesaw.api.Table;
import java.util.List;

import static org.breedinginsight.dao.db.tables.ImporterMappingTable.IMPORTER_MAPPING;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@SuperBuilder()
public class ImportMapping extends ImporterMappingEntity {
    @JsonProperty("mapping")
    private List<MappingField> mappingConfig;

    public ImportMapping(ImporterMappingEntity importMappingEntity) {
        this.setId(importMappingEntity.getId());
        this.setName(importMappingEntity.getName());
        this.setImporterTemplateId(importMappingEntity.getImporterTemplateId());
    }

    public static ImportMapping parseSQLRecord(Record record) {

        return ImportMapping.builder()
                .id(record.getValue(IMPORTER_MAPPING.ID))
                .importerTemplateId(record.getValue(IMPORTER_MAPPING.IMPORTER_TEMPLATE_ID))
                .name(record.getValue(IMPORTER_MAPPING.NAME))
                .mapping(record.getValue(IMPORTER_MAPPING.MAPPING))
                .createdAt(record.getValue(IMPORTER_MAPPING.CREATED_AT))
                .updatedAt(record.getValue(IMPORTER_MAPPING.UPDATED_AT))
                .createdBy(record.getValue(IMPORTER_MAPPING.CREATED_BY))
                .updatedBy(record.getValue(IMPORTER_MAPPING.UPDATED_BY))
                .build();
    }
}
