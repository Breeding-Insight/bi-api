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

import io.micronaut.http.server.types.files.StreamedFile;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
public class DownloadFile {
    @NotNull
    private String fileName;
    @NotNull
    private StreamedFile streamedFile;

    public DownloadFile(String fileName, StreamedFile streamedFile) {
        this.setFileName(fileName);
        this.setStreamedFile(streamedFile);
    }

}
