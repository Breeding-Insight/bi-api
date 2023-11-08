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

package org.breedinginsight.brapps.importer.model.exports;

import lombok.Getter;

/**
Defines MediaType parameters for file export
 */
@Getter
public enum FileType {
    XLS("xls", ".xls", "application/vnd.ms-excel"),
    XLSX("xlsx", ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", ".csv", "text/csv"),
    // NOTE: MIME type is not application/zip because Micronaut doesn't natively support it.
    ZIP("zip", ".zip", "application/octet-stream");
    private String name;
    private String extension;
    private String mimeType;

    FileType(String name, String extension, String mimeType) {
        this.name = name;
        this.extension = extension;
        this.mimeType = mimeType;
    }
}