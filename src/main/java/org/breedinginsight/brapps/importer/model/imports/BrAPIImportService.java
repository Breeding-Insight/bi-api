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

package org.breedinginsight.brapps.importer.model.imports;

import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;

import java.util.List;

public interface BrAPIImportService {
    String getImportTypeId();
    BrAPIImport getImportClass();
    List<ImportWorkflow> getWorkflows();
    default String getInvalidIntegerMsg(String columnName) {
        return String.format("Column name \"%s\" must be integer type, but non-integer type provided.", columnName);
    }
    default String getBlankRequiredFieldMsg(String fieldName) {
        return String.format("Required field \"%s\" cannot contain empty values", fieldName);
    }
    default String getMissingColumnMsg(String columnName) {
        return String.format("Column name \"%s\" does not exist in file", columnName);
    }
    default String getMissingUserInputMsg(String fieldName) {
        return String.format("User input, \"%s\" is required", fieldName);
    }
    default String getWrongUserInputDataTypeMsg(String fieldName, String typeName) {
        return String.format("User input, \"%s\" must be an %s", fieldName, typeName);
    }
    ImportPreviewResponse process(ImportServiceContext context)
            throws Exception;
}
