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
import org.breedinginsight.brapps.importer.model.BrAPIImportConfigManager;
import org.breedinginsight.brapps.importer.model.config.ImportConfig;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.mapping.BrAPIImportMapping;
import org.breedinginsight.brapps.importer.model.mapping.BrAPIMappingManager;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.dao.db.tables.pojos.ImportMappingEntity;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
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
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIFileImportService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private MimeTypeParser mimeTypeParser;
    private ImportMappingDAO importMappingDAO;
    private ObjectMapper objectMapper;
    private BrAPIMappingManager mappingManager;
    private BrAPIImportConfigManager configManager;

    @Inject
    BrAPIFileImportService(ProgramUserService programUserService, ProgramService programService, MimeTypeParser mimeTypeParser,
                           ImportMappingDAO importMappingDAO, ObjectMapper objectMapper, BrAPIMappingManager mappingManager,
                           BrAPIImportConfigManager configManager) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.mimeTypeParser = mimeTypeParser;
        this.importMappingDAO = importMappingDAO;
        this.objectMapper = objectMapper;
        this.mappingManager = mappingManager;
        this.configManager = configManager;
    }

    public List<ImportConfig> getAllImportTypeConfigs() {
        return configManager.getAllImportTypeConfigs();
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

        // If validate is true, validate the mapping
        if (validate) {
            mappingManager.map(mappingRequest, importMapping.getFile());
        }

        // Save the mapping
        String json = null;
        try {
            json = objectMapper.writeValueAsString(mappingRequest.getMapping());
        } catch(JsonProcessingException e) {
            log.error("Problem converting traits json", e);
            // If we didn't catch this in parsing to the class, this is a server error
            throw new InternalServerException("Problem converting mapping json", e);
        }

        ImportMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setName(mappingRequest.getName());
        importMappingEntity.setImportTypeId(mappingRequest.getImportTypeId());
        importMappingEntity.setMapping(JSONB.valueOf(json));
        importMappingEntity.setDraft(mappingRequest.getDraft());
        importMappingDAO.update(importMappingEntity);

        Optional<BrAPIImportMapping> updatedMapping = importMappingDAO.getMapping(importMappingEntity.getId());

        return updatedMapping.get();
    }

    public Boolean exists(UUID mappingId) {
        return importMappingDAO.existsById(mappingId);
    }

    public List<BrAPIImport> uploadData(UUID programId, UUID mappingId, AuthenticatedUser actingUser, CompletedFileUpload file, Boolean commit)
            throws DoesNotExistException, AuthorizationException, UnsupportedTypeException, HttpBadRequestException, UnprocessableEntityException {

        Optional<Program> optionalProgram = programService.getById(programId);
        if (!optionalProgram.isPresent())
        {
            throw new DoesNotExistException("Program id does not exist");
        }
        Program program = optionalProgram.get();

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        // Find the mapping
        Optional<BrAPIImportMapping> optionalMapping = importMappingDAO.getMapping(mappingId);
        if (optionalMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that id does not exist");
        }
        BrAPIImportMapping importMapping = optionalMapping.get();

        Optional<BrAPIImportService> optionalImportService = configManager.getImportServiceById(importMapping.getImportTypeId());
        if (optionalImportService.isEmpty()) {
            throw new DoesNotExistException("Config with that id does not exist");
        }
        BrAPIImportService importService = optionalImportService.get();

        // Read the file
        //TODO: Get better errors on this
        Table data = parseUploadedFile(file);

        //TODO: Get better errors on this
        List<BrAPIImport> brAPIImportList = mappingManager.map(importMapping, data);

        if (commit) {
            //TODO: Make this static if we can
            importService.process(brAPIImportList, data, program);
        }

        return brAPIImportList;
    }

    public List<BrAPIImportMapping> getAllMappings(UUID programId, AuthenticatedUser actingUser, Boolean draft)
            throws DoesNotExistException, AuthorizationException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        List<BrAPIImportMapping> brAPIImportMappings = importMappingDAO.getAllMappings(programId, draft);

        return brAPIImportMappings;
    }
}
