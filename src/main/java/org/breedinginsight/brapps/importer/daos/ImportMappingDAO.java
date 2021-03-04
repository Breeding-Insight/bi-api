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

package org.breedinginsight.brapps.importer.daos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.breedinginsight.brapps.importer.model.BrAPIImportMapping;
import org.breedinginsight.brapps.importer.model.BrAPIMapping;
import org.breedinginsight.brapps.importer.model.BrAPIMappingObject;
import org.breedinginsight.dao.db.tables.daos.ImportMappingDao;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class ImportMappingDAO extends ImportMappingDao {

    private DSLContext dsl;
    private ObjectMapper objectMapper;

    @Inject
    public ImportMappingDAO(Configuration config, DSLContext dsl, ObjectMapper objectMapper) {
        super(config);
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public Optional<BrAPIImportMapping> getMapping(UUID id) {
        ImportMappingEntity importMappingEntity = fetchOneById(id);
        if (importMappingEntity != null) {
            BrAPIImportMapping brAPIImportMapping = new BrAPIImportMapping(importMappingEntity);
            try {
                if (importMappingEntity.getFile() != null){
                    brAPIImportMapping.setFile(FileUtil.parseTableFromJson(importMappingEntity.getFile().toString()));
                }
                if (importMappingEntity.getMapping() != null) {
                    BrAPIMappingObject[] objects = objectMapper.readValue(importMappingEntity.getMapping().toString(), BrAPIMappingObject[].class);
                    brAPIImportMapping.setObjects(Arrays.asList(objects));
                }
            } catch (ParsingException | JsonProcessingException e) {
                throw new InternalServerException(e.toString());
            }

            return Optional.of(brAPIImportMapping);
        } else {
            return Optional.empty();
        }
    }
}
