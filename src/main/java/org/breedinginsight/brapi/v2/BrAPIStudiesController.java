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
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIStatus;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.response.BrAPIStudySingleResponse;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.model.request.query.StudyQuery;
import org.breedinginsight.brapi.v2.services.BrAPIStudyService;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.services.ExperimentalCollaboratorService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.StudyQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIStudiesController {

    private final String referenceSource;
    private final BrAPIStudyService studyService;
    private final StudyQueryMapper studyQueryMapper;
    private final ProgramService programService;
    private final SecurityService securityService;
    private final ProgramUserService programUserService;
    private final ExperimentalCollaboratorService experimentalCollaboratorService;


    @Inject
    public BrAPIStudiesController(BrAPIStudyService studyService,
                                  StudyQueryMapper studyQueryMapper,
                                  @Property(name = "brapi.server.reference-source") String referenceSource,
                                  ProgramService programService,
                                  SecurityService securityService,
                                  ProgramUserService programUserService,
                                  ExperimentalCollaboratorService experimentalCollaboratorService) {
        this.studyService = studyService;
        this.studyQueryMapper = studyQueryMapper;
        this.referenceSource = referenceSource;
        this.programService = programService;
        this.securityService = securityService;
        this.programUserService = programUserService;
        this.experimentalCollaboratorService = experimentalCollaboratorService;
    }

    @Get("/studies{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<List<BrAPIStudy>>>> getStudies(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = StudyQueryMapper.class) @Valid StudyQuery queryParams) {
        try {
            log.debug("fetching studies for program: " + programId);
            List<BrAPIStudy> studies;

            Optional<ProgramUser> experimentalCollaborator = programUserService.getIfExperimentalCollaborator(programId, securityService.getUser().getId());
            // If the program user is an experimental collaborator, filter results.
            if (experimentalCollaborator.isPresent()) {
                Optional<Program> program = programService.getById(programId);
                if (program.isEmpty()) {
                    return HttpResponse.notFound();
                }

                List<UUID> experimentIds = experimentalCollaboratorService.getAuthorizedExperimentIds(experimentalCollaborator.get().getId());
                studies = studyService.getStudiesByExperimentIds(program.get(), experimentIds)
                        .stream()
                        .peek(this::setDbIds)
                        .collect(Collectors.toList());
            } else {
                studies = studyService.getStudies(programId)
                        .stream()
                        .peek(this::setDbIds)
                        .collect(Collectors.toList());
            }

            queryParams.setSortField(studyQueryMapper.getDefaultSortField());
            queryParams.setSortOrder(studyQueryMapper.getDefaultSortOrder());
            SearchRequest searchRequest = queryParams.constructSearchRequest();
            return ResponseUtils.getBrapiQueryResponse(studies, studyQueryMapper, queryParams, searchRequest);
        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving study");
        } catch (IllegalArgumentException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, "Error parsing requested date format");
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @Post("/studies")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse studiesPost(@PathVariable("programId") UUID programId, @Body List<BrAPIStudy> body) {
        //DO NOT IMPLEMENT - Users are only able to create new studies via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Get("/studies/{studyDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIStudySingleResponse> studiesStudyDbIdGet(@PathVariable("programId") UUID programId, @PathVariable("studyDbId") String environmentId) {
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("program id: " + programId + " not found");
            return HttpResponse.notFound();
        }
        try {
            Optional<BrAPIStudy> study = studyService.getStudyByEnvironmentId(program.get(), UUID.fromString(environmentId));
            if(study.isPresent()) {
                setDbIds(study.get());
                return HttpResponse.ok(new BrAPIStudySingleResponse().result(study.get()));
            } else {
                log.warn("studyDbId: " + environmentId + " not found");
                return HttpResponse.notFound();
            }
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError(new BrAPIStudySingleResponse().metadata(new BrAPIMetadata().addStatusItem(new BrAPIStatus().message("Error fetching study")
                                                                                                                                       .messageType(BrAPIStatus.MessageTypeEnum.ERROR))));
        }
    }

    @Put("/studies/{studyDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse studiesStudyDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("studyDbId") String studyDbId,
                                            @Body BrAPIStudy body) {
        //DO NOT IMPLEMENT - Users are only able to update studies via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    private void setDbIds(BrAPIStudy study) {
        study.studyDbId(Utilities.getExternalReference(study.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES))
                                 .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                                 .getReferenceID());

        study.trialDbId(Utilities.getExternalReference(study.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS))
                                 .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                                 .getReferenceID());

        //TODO update locationDbId
    }
}
