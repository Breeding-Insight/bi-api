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

package org.breedinginsight.brapps.importer.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;
import tech.tablesaw.api.Table;

import java.util.UUID;

public class BrAPIImportMapping {
    private UUID id;
    //TODO: This should be an actual structure?
    //private JSONB mapping;
    @JsonSerialize(converter = TableConverter.class)
    private Table file;

    public BrAPIImportMapping(ImportMappingEntity importMappingEntity){
        this.id = importMappingEntity.getId();
        //this.mapping = importMappingEntity.getMapping();
        // Read the json to make a table
        this.file = Table.read().string(importMappingEntity.getFile().toString(), "json");
    }

}
