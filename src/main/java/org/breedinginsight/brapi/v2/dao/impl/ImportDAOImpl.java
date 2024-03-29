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

package org.breedinginsight.brapi.v2.dao.impl;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.ImporterImportDao;
import org.breedinginsight.dao.db.tables.daos.ImporterProgressDao;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ImportDAOImpl extends ImporterImportDao implements ImportDAO {

    private DSLContext dsl;
    private ImporterProgressDao progressDao;

    @Inject
    public ImportDAOImpl(Configuration config, DSLContext dsl, ImporterProgressDao importerProgressDao) {
        super(config);
        this.dsl = dsl;
        this.progressDao = importerProgressDao;
    }

    public Optional<ImportUpload> getUploadById(UUID id) {
        List<Record> records = getUploadsQuery()
                .where(IMPORTER_IMPORT.ID.eq(id))
                .fetch();

        if (records.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(parseRecords(records).get(0));
        }
    }

    public List<ImportUpload> getProgramUploads(UUID programId) {
        List<Record> records = getUploadsQuery().where(IMPORTER_IMPORT.PROGRAM_ID.eq(programId)).fetch();

        return parseRecords(records);
    }

    public void update(ImportUpload upload) {
        super.update(upload);
        upload.setUpdatedAt(OffsetDateTime.now());
        upload.getProgress().setUpdatedAt(upload.getUpdatedAt());
        progressDao.update(upload.getProgress());
    }

    private List<ImportUpload> parseRecords(List<Record> records) {

        List<ImportUpload> resultUploads = new ArrayList<>();
        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // Parse the result
        for (Record record : records) {
            ImportUpload upload = ImportUpload.parseSQLRecord(record);
            upload.setProgram(Program.parseSQLRecord(record));
            upload.setUser(User.parseSQLRecord(record));
            upload.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
            upload.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
            upload.setProgress(ImportProgress.parseSQLRecord(record));
            upload.setMapping(ImportMapping.parseSQLRecord(record));
            // Parse our file data and modified data into a data table
            try {
                if (upload.getFileData() != null) {
                    upload.setFileDataTable(FileUtil.parseTableFromJson(upload.getFileData().toString()));
                }
                if (upload.getModifiedData() != null) {
                    upload.setModifiedDataTable(FileUtil.parseTableFromJson(upload.getModifiedData().toString()));
                }
            } catch (ParsingException e) {
                throw new InternalServerException(e.toString(), e);
            }
            resultUploads.add(upload);
        }
        return resultUploads;
    }

    private SelectOnConditionStep<Record> getUploadsQuery() {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        return dsl.select()
                .from(IMPORTER_IMPORT)
                .leftJoin(IMPORTER_PROGRESS).on(IMPORTER_IMPORT.IMPORTER_PROGRESS_ID.eq(IMPORTER_PROGRESS.ID))
                .innerJoin(PROGRAM).on(IMPORTER_IMPORT.PROGRAM_ID.eq(PROGRAM.ID))
                .innerJoin(BI_USER).on(IMPORTER_IMPORT.USER_ID.eq(BI_USER.ID))
                .innerJoin(IMPORTER_MAPPING).on(IMPORTER_IMPORT.IMPORTER_MAPPING_ID.eq(IMPORTER_MAPPING.ID))
                .innerJoin(createdByUser).on(IMPORTER_IMPORT.CREATED_BY.eq(createdByUser.ID))
                .innerJoin(updatedByUser).on(IMPORTER_IMPORT.UPDATED_BY.eq(updatedByUser.ID));
    }

    public void updateProgress(ImportProgress importProgress) {
        importProgress.setUpdatedAt(OffsetDateTime.now());
        progressDao.update(importProgress);
    }

    public void createProgress(ImportProgress importProgress) {
        importProgress.setCreatedAt(OffsetDateTime.now());
        importProgress.setUpdatedAt(OffsetDateTime.now());
        progressDao.insert(importProgress);
    }
}
