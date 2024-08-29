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

package org.breedinginsight.brapi.v2;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPITrialSingleResponse;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentQuery;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.services.ExperimentalCollaboratorService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.ExperimentQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPITrialsController {

    private final String referenceSource;
    private final BrAPITrialService experimentService;
    private final ExperimentQueryMapper experimentQueryMapper;
    private final SecurityService securityService;
    private final ProgramUserService programUserService;
    private final ExperimentalCollaboratorService experimentalCollaboratorService;
    private final ProgramService programService;

    @Inject
    public BrAPITrialsController(BrAPITrialService experimentService,
                                 ExperimentQueryMapper experimentQueryMapper,
                                 @Property(name = "brapi.server.reference-source") String referenceSource,
                                 SecurityService securityService,
                                 ProgramUserService programUserService,
                                 ExperimentalCollaboratorService experimentalCollaboratorService,
                                 ProgramService programService) {
        this.experimentService = experimentService;
        this.experimentQueryMapper = experimentQueryMapper;
        this.referenceSource = referenceSource;
        this.securityService = securityService;
        this.programUserService = programUserService;
        this.experimentalCollaboratorService = experimentalCollaboratorService;
        this.programService = programService;
    }

    @Get("/trials{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN, ProgramSecuredRole.READ_ONLY, ProgramSecuredRole.PROGRAM_ADMIN
            ,ProgramSecuredRole.EXPERIMENTAL_COLLABORATOR })
    public HttpResponse<Response<DataResponse<List<BrAPITrial>>>> getExperiments(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = ExperimentQueryMapper.class) @Valid ExperimentQuery queryParams) {
        try {
            List<BrAPITrial> experiments = new ArrayList<>();
            log.debug("fetching trials for program: " + programId);

            Optional<ProgramUser> experimentalCollaborator = programUserService.getIfExperimentalCollaborator(programId, securityService.getUser().getId());
            // If the program user is an experimental collaborator, filter results.
            if (experimentalCollaborator.isPresent()) {
                Optional<Program> program = programService.getById(programId);
                if (program.isEmpty()) {
                    return HttpResponse.notFound();
                }
                List<UUID> experimentIds = experimentalCollaboratorService.getAuthorizedExperimentIds(experimentalCollaborator.get().getId());
                experiments = experimentService.getTrialsByExperimentIds(program.get(), experimentIds).stream().peek(this::setDbIds).collect(Collectors.toList());
            } else {
                experiments = experimentService.getExperiments(programId).stream().peek(this::setDbIds).collect(Collectors.toList());
            }

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

    @Get("/trials/{trialId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ExperimentCollaboratorSecured
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPITrialSingleResponse> getExperimentById(
            @PathVariable("programId") UUID programId,
            @PathVariable("trialId") UUID trialId,
            @QueryValue(defaultValue = "false") Boolean stats){
        try {
            String logMsg = "fetching trial id:" +  trialId +" for program: " + programId;
            if(stats){
                logMsg += " with stats";
            }
            log.debug(logMsg);
            BrAPITrial trial = experimentService.getTrialDataByUUID(programId, trialId, stats);
            setDbIds(trial);
            return HttpResponse.ok(new BrAPITrialSingleResponse().result(trial));
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @Post("/trials")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<?> trialsPost(@PathVariable("programId") UUID programId, @Body List<BrAPITrial> body) {
        //DO NOT IMPLEMENT - Users are only able to create new trials via the DeltaBreed UI
        return HttpResponse.notFound();
    }


    @Put("/trials/{trialDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<?> trialsTrialDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("trialDbId") String trialDbId, @Body BrAPITrial body) {
        //DO NOT IMPLEMENT - Users are only able to update trials via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    private void setDbIds(BrAPITrial trial) {
        trial.trialDbId(Utilities.getExternalReference(trial.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS))
                                 .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                                 .getReferenceID());
        trial.programDbId(Utilities.getExternalReference(trial.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS))
                                 .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                                 .getReferenceID());

        //TODO update locationDbId
    }
}
