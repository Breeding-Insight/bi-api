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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.brapps.importer.model.TableConverter;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;

import tech.tablesaw.api.Table;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class BrAPIImportMapping {
    private UUID id;
    private String name;
    private String importTypeId;
    private List<BrAPIMappingField> mapping;
    @JsonSerialize(converter = TableConverter.class)
    private Table file;
    private Boolean draft;


    public BrAPIImportMapping(ImportMappingEntity importMappingEntity) {
        this.id = importMappingEntity.getId();
        this.name = importMappingEntity.getName();
        this.importTypeId = importMappingEntity.getImportTypeId();
        this.draft = importMappingEntity.getDraft();
    }
}
