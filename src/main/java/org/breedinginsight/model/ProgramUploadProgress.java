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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.BatchUploadProgressEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.BATCH_UPLOAD;
import static org.breedinginsight.dao.db.Tables.BATCH_UPLOAD_PROGRESS;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class ProgramUploadProgress extends BatchUploadProgressEntity {

    public static ProgramUploadProgress parseSQLRecord(Record record) {

        return ProgramUploadProgress.builder()
                .id(record.getValue(BATCH_UPLOAD_PROGRESS.ID))
                .statuscode(record.getValue(BATCH_UPLOAD_PROGRESS.STATUSCODE))
                .total(record.getValue(BATCH_UPLOAD_PROGRESS.TOTAL))
                .finished(record.getValue(BATCH_UPLOAD_PROGRESS.FINISHED))
                .inProgress(record.getValue(BATCH_UPLOAD_PROGRESS.IN_PROGRESS))
                .build();
    }
}
