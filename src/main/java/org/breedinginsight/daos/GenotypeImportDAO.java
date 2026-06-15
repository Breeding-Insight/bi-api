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
package org.breedinginsight.daos;

import io.micronaut.http.HttpStatus;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.GenotypeImportDao;
import org.breedinginsight.dao.db.tables.pojos.GenotypeImportEntity;
import org.breedinginsight.model.GenotypeImportDetails;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class GenotypeImportDAO extends GenotypeImportDao {

    private final DSLContext dsl;

    @Inject
    public GenotypeImportDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public void createGenotypeImportLink(UUID submissionId, UUID importerImportId, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();

        insert(GenotypeImportEntity.builder()
                .id(UUID.randomUUID())
                .sampleSubmissionId(submissionId)
                .importerImportId(importerImportId)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(userId)
                .updatedBy(userId)
                .build());
    }

    public List<GenotypeImportDetails> getGenotypeImportsByProgramId(UUID programId) {
        BiUserTable sampleSubmissionCreatedByUser = BI_USER.as("sampleSubmissionCreatedByUser");
        BiUserTable genotypingImportByUser = BI_USER.as("genotypingImportByUser");

        return dsl.select(
                        SAMPLE_SUBMISSION.ID,
                        SAMPLE_SUBMISSION.NAME,
                        sampleSubmissionCreatedByUser.NAME,
                        IMPORTER_IMPORT.UPLOAD_FILE_NAME,
                        IMPORTER_IMPORT.CREATED_AT,
                        genotypingImportByUser.NAME)
                .from(GENOTYPE_IMPORT)
                .join(SAMPLE_SUBMISSION).on(GENOTYPE_IMPORT.SAMPLE_SUBMISSION_ID.eq(SAMPLE_SUBMISSION.ID))
                .join(sampleSubmissionCreatedByUser).on(SAMPLE_SUBMISSION.CREATED_BY.eq(sampleSubmissionCreatedByUser.ID))
                .join(IMPORTER_IMPORT).on(GENOTYPE_IMPORT.IMPORTER_IMPORT_ID.eq(IMPORTER_IMPORT.ID))
                .join(IMPORTER_PROGRESS).on(IMPORTER_IMPORT.IMPORTER_PROGRESS_ID.eq(IMPORTER_PROGRESS.ID))
                .join(genotypingImportByUser).on(IMPORTER_IMPORT.USER_ID.eq(genotypingImportByUser.ID))
                .where(SAMPLE_SUBMISSION.PROGRAM_ID.eq(programId))
                .and(IMPORTER_IMPORT.PROGRAM_ID.eq(programId))
                .and(IMPORTER_PROGRESS.STATUSCODE.eq((short) HttpStatus.OK.getCode()))
                .orderBy(IMPORTER_IMPORT.CREATED_AT.desc())
                .fetch(record -> GenotypeImportDetails
                        .parseSqlRecord(record, sampleSubmissionCreatedByUser, genotypingImportByUser));
    }

}