package org.breedinginsight.brapps.importer.services;

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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingProgramDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.processors.Processor;
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
import java.util.concurrent.*;

@Slf4j
@Singleton
public class UploadService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private UserService userService;
    private MimeTypeParser mimeTypeParser;
    private ImportMappingDAO importMappingDAO;
    private ObjectMapper objectMapper;
    private Mapper mapper;
    private TemplateManager configManager;
    private ImportDAO importDAO;
    private DSLContext dsl;
    private ImportMappingProgramDAO importMappingProgramDAO;
    private ImportStatusService statusService;
    private FileImportService fileImportService;

    // TODO: Add configurations for this
    // An executor that go up to 4 threads and only holds onto them for 60 seconds of idle time
    private final Executor executor = new ThreadPoolExecutor(0, // core size
            4, // max size
            60, // idle timeout
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(100));

    @Inject
    UploadService(ProgramUserService programUserService, ProgramService programService, MimeTypeParser mimeTypeParser,
                  ImportMappingDAO importMappingDAO, ObjectMapper objectMapper, Mapper mapper,
                  TemplateManager configManager, ImportDAO importDAO, DSLContext dsl, ImportMappingProgramDAO importMappingProgramDAO,
                  UserService userService, ImportStatusService importStatusService, FileImportService fileImportService) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.mimeTypeParser = mimeTypeParser;
        this.importMappingDAO = importMappingDAO;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.configManager = configManager;
        this.importDAO = importDAO;
        this.dsl = dsl;
        this.importMappingProgramDAO = importMappingProgramDAO;
        this.userService = userService;
        this.statusService = importStatusService;
        this.fileImportService = fileImportService;
    }

    public ImportResponse uploadData(UUID programId, Integer templateId, UUID mappingId, Map<String, Object> userInput,
                                     AuthenticatedUser actingUser, CompletedFileUpload file, Boolean commit)
            throws DoesNotExistException, UnsupportedTypeException, HttpStatusException, UnprocessableEntityException, ValidatorException {

        // Get our program and user
        Program program = programService.getById(programId).get();
        User user = userService.getById(actingUser.getId()).get();

        // TODO: Check that the program that the user created the import for is the one they are updating for

        // Get our processor
        Optional<BrAPIImport> optionalTemplate = configManager.getTemplate(templateId);
        if (optionalTemplate.isEmpty()) throw new DoesNotExistException("Template with that id does not exist");
        BrAPIImport template = optionalTemplate.get();
        Processor processor = configManager.getProcessor(template.getClass()).get();

        // If mapping specified, find that mapping. Otherwise generate mapping from template.
        ImportMapping importMapping;
        if (mappingId != null) {
            Optional<ImportMapping> optionalMapping = importMappingDAO.getMapping(mappingId);
            if (optionalMapping.isEmpty()) {
                throw new DoesNotExistException("Mapping with that id does not exist");
            }
            importMapping = optionalMapping.get();
        } else {
            // Generate mapping from template
            // TODO: Add this option to mapping when map isn't passed instead of generated a map
            importMapping = configManager.generateMappingForTemplate(template.getClass());
        }
        ImportMapping finalImportMapping = importMapping;

        // Create our import progress object and insert
        // Create our import progress object
        ImportUpload upload = dsl.transactionResult(configuration -> {
            ImportUpload newUpload = new ImportUpload();
            newUpload.setProgramId(programId);
            newUpload.setImporterMappingId(finalImportMapping.getId());
            newUpload.setUploadFileName(file.getFilename());
            newUpload.setCreatedBy(actingUser.getId());
            newUpload.setUpdatedBy(actingUser.getId());

            // Create a progress object
            // TODO: Use convenience methods
            ImportProgress importProgress = new ImportProgress();
            importProgress.setCreatedBy(actingUser.getId());
            importProgress.setUpdatedBy(actingUser.getId());
            importProgress.setStatusCode(HttpStatus.ACCEPTED.getCode());
            importProgress.setMessage("Request received, waiting for available job");
            importDAO.createProgress(importProgress);

            newUpload.setImporterProgressId(importProgress.getId());
            newUpload.setProgress(importProgress);
            importDAO.insert(newUpload);
            return newUpload;
        });

        // Do our work in separate thread
        executor.execute(() -> {

            try {
                statusService.updateMessage(upload, "Upload started");
                Table data = parseUploadedFile(file);

                statusService.updateMessage(upload, "Mapping file to import objects");
                List<BrAPIImport> brAPIImportList = new ArrayList<>();
                // TODO: Move null and data type validations out from mapper
                if (commit) {
                    brAPIImportList = mapper.map(finalImportMapping, data, userInput);
                } else {
                    brAPIImportList = mapper.map(finalImportMapping, data);
                }

                Map<Integer, PendingImport> mappedImport = new HashMap<>();
                Map<String, ImportPreviewStatistics> importStatistics = new HashMap<>();
                // Processor getExisting
                statusService.updateMessage(upload, "Checking existing objects in brapi service and mapping data.");
                processor.getExistingBrapiData(brAPIImportList, program);
                // Processor validate
                statusService.updateMessage(upload, "Checking data validations.");
                processor.validate(brAPIImportList, program);
                // Processor process
                statusService.updateMessage(upload, "Constructing BrAPI objects from input.");
                processor.process(brAPIImportList, program, user, commit, mappedImport, importStatistics);
                // Processor post
                if (commit) {
                    statusService.updateMessage(upload, "Committing data to BrAPI Server");
                    processor.postBrapiData(mappedImport, program, upload);
                }

                ImportPreviewResponse response = new ImportPreviewResponse();
                response.setStatistics(importStatistics);
                List<PendingImport> mappedBrAPIImportList = new ArrayList<>(mappedImport.values());
                response.setRows(mappedBrAPIImportList);
                statusService.finishUpload(upload, response, String.format("Finished ", commit ? "upload": "preview"));

            } catch (UnsupportedTypeException e) {
                statusService.updateStatus(upload, HttpStatus.BAD_REQUEST, "Unable to process file.");
                // TODO: Throw a custom importer error
                throw new InternalServerException("Unable to process");
            } catch (UnprocessableEntityException e) {
                e.printStackTrace();
            } catch (ValidatorException e) {
                // TODO: The toString method on this won't work, error could also be better.
                statusService.updateStatus(upload, HttpStatus.UNPROCESSABLE_ENTITY, e.getErrors().toString());
                throw new InternalServerException(e.getMessage(), e);
            } catch (ApiException e) {
                // TODO: Error could be better
                statusService.updateStatus(upload, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                throw new InternalServerException(e.getMessage(), e);
            } catch (Exception e) {
                statusService.updateStatus(upload, HttpStatus.INTERNAL_SERVER_ERROR, "Uknown error has occurred");
                throw new InternalServerException(e.getMessage(), e);
            }
        });

        // Construct our return with our import id
        // TODO: Should we be returning the progress?
        ImportResponse response = new ImportResponse();
        response.setImportId(upload.getId());
        return response;
    }

    public Pair<HttpStatus, ImportResponse> getDataUpload(UUID uploadId) throws DoesNotExistException {

        Optional<ImportUpload> uploadOptional = importDAO.getUploadById(uploadId);
        if (uploadOptional.isEmpty()){
            throw new DoesNotExistException("Upload with that id does not exist");
        }
        ImportUpload upload = uploadOptional.get();

        // Parse our our response
        ImportResponse response = new ImportResponse();
        response.setImportId(uploadId);
        response.setProgress(upload.getProgress());
        response.setPreview(upload.getMappedData());

        Integer statusCode = upload.getProgress().getStatusCode();
        HttpStatus status = HttpStatus.valueOf(statusCode);

        return new ImmutablePair<>(status, response);
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
                progress.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (DoesNotExistException e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatusCode(HttpStatus.NOT_FOUND.getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (HttpStatusException e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatusCode(e.getStatus().getCode());
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (ValidatorException e) {
                log.error("Validation errors", e);
                ImportProgress progress = upload.getProgress();
                progress.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
                progress.setMessage("Multiple Errors");
                String json = (new JSON()).getGson().toJson(e.getErrors());
                progress.setBody(JSONB.valueOf(json));
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                ImportProgress progress = upload.getProgress();
                progress.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                // TODO: Probably don't want to return this message. But do it for now
                progress.setMessage(e.getMessage());
                progress.setUpdatedBy(actingUser.getId());
                importDAO.update(upload);
            }
            return null;
        });
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
}
