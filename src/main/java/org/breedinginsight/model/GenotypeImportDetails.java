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

package org.breedinginsight.model;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.jooq.Record;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.IMPORTER_IMPORT;
import static org.breedinginsight.dao.db.Tables.SAMPLE_SUBMISSION;
import static org.breedinginsight.dao.db.Tables.GENOTYPE_IMPORT;

@Getter
@Setter
@Accessors(chain = true)
@ToString
@SuperBuilder
@NoArgsConstructor
@Introspected
@Jacksonized
public class GenotypeImportDetails {
    private UUID genotypeImportId;
    private UUID sampleSubmissionId;
    private String projectNameForSampleSubmission;
    private String sampleSubmissionCreatedBy;
    private String genotypingFileName;
    private OffsetDateTime genotypingImportDate;
    private String genotypingImportBy;

    public static GenotypeImportDetails parseSqlRecord(Record record,
                                                       BiUserTable sampleSubmissionCreatedByUser,
                                                       BiUserTable genotypingImportByUser) {
        return GenotypeImportDetails.builder()
                .genotypeImportId(record.get(GENOTYPE_IMPORT.ID))
                .sampleSubmissionId(record.get(SAMPLE_SUBMISSION.ID))
                .projectNameForSampleSubmission(record.get(SAMPLE_SUBMISSION.NAME))
                .sampleSubmissionCreatedBy(record.get(sampleSubmissionCreatedByUser.NAME))
                .genotypingFileName(record.get(IMPORTER_IMPORT.UPLOAD_FILE_NAME))
                .genotypingImportDate(record.get(IMPORTER_IMPORT.CREATED_AT))
                .genotypingImportBy(record.get(genotypingImportByUser.NAME))
                .build();
    }
}
