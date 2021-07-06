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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MediaType;
import org.brapi.v2.model.core.BrAPIProgram;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.config.ImportConfigResponse;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.dao.db.tables.pojos.ImporterMappingEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.constants.SupportedMediaType;
import org.breedinginsight.services.exceptions.*;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class FileImportService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private MimeTypeParser mimeTypeParser;
    private ImportMappingDAO importMappingDAO;
    private ObjectMapper objectMapper;
    private MappingManager mappingManager;
    private ImportConfigManager configManager;
    private ImportDAO importDAO;
    private DSLContext dsl;

    @Inject
    FileImportService(ProgramUserService programUserService, ProgramService programService, MimeTypeParser mimeTypeParser,
                      ImportMappingDAO importMappingDAO, ObjectMapper objectMapper, MappingManager mappingManager,
                      ImportConfigManager configManager, ImportDAO importDAO, DSLContext dsl) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.mimeTypeParser = mimeTypeParser;
        this.importMappingDAO = importMappingDAO;
        this.objectMapper = objectMapper;
        this.mappingManager = mappingManager;
        this.configManager = configManager;
        this.importDAO = importDAO;
        this.dsl = dsl;
    }

    public List<ImportConfigResponse> getAllImportTypeConfigs() {
        return configManager.getAllImportTypeConfigs();
    }
    /*
        Saves the file for the mapping record
     */
    public ImportMapping createMapping(UUID programId, AuthenticatedUser actingUser, CompletedFileUpload file) throws
            DoesNotExistException, AuthorizationException, UnsupportedTypeException {

        Program program = validateRequest(programId, actingUser);

        Table df = parseUploadedFile(file);

        // Convert the table to json to store
        String jsonString = df.write().toString("json");
        JSONB jsonb = JSONB.valueOf(jsonString);
        ImporterMappingEntity importMappingEntity = ImporterMappingEntity.builder()
                .programId(programId)
                .file(jsonb)
                .draft(true)
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();
        importMappingDAO.insert(importMappingEntity);

        ImportMapping importMapping = importMappingDAO.getMapping(importMappingEntity.getId()).get();

        return importMapping;
    }

    private Table parseUploadedFile(CompletedFileUpload file) throws UnsupportedTypeException, HttpStatusException {

        MediaType mediaType;
        try {
            mediaType = mimeTypeParser.getMimeType(file);
        } catch (IOException e){
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Could not determine file type");
        }

        Table df;
        if (mediaType.toString().equals(SupportedMediaType.CSV)) {
            try {
                df = FileUtil.parseTableFromCsv(file.getInputStream());
            } catch (IOException | ParsingException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Error parsing csv: " + e.getMessage());
            }
        } else if (mediaType.toString().equals(SupportedMediaType.XLS) ||
                mediaType.toString().equals(SupportedMediaType.XLSX)) {

            try {
                //TODO: Allow them to pass in header row index in the future
                df = FileUtil.parseTableFromExcel(file.getInputStream(), 0);
            } catch (IOException | ParsingException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Error parsing excel: " + e.getMessage());
            }
        } else {
            throw new UnsupportedTypeException("Unsupported mime type");
        }
        return df;
    }

    public ImportMapping updateMappingFile(UUID programId, UUID mappingId, AuthenticatedUser actingUser, CompletedFileUpload file)
            throws UnsupportedTypeException, HttpStatusException, DoesNotExistException, AuthorizationException {

        Program program = validateRequest(programId, actingUser);

        ImportMapping importMapping;
        Optional<ImportMapping> optionalImportMapping = importMappingDAO.getMapping(mappingId);

        Table df = parseUploadedFile(file);

        //TODO: Validate the new file works with the mapping, if validate is specified

        // Convert the table to json to store
        String jsonString = df.write().toString("json");
        JSONB jsonb = JSONB.valueOf(jsonString);
        ImporterMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setFile(jsonb);
        importMappingDAO.update(importMappingEntity);

        importMapping = importMappingDAO.getMapping(importMappingEntity.getId()).get();

        return importMapping;
    }

    public ImportMapping updateMapping(UUID programId, AuthenticatedUser actingUser, UUID mappingId,
                                       ImportMapping mappingRequest, Boolean validate) throws
            DoesNotExistException, AuthorizationException, HttpStatusException, UnprocessableEntityException, AlreadyExistsException {

        Program program = validateRequest(programId, actingUser);

        Optional<ImportMapping> optionalImportMapping = importMappingDAO.getMapping(mappingId);
        if (optionalImportMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that id does not exist");
        }
        ImportMapping importMapping = optionalImportMapping.get();

        // Check mappings within the given program with the same name
        List<ImportMapping> matchingMappings = importMappingDAO.getMappingsByName(programId, mappingRequest.getName());
        List<ImportMapping> nonSelfMappings = matchingMappings.stream()
                .filter(mapping -> !mapping.getId().equals(mappingId)).collect(Collectors.toList());
        if (nonSelfMappings.size() > 0){
            throw new AlreadyExistsException("A mapping with that name already exists");
        }

        // If validate is true, validate the mapping
        if (validate) {
            mappingManager.map(mappingRequest, importMapping.getFileTable());
        }

        // Save the mapping
        String json = null;
        try {
            json = objectMapper.writeValueAsString(mappingRequest.getMappingConfig());
        } catch(JsonProcessingException e) {
            log.error("Problem converting traits json", e);
            // If we didn't catch this in parsing to the class, this is a server error
            throw new InternalServerException("Problem converting mapping json", e);
        }

        ImporterMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setName(mappingRequest.getName());
        importMappingEntity.setImportTypeId(mappingRequest.getImportTypeId());
        importMappingEntity.setMapping(JSONB.valueOf(json));
        importMappingEntity.setDraft(mappingRequest.getDraft());
        importMappingDAO.update(importMappingEntity);

        Optional<ImportMapping> updatedMapping = importMappingDAO.getMapping(importMappingEntity.getId());

        return updatedMapping.get();
    }

    public Boolean exists(UUID mappingId) {
        return importMappingDAO.existsById(mappingId);
    }

    public ImportResponse uploadData(UUID programId, UUID mappingId, AuthenticatedUser actingUser, CompletedFileUpload file)
            throws DoesNotExistException, AuthorizationException, UnsupportedTypeException, HttpStatusException, UnprocessableEntityException {

        Program program = validateRequest(programId, actingUser);

        // Find the mapping
        Optional<ImportMapping> optionalMapping = importMappingDAO.getMapping(mappingId);
        if (optionalMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that id does not exist");
        }
        ImportMapping importMapping = optionalMapping.get();

        // Read the file
        //TODO: Get better errors on this
        Table data = parseUploadedFile(file);
        String filename = file.getFilename();

        // Map the file to validate it
        List<BrAPIImport> brAPIImportList = mappingManager.map(importMapping, data);

        // Create our import progress object
        ImportUpload upload = dsl.transactionResult(configuration -> {
            ImportUpload newUpload = new ImportUpload();
            newUpload.setProgramId(programId);
            String jsonString = data.write().toString("json");
            JSONB jsonb = JSONB.valueOf(jsonString);
            newUpload.setFileData(jsonb);
            newUpload.setImporterMappingId(mappingId);
            newUpload.setUploadFileName(filename);
            newUpload.setUserId(actingUser.getId());
            newUpload.setCreatedBy(actingUser.getId());
            newUpload.setUpdatedBy(actingUser.getId());

            // Create a progress object
            ImportProgress importProgress = new ImportProgress();
            importProgress.setCreatedBy(actingUser.getId());
            importProgress.setUpdatedBy(actingUser.getId());
            importProgress.setStatuscode((short) HttpStatus.OK.getCode());
            importDAO.createProgress(importProgress);

            newUpload.setImporterProgressId(importProgress.getId());
            importDAO.insert(newUpload);
            return newUpload;
        });

        // Construct our return with our import id
        ImportResponse response = new ImportResponse();
        response.setImportId(upload.getId());
        return response;
    }

    public ImportResponse updateUpload(UUID programId, UUID uploadId, AuthenticatedUser actingUser, Boolean commit) throws
            DoesNotExistException, UnprocessableEntityException, AuthorizationException {

        Program program = validateRequest(programId, actingUser);

        // Find the import
        Optional<ImportUpload> uploadOptional = importDAO.getUploadById(uploadId);
        if (uploadOptional.isEmpty()) throw new DoesNotExistException("Upload with that id does not exist");
        ImportUpload upload = uploadOptional.get();

        if (upload.getProgress() != null && upload.getProgress().getStatuscode().equals((short) HttpStatus.ACCEPTED.getCode())) {
            // Another action is in process for this import, throw an error
            throw new HttpStatusException(HttpStatus.LOCKED, "Another action is currently being performed on this import.");
        }

        // Check that the program that the user created the import for is the one they are updating for
        if (!programId.equals(upload.getProgramId())){
            throw new BadRequestException("Unable to update upload for a different program than the upload was created in.");
        }

        // Get mapping
        Optional<ImportMapping> mappingConfigOptional = importMappingDAO.getMapping(upload.getImporterMappingId());
        if (mappingConfigOptional.isEmpty()) throw new DoesNotExistException("Cannot find mapping config associated with upload.");
        ImportMapping mappingConfig = mappingConfigOptional.get();

        Optional<BrAPIImportService> optionalImportService = configManager.getImportServiceById(mappingConfig.getImportTypeId());
        if (optionalImportService.isEmpty()) throw new DoesNotExistException("Config with that id does not exist");
        BrAPIImportService importService = optionalImportService.get();

        // TODO: maybe return brapiimport from configmanager

        // Get our data
        Table data = upload.getFileDataTable();

        upload.setMappedData(null);
        // Get fresh progress
        ImportProgress importProgress = new ImportProgress();
        importProgress.setId(upload.getProgress().getId());
        upload.setProgress(importProgress);
        upload.getProgress().setStatuscode((short) HttpStatus.ACCEPTED.getCode());
        upload.getProgress().setMessage("Mapping file to import objects");
        importDAO.update(upload);
        // Redo the mapping
        //TODO: Get better errors for these
        List<BrAPIImport> brAPIImportList = mappingManager.map(mappingConfig, data);

        // Spin off new process for processing the file
        CompletableFuture.supplyAsync(() -> {
            try {
                importService.process(brAPIImportList, data, program, upload, commit);
            } catch (UnprocessableEntityException e){
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.UNPROCESSABLE_ENTITY.getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (DoesNotExistException e)  {
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.NOT_FOUND.getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (Exception e) {
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                // TODO: Probably don't want to return this message. But do it for now
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            }
            return null;
        });

        ImportResponse importResponse = new ImportResponse();
        importResponse.setImportId(upload.getId());
        importResponse.setProgress(upload.getProgress());
        return importResponse;
    }

    public Pair<HttpStatus, ImportResponse> getDataUpload(UUID uploadId, Boolean includeMapping) throws DoesNotExistException {

        Optional<ImportUpload> uploadOptional = importDAO.getUploadById(uploadId);
        if (uploadOptional.isEmpty()){
            throw new DoesNotExistException("Upload with that id does not exist");
        }
        ImportUpload upload = uploadOptional.get();

        // Parse our our response
        ImportResponse response = new ImportResponse();
        response.setImportId(uploadId);
        response.setProgress(upload.getProgress());
        if (includeMapping){
            response.setPreview(upload.getMappedData());
        }

        Integer statusCode = (int) upload.getProgress().getStatuscode();
        HttpStatus status = HttpStatus.valueOf(statusCode);

        return new ImmutablePair<>(status, response);
    }

    public List<ImportMapping> getAllMappings(UUID programId, AuthenticatedUser actingUser, Boolean draft)
            throws DoesNotExistException, AuthorizationException {

        Program program = validateRequest(programId, actingUser);
        List<ImportMapping> importMappings = importMappingDAO.getAllMappings(programId, draft);
        return importMappings;
    }

    private Program validateRequest(UUID programId, AuthenticatedUser actingUser) throws DoesNotExistException, AuthorizationException{
        Optional<Program> optionalProgram = programService.getById(programId);
        if (!optionalProgram.isPresent())
        {
            throw new DoesNotExistException("Program id does not exist");
        }
        Program program = optionalProgram.get();

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }
        return program;
    }
}
