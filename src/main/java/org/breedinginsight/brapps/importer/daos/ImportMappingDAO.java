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

import org.breedinginsight.brapps.importer.model.BrAPIImportMapping;
import org.breedinginsight.dao.db.tables.daos.ImportMappingDao;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class ImportMappingDAO extends ImportMappingDao {

    private DSLContext dsl;

    @Inject
    public ImportMappingDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public Optional<BrAPIImportMapping> getMapping(UUID id) throws IOException {
        ImportMappingEntity importMappingEntity = fetchOneById(id);
        if (importMappingEntity != null) {
            return Optional.of(new BrAPIImportMapping(importMappingEntity));
        } else {
            return Optional.empty();
        }
    }
}
