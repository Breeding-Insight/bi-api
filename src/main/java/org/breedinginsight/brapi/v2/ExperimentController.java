package org.breedinginsight.brapi.v2;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentQuery;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.ExperimentQueryMapper;
import javax.inject.Inject;
import javax.validation.Valid;
import java.io.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ExperimentController {
    private final BrAPITrialService experimentService;
    private final ExperimentQueryMapper experimentQueryMapper;
    private final ProgramService programService;

    @Inject
    public ExperimentController(BrAPITrialService experimentService, ExperimentQueryMapper experimentQueryMapper, ProgramService programService) {
        this.experimentService = experimentService;
        this.experimentQueryMapper = experimentQueryMapper;
        this.programService = programService;
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/trials{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<List<BrAPITrial>>>> getExperiments(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = ExperimentQueryMapper.class) @Valid ExperimentQuery queryParams) {
        try {
            log.debug("fetching trials for program: " + programId);

            List<BrAPITrial> experiments = experimentService.getExperiments(programId);
            SearchRequest searchRequest = queryParams.constructSearchRequest();
            return ResponseUtils.getBrapiQueryResponse(experiments, experimentQueryMapper, queryParams, searchRequest);
        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving experiments");
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/trials/{trialId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<BrAPITrial>> getExperimentById(
            @PathVariable("programId") UUID programId,
            @PathVariable("trialId") UUID trialId,
            @QueryValue(defaultValue = "false") Boolean stats){
        try {
            String logMsg = "fetching trial id:" +  trialId +" for program: " + programId;
            if(stats){
                logMsg += " with stats";
            }
            log.debug(logMsg);
            Response<BrAPITrial> response = new Response<>(experimentService.getTrialDataByUUID(programId, trialId, stats));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/export{?queryParams*}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(value={"text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip"})
    public HttpResponse<StreamedFile> datasetExport(
            @PathVariable("programId") UUID programId, @PathVariable("experimentId") UUID experimentId,
            @QueryValue @Valid ExperimentExportQuery queryParams) {
        String downloadErrorMessage = "An error occurred while generating the download file. Contact the development team at bidevteam@cornell.edu.";
        try {
            DownloadFile downloadFile;
            Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program does not exist"));

            // If multiple files are requested, make multiple requests!
            List<DownloadFile> datasetFiles = new LinkedList<>();
            if (queryParams.getEnvironments() != null && queryParams.getEnvironments().split(",").length > 1) {
                String[] environments = queryParams.getEnvironments().split(",");
                for (String env : environments) {
                    // Overwrite queryParams environments to just current env.
                    queryParams.setEnvironments(env);
                    // Get file for env.
                    DownloadFile file = experimentService.exportObservations(program, experimentId, queryParams);
                    // Add to file list.
                    datasetFiles.add(file);
                }
            } else {
                datasetFiles.add(experimentService.exportObservations(program, experimentId, queryParams));
            }

            // TODO: zip if more than 1 file.
            if (datasetFiles.size() > 1)
            {
                // Build zip file name. <exp-title_<export-timestamp>.zip
                BrAPITrial experiment = experimentService.getExperiment(program, experimentId);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
                String timestamp = formatter.format(OffsetDateTime.now());
                String filename = String.format("%s_%s.zip", experiment.getTrialName(), timestamp);

                PipedInputStream in = new PipedInputStream();
                final PipedOutputStream out = new PipedOutputStream(in);
                new Thread(() -> {
                    try {
                        ZipOutputStream zipStream = new ZipOutputStream(out);
                        for (DownloadFile datasetFile : datasetFiles) {

                            ZipEntry entry = new ZipEntry(datasetFile.getFileName());
                            zipStream.putNextEntry(entry);
                            // Write datasetFile to zip.
                            zipStream.write(datasetFile.getStreamedFile().getInputStream().readAllBytes());
                            zipStream.closeEntry();

                        }
                        zipStream.close();
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                StreamedFile sf = new StreamedFile(in, new MediaType(MediaType.APPLICATION_OCTET_STREAM));
                downloadFile = new DownloadFile(filename, sf);
            }
            else {
                // There's only one file, download without zipping.
                downloadFile = datasetFiles.get(0);
            }

            HttpResponse<StreamedFile> response = HttpResponse
                    .ok(downloadFile.getStreamedFile())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + downloadFile.getFileName());
            return response;
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, downloadErrorMessage).contentType(MediaType.TEXT_PLAIN).body(downloadErrorMessage);
            return response;
        }


    }


}
