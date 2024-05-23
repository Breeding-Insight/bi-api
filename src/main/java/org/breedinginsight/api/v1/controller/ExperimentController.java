package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.SubEntityDatasetRequest;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.model.Dataset;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.response.mappers.ExperimentQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.Optional;
import java.util.UUID;

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

    @Get("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/export{?queryParams*}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(value={"text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"})
    public HttpResponse<StreamedFile> datasetExport(
            @PathVariable("programId") UUID programId, @PathVariable("experimentId") UUID experimentId,
            @QueryValue @Valid ExperimentExportQuery queryParams) {
        String downloadErrorMessage = "An error occurred while generating the download file. Contact the development team at bidevteam@cornell.edu.";
        try {
            Optional<Program> program = programService.getById(programId);
            if(program.isEmpty()) {
                return HttpResponse.notFound();
            }

            // if a list of environmentIds are sent, return multiple files (zipped),
            // else if a single environmentId is sent, return single file (CSV/Excel),
            // else (if no environmentIds are sent), return a single file (CSV/Excel) including all Environments.
            DownloadFile downloadFile = experimentService.exportObservations(program.get(), experimentId, queryParams);

            return HttpResponse
                    .ok(downloadFile.getStreamedFile())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + downloadFile.getFileName());
        } catch (Exception e) {
            log.info(downloadErrorMessage, e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, downloadErrorMessage).contentType(MediaType.TEXT_PLAIN).body(downloadErrorMessage);
            return response;
        }
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/dataset/{datasetId}{?stats}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<Dataset>> getDatasetData(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId,
            @PathVariable("datasetId") UUID datasetId,
            @QueryValue(defaultValue = "false") Boolean stats) {
        String downloadErrorMessage = "An error occurred while fetching the dataset. Contact the development team at bidevteam@cornell.edu.";
        try {
            Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program does not exist"));
            Response<Dataset> response = new Response(experimentService.getDatasetData(program, experimentId, datasetId, stats));
            return HttpResponse.ok(response);
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, downloadErrorMessage).contentType(MediaType.TEXT_PLAIN).body(downloadErrorMessage);
            return response;
        }
    }

    @Post("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/dataset")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<Dataset>> getDatasetData(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId,
            @Body @Valid SubEntityDatasetRequest datasetRequest) {
        try {
            Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program does not exist"));
            Response<Dataset> response = new Response(experimentService.createSubEntityDataset(program, experimentId, datasetRequest));
            return HttpResponse.ok(response);
        } catch (Exception e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }
}
