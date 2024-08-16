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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.SubEntityDatasetRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.dao.db.tables.pojos.ExperimentProgramUserRoleEntity;
import org.breedinginsight.model.*;
import org.breedinginsight.services.ExperimentalCollaboratorService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.RoleService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.response.mappers.ExperimentQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ExperimentController {
    private final BrAPITrialService experimentService;
    private final ExperimentQueryMapper experimentQueryMapper;
    private final ProgramService programService;
    private final ExperimentalCollaboratorService experimentalCollaboratorService;
    private final SecurityService securityService;
    private final ProgramUserService programUserService;
    private final RoleService roleService;

    @Inject
    public ExperimentController(BrAPITrialService experimentService, ExperimentQueryMapper experimentQueryMapper, ProgramService programService, ExperimentalCollaboratorService experimentalCollaboratorService, SecurityService securityService, ProgramUserService programUserService, RoleService roleService) {
        this.experimentService = experimentService;
        this.experimentQueryMapper = experimentQueryMapper;
        this.programService = programService;
        this.experimentalCollaboratorService = experimentalCollaboratorService;
        this.securityService = securityService;
        this.programUserService = programUserService;
        this.roleService = roleService;
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

    /**
     * Creates a sub-entity dataset for a given program and experiment with the specified name and number of repeated measures.
     * @param programId The UUID of the program.
     * @param experimentId The UUID of the experiment.
     * @param datasetRequest The POST body, contains the dataset name and number of repeated measures to create.
     * @return An HttpResponse with a Response object containing the newly created Dataset.
     */
    @Post("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/dataset")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<Dataset>> createSubEntityDataset(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId,
            @Body @Valid SubEntityDatasetRequest datasetRequest) {
        try {
            Optional<Program> programOptional = programService.getById(programId);
            if (programOptional.isEmpty()) {
                return HttpResponse.status(HttpStatus.NOT_FOUND, "Program does not exist");
            }

            Response<Dataset> response = new Response(experimentService.createSubEntityDataset(programOptional.get(), experimentId, datasetRequest));
            return HttpResponse.ok(response);
        } catch (Exception e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /**
     * Retrieves the datasets for a given program and experiment.
     *
     * @param programId The UUID of the program.
     * @param experimentId The UUID of the experiment.
     * @return An HttpResponse with a Response object containing a list of DatasetMetadata.
     * @throws DoesNotExistException if the program does not exist.
     * @throws ApiException if an error occurs while retrieving the datasets.UserId
     */
    @Get("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/datasets")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<List<DatasetMetadata>>> getDatasets(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId) throws DoesNotExistException, ApiException {

        Optional<Program> programOptional = programService.getById(programId);
        if (programOptional.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND, "Program does not exist");
        }

        Response<List<DatasetMetadata>> response = new Response(experimentService.getDatasetsMetadata(programOptional.get(), experimentId));
        return HttpResponse.ok(response);

    }

    /**
     * Adds a record to the experiment_program_user_role table
     * @param programId The UUID of the program
     * @param experimentId The UUID of the experiment
     * @param programUserRoleId The UUID of the program user
     * @return HttpResponse containing the newly created ExperimentProgramUserRoleEntity
     */
    @Post("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/collaborators")
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.SYSTEM_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<ExperimentProgramUserRoleEntity>> createExperimentalCollaborator(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId,
            @Body @Valid UUID programUserRoleId
    ) {
        try {
            Optional<Program> programOptional = programService.getById(programId);
            if (programOptional.isEmpty()) {
                return HttpResponse.status(HttpStatus.NOT_FOUND, "Program does not exist");
            }

            //get active user creating the collaborator
            AuthenticatedUser createdByUser = securityService.getUser();
            UUID createdByUserId = createdByUser.getId();

            Response<ExperimentProgramUserRoleEntity> response = new Response(experimentalCollaboratorService.createExperimentalCollaborator(programUserRoleId,experimentId,createdByUserId));
            return HttpResponse.ok(response);
        } catch (Exception e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

    }

    /**
     * Returns an array of collaborators for given experiment filterable using the active query parameter
     * @param programId The UUID of the program
     * @param experimentId The UUID of the experiment
     * @param active true if querying for collaborators added as a collaborator to the experiment, false if querying for collaborators not added
     * @return
     *  Response includes name and email as a convenience to the front end to avoid making another api call
     */
    @Get("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/collaborators{?active}")
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.SYSTEM_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<DataResponse<ExperimentProgramUserRoleEntity>>> getExperimentalCollaborators(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId,
            @QueryValue(defaultValue = "true") Boolean active
    ) {
    //todo confirm what default of active should be/if there should be a default

        try {
            //Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program does not exist"));

            //Get experimental collaborators associated with the experiment
            List<ExperimentProgramUserRoleEntity> collaborators = experimentalCollaboratorService.getExperimentalCollaborators(experimentId);

            //Get roleId for experimental collaborator role
            Role experimentalCollabRole = roleService.getRoleByDomain(ProgramSecuredRole.EXPERIMENTAL_COLLABORATOR.toString()).get();
            UUID roleId = experimentalCollabRole.getId();

            //Get all program users with experimental collaborator role
            List<ProgramUser> collabRoleUsers = programUserService.getProgramUsersByRole(programId, roleId);

            //let's start with case of finding users not assoc with experiment
            //need new fancy model maybe

            Boolean isACollaborator = false;
            for (ProgramUser collabRoleUser : collabRoleUsers) {
                //if collabRoleUser.getUser().getId();
                //should be something more efficient, right?
                if (active){
                    //return users with experimental collaborator role associated with this experiment
                } else {
                    //return users with experimental collaborator role NOT associated with this experiment
                }
            }

            //wait what *is* response type
            //model ExperimentalCollaboratorResponse


            //biuser has name and email
            //program_user_role has id, user_id

            //experimentProgramUserRoleEntity with some stuff appended on

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: paging
            Pagination pagination = new Pagination(collaborators.size(), collaborators.size(), 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<ExperimentProgramUserRoleEntity>> response = new Response(metadata, new DataResponse<>(collaborators));
            return HttpResponse.ok(response);
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "todo").contentType(MediaType.TEXT_PLAIN).body("todo");
            return response;
        }
         /*

Response:
[
    {
        "id": UUID, collaboratorId
        "active": Boolean,
        "userId": UUID,programUserId
        "name": String,
        "email": String
    },
    {
        "id": UUID,
        "active": Boolean,
        "userId": UUID,
        "name": String,
        "email": String
    }
]
     */

    }

    /**
     * Removes record from experiment_program_user_role table
     * @param programId The UUID of the program
     * @param experimentId The UUID of the experiment
     * @param collaboratorId The UUID of the collaborator, referring to a unique experiment-program user role combo in the experiment_program_user_role table
     * @return A Http Response
     */
    @Delete("/${micronaut.bi.api.version}/programs/{programId}/experiments/{experimentId}/collaborators/{collaboratorId}")
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.SYSTEM_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse deleteExperimentalCollaborator(
            @PathVariable("programId") UUID programId,
            @PathVariable("experimentId") UUID experimentId,
            @PathVariable("collaboratorId") UUID collaboratorId
    ) {
        try {
            //todo double check this endpoint would be getting the collaboratorId and not the programUserRoleId
            experimentalCollaboratorService.deleteExperimentalCollaborator(collaboratorId);
            return HttpResponse.ok();
        } catch (Exception e) {
            log.error("Error deleting experimental collaborator.\n\tprogramId: " + programId +  "\n\texperimentId: " + experimentId + "\n\tcollaboratorId: " + collaboratorId);
            throw e;
        }

    }
}
