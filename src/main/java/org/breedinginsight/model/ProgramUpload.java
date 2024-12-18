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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.BatchUploadEntity;
import org.jooq.Record;

import java.util.List;

import static org.breedinginsight.dao.db.Tables.BATCH_UPLOAD;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@SuperBuilder(builderMethodName = "uploadBuilder")
@JsonIgnoreProperties(value = { "createdBy", "updatedBy", "programId", "userId"})
public class ProgramUpload<T> extends BatchUploadEntity {

    private Program program;
    private User user;
    private User createdByUser;
    private User updatedByUser;

    @JsonProperty("data")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<T> parsedData;


    @JsonIgnore
    public Trait[] getDataJson() throws JsonProcessingException {
        ObjectMapper objMapper = new ObjectMapper();
        return objMapper.readValue(super.getData().data(), Trait[].class);
    }

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

    public static <T> ProgramUpload<T> parseSQLRecord(Record record) {

        return ProgramUpload.<T>uploadBuilder()
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
    }
}
