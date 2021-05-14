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
package org.breedinginsight.brapps.importer.model.imports.study;

import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIProgramDAO;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class StudyImportService extends BrAPIImportService {

    private String IMPORT_TYPE_ID = "StudyImport";

    private BrAPIGermplasmDAO brAPIGermplasmDAO;
    private BrAPIProgramDAO brAPIProgramDAO;
    private FileMappingUtil fileMappingUtil;

    @Inject
    public StudyImportService(FileMappingUtil fileMappingUtil,
                              BrAPIProgramDAO brAPIProgramDAO, BrAPIGermplasmDAO brAPIGermplasmDAO)
    {
        this.fileMappingUtil = fileMappingUtil;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIProgramDAO = brAPIProgramDAO;
    }

    @Override
    public StudyImport getImportClass() {
        return new StudyImport();
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

    @Override
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, Boolean commit)
            throws UnprocessableEntityException {

        ImportPreviewResponse response = new ImportPreviewResponse();
        return response;
    }
}

