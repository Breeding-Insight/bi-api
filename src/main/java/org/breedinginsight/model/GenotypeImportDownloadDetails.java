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
import org.jooq.Record;

import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.GENOTYPE_IMPORT;
import static org.breedinginsight.dao.db.Tables.IMPORTER_IMPORT;

@Getter
@Setter
@Accessors(chain = true)
@ToString
@SuperBuilder
@NoArgsConstructor
@Introspected
@Jacksonized
public class GenotypeImportDownloadDetails {
    private UUID sampleSubmissionId;
    private UUID importerImportId;
    private String genotypeFileName;

    public static GenotypeImportDownloadDetails parseSqlRecord(Record record) {
        return GenotypeImportDownloadDetails.builder()
                .sampleSubmissionId(record.get(GENOTYPE_IMPORT.SAMPLE_SUBMISSION_ID))
                .importerImportId(record.get(GENOTYPE_IMPORT.IMPORTER_IMPORT_ID))
                .genotypeFileName(record.get(IMPORTER_IMPORT.UPLOAD_FILE_NAME))
                .build();
    }
}