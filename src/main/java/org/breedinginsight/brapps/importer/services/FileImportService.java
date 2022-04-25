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
import org.brapi.client.v2.JSON;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingProgramDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.config.ImportConfigResponse;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.dao.db.tables.pojos.ImporterMappingEntity;
import org.breedinginsight.dao.db.tables.pojos.ImporterMappingProgramEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.constants.SupportedMediaType;
import org.breedinginsight.services.exceptions.*;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class FileImportService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private UserService userService;
    private MimeTypeParser mimeTypeParser;
    private ImportMappingDAO importMappingDAO;
    private ObjectMapper objectMapper;
    private MappingManager mappingManager;
    private ImportConfigManager configManager;
    private ImportDAO importDAO;
    private DSLContext dsl;
    private ImportMappingProgramDAO importMappingProgramDAO;

    @Inject
    FileImportService(ProgramUserService programUserService, ProgramService programService, MimeTypeParser mimeTypeParser,
                      ImportMappingDAO importMappingDAO, ObjectMapper objectMapper, MappingManager mappingManager,
                      ImportConfigManager configManager, ImportDAO importDAO, DSLContext dsl, ImportMappingProgramDAO importMappingProgramDAO,
                      UserService userService) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.mimeTypeParser = mimeTypeParser;
        this.importMappingDAO = importMappingDAO;
        this.objectMapper = objectMapper;
        this.mappingManager = mappingManager;
        this.configManager = configManager;
        this.importDAO = importDAO;
        this.dsl = dsl;
        this.importMappingProgramDAO = importMappingProgramDAO;
        this.userService = userService;
    }

    public List<ImportConfigResponse> getAllImportTypeConfigs() {
        return configManager.getAllImportTypeConfigs();
    }

    /**
     * Saves an import template mapping. Matches to the template fields is not enforced.
     * Intended to be called from controller.
     * @param programId
     * @param actingUser
     * @param mappingRequest
     * @return
     * @throws DoesNotExistException
     * @throws AuthorizationException
     * @throws UnsupportedTypeException
     */
    public ImportMapping createMapping(UUID programId, AuthenticatedUser actingUser, ImportMapping mappingRequest) throws AlreadyExistsException, UnprocessableEntityException {

        // TODO: Check import type exists

        // Check a name was passed if saving mapping
        if (mappingRequest.getSaved() && mappingRequest.getName() == null) {
            throw new UnprocessableEntityException("Mapping name required when saving a mapping");
        }

        // Check mapping name not already in use
        List<ImportMapping> matchingMappings = importMappingDAO.getProgramMappingsByName(programId, mappingRequest.getName());
        if (matchingMappings.size() > 0){
            throw new AlreadyExistsException("A mapping with that name already exists");
        }

        // Convert mapping to json for storage
        String json = convertMappingToJson(mappingRequest);

        // Create mapping
        ImporterMappingEntity importMappingEntity = ImporterMappingEntity.builder()
                .name(mappingRequest.getSaved() ? mappingRequest.getName() : null)
                .importTemplateId(mappingRequest.getImportTemplateId())
                .mapping(JSONB.valueOf(json))
                .saved(mappingRequest.getSaved())
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();
        importMappingDAO.insert(importMappingEntity);

        // Create program association
        ImporterMappingProgramEntity importerMappingProgramEntity = ImporterMappingProgramEntity.builder()
                .programId(programId)
                .importerMappingId(importMappingEntity.getId())
                .build();
        importMappingProgramDAO.insert(importerMappingProgramEntity);

        return importMappingDAO.getMapping(importMappingEntity.getId()).get();
    }

    /**
     * Updates an import template mapping. Matches to the template fields is not enforced.
     * Intended to be called from controller.
     * @param programId
     * @param actingUser
     * @param mappingId
     * @param mappingRequest
     * @return
     * @throws DoesNotExistException
     * @throws AuthorizationException
     * @throws AlreadyExistsException
     */
    public ImportMapping updateMapping(UUID programId, AuthenticatedUser actingUser, UUID mappingId,
                                       ImportMapping mappingRequest) throws
            DoesNotExistException, AuthorizationException, AlreadyExistsException {

        Program program = validateRequest(programId, actingUser);

        // Get existing mapping
        Optional<ImportMapping> optionalImportMapping = importMappingDAO.getMapping(mappingId);
        if (optionalImportMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that id does not exist");
        }

        // Check it doesn't use a reserved name
        // TODO: Remove when default imports go right to template instead of needing mapping
        List<ImportMapping> importMappings = importMappingDAO.getAllSystemMappings();
        for(ImportMapping systemMapping: importMappings) {
            if (systemMapping.getName().equalsIgnoreCase(mappingRequest.getName())) {
                throw new AlreadyExistsException(String.format("Import name '%s' is a reserved name and cannot be used", mappingRequest.getName()));
            }
        }

        // Check mappings within the given program with the same name
        List<ImportMapping> matchingMappings = importMappingDAO.getProgramMappingsByName(programId, mappingRequest.getName());
        List<ImportMapping> nonSelfMappings = matchingMappings.stream()
                .filter(mapping -> !mapping.getId().equals(mappingId)).collect(Collectors.toList());
        if (nonSelfMappings.size() > 0){
            throw new AlreadyExistsException("A mapping with that name already exists");
        }

        // Save the mapping
        String json = convertMappingToJson(mappingRequest);

        ImporterMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setName(mappingRequest.getName());
        importMappingEntity.setImportTemplateId(mappingRequest.getImportTemplateId());
        importMappingEntity.setMapping(JSONB.valueOf(json));
        importMappingDAO.update(importMappingEntity);

        Optional<ImportMapping> updatedMapping = importMappingDAO.getMapping(importMappingEntity.getId());

        return updatedMapping.get();
    }

    /**
     * Converts a mapping object into json for storage in the db.
     * @param mappingRequest
     * @return
     */
    private String convertMappingToJson(ImportMapping mappingRequest) {

        // Save the mapping
        String json = null;
        try {
            json = objectMapper.writeValueAsString(mappingRequest.getMappingConfig());
        } catch(JsonProcessingException e) {
            log.error("Problem converting traits json", e);
            // If we didn't catch this in parsing to the class, this is a server error
            throw new InternalServerException("Problem converting mapping json", e);
        }
        return json;
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

        // replace "." with "" in column names to deal with json flattening issue in tablesaw
        List<String> columnNames = df.columnNames();
        List<String> namesToReplace = new ArrayList<>();
        for (String name : columnNames) {
            if (name.contains(".")) {
                namesToReplace.add(name);
            }
        }

        List<Column<?>> columns = df.columns(namesToReplace.stream().toArray(String[]::new));
        for (int i=0; i<columns.size(); i++) {
            Column<?> column = columns.get(i);
            column.setName(namesToReplace.get(i).replace(".",""));
        }

        return df;
    }

    public Boolean exists(UUID mappingId) {
        return importMappingDAO.existsById(mappingId);
    }

    public ImportResponse uploadData(UUID programId, UUID mappingId, AuthenticatedUser actingUser, CompletedFileUpload file)
            throws DoesNotExistException, AuthorizationException, UnsupportedTypeException, HttpStatusException, UnprocessableEntityException, ValidatorException {

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

    public ImportResponse updateUpload(UUID programId, UUID uploadId, AuthenticatedUser actingUser, Map<String, Object> userInput, Boolean commit) throws
            DoesNotExistException, UnprocessableEntityException, AuthorizationException {

        Program program = validateRequest(programId, actingUser);

        // Get user
        User user = userService.getById(actingUser.getId()).get();

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
            throw new UnprocessableEntityException("Unable to update upload for a different program than the upload was created in.");
        }

        // Get mapping
        Optional<ImportMapping> mappingConfigOptional = importMappingDAO.getMapping(upload.getImporterMappingId());
        if (mappingConfigOptional.isEmpty()) throw new DoesNotExistException("Cannot find mapping config associated with upload.");
        ImportMapping mappingConfig = mappingConfigOptional.get();

        Optional<BrAPIImportService> optionalImportService = configManager.getImportServiceById(mappingConfig.getImportTemplateId());
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
        List<BrAPIImport> brAPIImportList;
        try {
            if (commit) {
                brAPIImportList = mappingManager.map(mappingConfig, data, userInput);
            } else {
                brAPIImportList = mappingManager.map(mappingConfig, data);
            }
            processFile(brAPIImportList, data, program, upload, user, commit, importService, actingUser);
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            ImportProgress progress = upload.getProgress();
            progress.setStatuscode((short) HttpStatus.UNPROCESSABLE_ENTITY.getCode());
            progress.setMessage(e.getMessage());
            progress.setUpdatedBy(actingUser.getId());
            importDAO.update(upload);
            throw e;
        } catch (ValidatorException e) {
            log.error("Validation errors", e);
            ImportProgress progress = upload.getProgress();
            progress.setStatuscode((short) HttpStatus.UNPROCESSABLE_ENTITY.getCode());
            progress.setMessage("Multiple Errors");
            String json = (new JSON()).getGson().toJson(e.getErrors());
            progress.setBody(JSONB.valueOf(json));
            progress.setUpdatedBy(actingUser.getId());
            importDAO.update(upload);
        }

        ImportResponse importResponse = new ImportResponse();
        importResponse.setImportId(upload.getId());
        importResponse.setProgress(upload.getProgress());
        return importResponse;
    }

    private void processFile(List<BrAPIImport> finalBrAPIImportList, Table data, Program program,
                                   ImportUpload upload, User user, Boolean commit, BrAPIImportService importService,
                                   AuthenticatedUser actingUser) {
        // Spin off new process for processing the file
        CompletableFuture.supplyAsync(() -> {
            try {
                importService.process(finalBrAPIImportList, data, program, upload, user, commit);
            } catch (UnprocessableEntityException e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.UNPROCESSABLE_ENTITY.getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (DoesNotExistException e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.NOT_FOUND.getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (HttpStatusException e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) e.getStatus().getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (ValidatorException e) {
                log.error("Validation errors", e);
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.UNPROCESSABLE_ENTITY.getCode());
                progress.setMessage("Multiple Errors");
                String json = (new JSON()).getGson().toJson(e.getErrors());
                progress.setBody(JSONB.valueOf(json));
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                // TODO: Probably don't want to return this message. But do it for now
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            }
            return null;
        });
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

    public List<ImportMapping> getAllMappings(UUID programId, AuthenticatedUser actingUser)
            throws DoesNotExistException {

        return importMappingDAO.getAllProgramMappings(programId);
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

    public List<ImportMapping> getAllSystemMappings(AuthenticatedUser actingUser) {
        List<ImportMapping> importMappings = importMappingDAO.getAllSystemMappings();
        return importMappings;
    }

    public List<ImportMapping> getSystemMappingByName(AuthenticatedUser actingUser, String name) {
        List<ImportMapping> importMappings = importMappingDAO.getSystemMappingByName(name);
        return importMappings;
    }

    public ImportMapping getMappingDetails(UUID mappingId) throws DoesNotExistException {
        Optional<ImportMapping> optionalMapping = importMappingDAO.getMapping(mappingId);
        if (optionalMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that ID does not exist");
        }
        return optionalMapping.get();
    }
}
