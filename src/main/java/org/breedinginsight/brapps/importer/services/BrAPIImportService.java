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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MediaType;
import org.brapi.client.v2.model.exceptions.HttpBadRequestException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.model.BrAPIImportMapping;
import org.breedinginsight.brapps.importer.model.BrAPIMapping;
import org.breedinginsight.brapps.importer.model.BrAPIMappingManager;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.constants.SupportedMediaType;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.UnsupportedTypeException;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.JSONB;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.io.IOException;
import java.rmi.ServerException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class BrAPIImportService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private MimeTypeParser mimeTypeParser;
    private ImportMappingDAO importMappingDAO;
    private ObjectMapper objectMapper;
    private BrAPIMappingManager mappingManager;

    @Inject
    BrAPIImportService(ProgramUserService programUserService, ProgramService programService, MimeTypeParser mimeTypeParser,
                       ImportMappingDAO importMappingDAO, ObjectMapper objectMapper, BrAPIMappingManager mappingManager) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.mimeTypeParser = mimeTypeParser;
        this.importMappingDAO = importMappingDAO;
        this.objectMapper = objectMapper;
        this.mappingManager = mappingManager;
    }

    /*
        Saves the file for the mapping record
     */
    public BrAPIImportMapping createMapping(UUID programId, AuthenticatedUser actingUser, CompletedFileUpload file) throws
            DoesNotExistException, AuthorizationException, HttpBadRequestException, UnsupportedTypeException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        Table df = parseUploadedFile(file);

        // Convert the table to json to store
        String jsonString = df.write().toString("json");
        JSONB jsonb = JSONB.valueOf(jsonString);
        ImportMappingEntity importMappingEntity = ImportMappingEntity.builder()
                .programId(programId)
                .file(jsonb)
                .draft(true)
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();
        importMappingDAO.insert(importMappingEntity);

        BrAPIImportMapping importMapping = importMappingDAO.getMapping(importMappingEntity.getId()).get();

        return importMapping;
    }

    private Table parseUploadedFile(CompletedFileUpload file) throws HttpBadRequestException, UnsupportedTypeException {

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

            try {
                //TODO: Allow them to pass in header row index in the future
                df = FileUtil.parseTableFromExcel(file.getInputStream(), 0);
            } catch (IOException | ParsingException e) {
                throw new HttpBadRequestException("Error parsing excel: " + e.getMessage());
            }
        } else {
            throw new UnsupportedTypeException("Unsupported mime type");
        }
        return df;
    }

    public BrAPIImportMapping updateMappingFile(UUID programId, UUID mappingId, AuthenticatedUser actingUser, CompletedFileUpload file)
            throws UnsupportedTypeException, HttpBadRequestException, DoesNotExistException, AuthorizationException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        BrAPIImportMapping importMapping;
        Optional<BrAPIImportMapping> optionalImportMapping = importMappingDAO.getMapping(mappingId);

        Table df = parseUploadedFile(file);

        //TODO: Validate the new file works with the mapping, if validate is specified

        // Convert the table to json to store
        String jsonString = df.write().toString("json");
        JSONB jsonb = JSONB.valueOf(jsonString);
        ImportMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setFile(jsonb);
        importMappingDAO.update(importMappingEntity);

        importMapping = importMappingDAO.getMapping(importMappingEntity.getId()).get();

        return importMapping;
    }

    public BrAPIImportMapping updateMapping(UUID programId, AuthenticatedUser actingUser, UUID mappingId,
                                            BrAPIImportMapping mappingRequest, Boolean validate) throws
            DoesNotExistException, AuthorizationException, HttpBadRequestException, UnsupportedTypeException, UnprocessableEntityException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        Optional<BrAPIImportMapping> optionalImportMapping = importMappingDAO.getMapping(mappingId);
        if (optionalImportMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that id does not exist");
        }
        BrAPIImportMapping importMapping = optionalImportMapping.get();

        mappingRequest.setFile(importMapping.getFile());

        // If validate is true, validate the mapping
        if (validate) {
            mappingManager.map(mappingRequest);
        }

        // Save the mapping
        String json = null;
        try {
            json = objectMapper.writeValueAsString(mappingRequest.getObjects());
        } catch(JsonProcessingException e) {
            log.error("Problem converting traits json", e);
            // If we didn't catch this in parsing to the class, this is a server error
            throw new InternalServerException("Problem converting mapping json", e);
        }

        ImportMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setName(mappingRequest.getName());
        importMappingEntity.setImportTypeId(mappingRequest.getImportTypeId());
        importMappingEntity.setMapping(JSONB.valueOf(json));
        importMappingDAO.update(importMappingEntity);

        return importMapping;
    }

    public Boolean exists(UUID mappingId) {
        return importMappingDAO.existsById(mappingId);
    }
}
