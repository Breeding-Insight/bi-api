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

import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Table;

import java.util.List;

public abstract class BrAPIImportService {
    public String getImportTypeId() {return null;}
    public BrAPIImport getImportClass() {return null;}
    public ImportPreviewResponse processAsync(List<BrAPIImport> brAPIImports, Table data, Program program,
                                              ProgramUpload upload, AuthenticatedUser actingUser, Boolean commit) {return null;}
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, ProgramUpload upload, Boolean commit)
            throws UnprocessableEntityException, DoesNotExistException {return null;}
}
