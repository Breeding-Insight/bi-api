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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.BatchUploadEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.BATCH_UPLOAD;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "createdBy", "updatedBy"})
public class ProgramUpload extends BatchUploadEntity {

    public ProgramUpload(BatchUploadEntity uploadEntity) {
        this.setId(uploadEntity.getId());
        this.setProgramId(uploadEntity.getProgramId());
        this.setUserId(uploadEntity.getUserId());
        this.setData(uploadEntity.getData());
        this.setType(uploadEntity.getType());
        this.setCreatedAt(uploadEntity.getCreatedAt());
        this.setUpdatedAt(uploadEntity.getUpdatedAt());
        this.setCreatedBy(uploadEntity.getCreatedBy());
        this.setUpdatedBy(uploadEntity.getUpdatedBy());
    }

    public static ProgramUpload parseSQLRecord(Record record) {

        ProgramUpload upload = ProgramUpload.builder()
                .id(record.getValue(BATCH_UPLOAD.ID))
                .programId(record.getValue(BATCH_UPLOAD.PROGRAM_ID))
                .userId(record.getValue(BATCH_UPLOAD.USER_ID))
                .data(record.getValue(BATCH_UPLOAD.DATA))
                .type(record.getValue(BATCH_UPLOAD.TYPE))
                .createdAt(record.getValue(BATCH_UPLOAD.CREATED_AT))
                .updatedAt(record.getValue(BATCH_UPLOAD.UPDATED_AT))
                .createdBy(record.getValue(BATCH_UPLOAD.CREATED_BY))
                .updatedBy(record.getValue(BATCH_UPLOAD.UPDATED_BY))
                .build();

        return upload;
    }

}
