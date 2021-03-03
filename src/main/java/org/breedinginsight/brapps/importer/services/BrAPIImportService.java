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

package org.breedinginsight.brapps.importer.services;

import io.micronaut.http.multipart.CompletedFileUpload;
import org.apache.tika.mime.MediaType;
import org.brapi.client.v2.model.exceptions.HttpBadRequestException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.model.BrAPIImportMapping;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.constants.SupportedMediaType;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnsupportedTypeException;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.JSONB;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.io.IOException;
import java.util.UUID;

public class BrAPIImportService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private MimeTypeParser mimeTypeParser;
    private ImportMappingDAO importMappingDAO;

    @Inject
    BrAPIImportService(ProgramUserService programUserService, ProgramService programService, MimeTypeParser mimeTypeParser,
                       ImportMappingDAO importMappingDAO) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.mimeTypeParser = mimeTypeParser;
        this.importMappingDAO = importMappingDAO;
    }

    public BrAPIImportMapping saveMapping(UUID programId, AuthenticatedUser actingUser, CompletedFileUpload file) throws
            DoesNotExistException, AuthorizationException, HttpBadRequestException, UnsupportedTypeException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        MediaType mediaType;
        try {
            mediaType = mimeTypeParser.getMimeType(file);
        } catch (IOException e){
            throw new HttpBadRequestException("Could not determine file type");
        }

        Table df;
        if (mediaType.toString().equals(SupportedMediaType.CSV)) {
            try {
                df = FileUtil.parseTableFromCsv(file.getInputStream());
            } catch (IOException | ParsingException e) {
                throw new HttpBadRequestException("Error parsing csv: " + e.getMessage());
            }
        } else if (mediaType.toString().equals(SupportedMediaType.XLS) ||
            mediaType.toString().equals(SupportedMediaType.XLSX)) {
            //TODO: Read excel
            df = FileUtil.parseTableFromExcel(file);
        } else {
            throw new UnsupportedTypeException("Unsupported mime type");
        }

        // Convert the table to json to store
        //TODO: Store mapping if that was passed
        String jsonString = df.write().toString("json");
        JSONB jsonb = JSONB.valueOf(jsonString);
        ImportMappingEntity importMappingEntity = ImportMappingEntity.builder()
                .programId(programId)
                .file(jsonb)
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();
        importMappingDAO.insert(importMappingEntity);

        BrAPIImportMapping importMapping = new BrAPIImportMapping(importMappingEntity);
        return importMapping;
    }
}
