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

package org.breedinginsight.brapps.importer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ImporterProgressEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.tables.ImporterProgressTable.IMPORTER_PROGRESS;


@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class ImportProgress extends ImporterProgressEntity {

    public static ImportProgress parseSQLRecord(Record record) {

        return ImportProgress.builder()
                .id(record.getValue(IMPORTER_PROGRESS.ID))
                .statuscode(record.getValue(IMPORTER_PROGRESS.STATUSCODE))
                .message(record.getValue(IMPORTER_PROGRESS.MESSAGE))
                .total(record.getValue(IMPORTER_PROGRESS.TOTAL))
                .finished(record.getValue(IMPORTER_PROGRESS.FINISHED))
                .inProgress(record.getValue(IMPORTER_PROGRESS.IN_PROGRESS))
                .build();
    }
}
