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
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.mapping.MappingField;
import org.breedinginsight.dao.db.tables.daos.ImporterMappingDao;
import org.breedinginsight.dao.db.tables.pojos.ImporterMappingEntity;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ImportMappingDAO extends ImporterMappingDao {

    private DSLContext dsl;
    private ObjectMapper objectMapper;

    @Inject
    public ImportMappingDAO(Configuration config, DSLContext dsl, ObjectMapper objectMapper) {
        super(config);
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public Optional<ImportMapping> getMapping(UUID id) {
        ImporterMappingEntity importMappingEntity = fetchOneById(id);
        if (importMappingEntity != null) {
            ImportMapping importMapping = parseBrAPIImportMapping(importMappingEntity);
            return Optional.of(importMapping);
        } else {
            return Optional.empty();
        }
    }

    public List<ImportMapping> getAllProgramMappings(UUID programId, Boolean draft) {

        List<Record> records = dsl.select()
                .from(IMPORTER_MAPPING)
                .join(IMPORTER_MAPPING_PROGRAM).on(
                        IMPORTER_MAPPING_PROGRAM.PROGRAM_ID.eq(programId).and(
                        IMPORTER_MAPPING.ID.eq(IMPORTER_MAPPING_PROGRAM.IMPORTER_MAPPING_ID)))
                .where(IMPORTER_MAPPING.DRAFT.eq(draft))
                .fetch();

        List<ImportMapping> mappings = new ArrayList<>();
        for (Record record: records) {
            mappings.add(ImportMapping.parseSQLRecord(record));
        }
        return mappings;
    }

    public List<ImportMapping> getProgramMappingsByName(UUID programId, String name) {
        List<Record> records = dsl.select()
                .from(IMPORTER_MAPPING)
                .join(IMPORTER_MAPPING_PROGRAM).on(
                        IMPORTER_MAPPING_PROGRAM.PROGRAM_ID.eq(programId).and(
                                IMPORTER_MAPPING.ID.eq(IMPORTER_MAPPING_PROGRAM.IMPORTER_MAPPING_ID)))
                .where(IMPORTER_MAPPING.NAME.equalIgnoreCase(name))
                .fetch();

        List<ImportMapping> mappings = new ArrayList<>();
        for (Record record: records) {
            mappings.add(ImportMapping.parseSQLRecord(record));
        }
        return mappings;
    }

    public List<ImportMapping> getAllSystemMappings() {
        List<Record> records = dsl.select()
                .from(IMPORTER_MAPPING)
                .leftJoin(IMPORTER_MAPPING_PROGRAM).on(
                        IMPORTER_MAPPING.ID.eq(IMPORTER_MAPPING_PROGRAM.IMPORTER_MAPPING_ID))
                .where(IMPORTER_MAPPING_PROGRAM.ID.isNull())
                .fetch();

        List<ImportMapping> mappings = new ArrayList<>();
        for (Record record: records) {
            mappings.add(ImportMapping.parseSQLRecord(record));
        }
        return mappings;
    }

    public List<ImportMapping> getSystemMappingByName(String name) {
        List<Record> records = dsl.select()
                .from(IMPORTER_MAPPING)
                .leftJoin(IMPORTER_MAPPING_PROGRAM).on(
                        IMPORTER_MAPPING.ID.eq(IMPORTER_MAPPING_PROGRAM.IMPORTER_MAPPING_ID))
                .where(
                    IMPORTER_MAPPING_PROGRAM.ID.isNull().and(
                    IMPORTER_MAPPING.NAME.equalIgnoreCase(name)))
                .fetch();

        List<ImportMapping> mappings = new ArrayList<>();
        for (Record record: records) {
            mappings.add(ImportMapping.parseSQLRecord(record));
        }
        return mappings;
    }

    private ImportMapping parseBrAPIImportMapping(ImporterMappingEntity importMappingEntity) {

        ImportMapping importMapping = new ImportMapping(importMappingEntity);
        try {
            if (importMappingEntity.getFile() != null){
                importMapping.setFileTable(FileUtil.parseTableFromJson(importMappingEntity.getFile().toString()));
            }
            if (importMappingEntity.getMapping() != null) {
                MappingField[] mappingFields = objectMapper.readValue(importMappingEntity.getMapping().toString(), MappingField[].class);
                importMapping.setMappingConfig(Arrays.asList(mappingFields));
            }
        } catch (ParsingException | JsonProcessingException e) {
            throw new InternalServerException(e.toString());
        }

        return importMapping;
    }
}
