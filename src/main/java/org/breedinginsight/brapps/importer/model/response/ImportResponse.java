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

package org.breedinginsight.brapps.importer.model.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.Setter;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.model.User;
import org.jooq.JSONB;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class ImportResponse {
    private UUID importId;
    private ImportProgress progress;
    // Since we are only ever returning the preview, don't worry about trying to deserialize it from the db
    private JSONB preview;
    private UUID importMappingId;
    private String importMappingName;
    private String importType;
    private String uploadFileName;
    private User createdByUser;
    private User updatedByUser;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @JsonRawValue
    public String getPreview() {
        return preview != null ? preview.data() : null;
    }
}
