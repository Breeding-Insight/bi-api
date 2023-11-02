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
import org.breedinginsight.dao.db.tables.records.ImporterMappingRecord;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.Configuration;
import org.jooq.DAO;
import org.jooq.DSLContext;
import org.jooq.Record;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static org.breedinginsight.dao.db.Tables.*;

public interface ImportMappingDAO extends DAO<ImporterMappingRecord, ImporterMappingEntity, UUID> {

    Optional<ImportMapping> getMapping(UUID id);

    List<ImportMapping> getAllProgramMappings(UUID programId, Boolean draft);

    List<ImportMapping> getProgramMappingsByName(UUID programId, String name);

    List<ImportMapping> getAllSystemMappings();

    List<ImportMapping> getSystemMappingByName(String name);

    ImporterMappingEntity fetchOneById(UUID value);
}
