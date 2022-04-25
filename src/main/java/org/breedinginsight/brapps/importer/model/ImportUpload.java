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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.brapps.importer.model.mapping.MappingField;
import org.breedinginsight.dao.db.tables.pojos.ImporterUploadEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.jooq.Record;
import tech.tablesaw.api.Table;

import java.util.List;
import java.util.Map;

import static org.breedinginsight.dao.db.Tables.IMPORTER_UPLOAD;


@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@SuperBuilder(builderMethodName = "uploadBuilder")
public class ImportUpload extends ImporterUploadEntity {

    private Program program;
    private User user;
    private User createdByUser;
    private User updatedByUser;
    private ImportProgress progress;

    private Table fileDataTable;
    private Table modifiedDataTable;
    private List<MappingField> mappedDataObjects;

    @JsonProperty("preview")
    public Map<String, Object> getPreview() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(super.getMappedData().data(), Map.class);
    }

    public void updateProgress(Integer finished, Integer inProgress) {
        progress.setFinished((long) finished);
        progress.setInProgress((long) inProgress);
    }

    public static ImportUpload parseSQLRecord(Record record) {

        return ImportUpload.uploadBuilder()
                .id(record.getValue(IMPORTER_UPLOAD.ID))
                .programId(record.getValue(IMPORTER_UPLOAD.PROGRAM_ID))
                .importerMappingId(record.getValue(IMPORTER_UPLOAD.IMPORTER_MAPPING_ID))
                .importerProgressId(record.getValue(IMPORTER_UPLOAD.IMPORTER_PROGRESS_ID))
                .uploadFileName(record.getValue(IMPORTER_UPLOAD.UPLOAD_FILE_NAME))
                .mappedData(record.getValue(IMPORTER_UPLOAD.MAPPED_DATA))
                .createdAt(record.getValue(IMPORTER_UPLOAD.CREATED_AT))
                .updatedAt(record.getValue(IMPORTER_UPLOAD.UPDATED_AT))
                .createdBy(record.getValue(IMPORTER_UPLOAD.CREATED_BY))
                .updatedBy(record.getValue(IMPORTER_UPLOAD.UPDATED_BY))
                .build();
    }
}
