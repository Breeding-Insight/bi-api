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

import io.micronaut.http.server.exceptions.InternalServerException;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.ImporterImportDao;
import org.breedinginsight.dao.db.tables.daos.ImporterProgressDao;
import org.breedinginsight.dao.db.tables.pojos.ImporterImportEntity;
import org.breedinginsight.dao.db.tables.pojos.ImporterProgressEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.records.ImporterImportRecord;
import org.breedinginsight.dao.db.tables.records.ProgramRecord;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

public interface ImportDAO extends DAO<ImporterImportRecord, ImporterImportEntity, UUID> {

    Optional<ImportUpload> getUploadById(UUID id);
    List<ImportUpload> getProgramUploads(UUID programId);

    void update(ImportUpload upload);

    void updateProgress(ImportProgress importProgress);

    void createProgress(ImportProgress importProgress);
}
