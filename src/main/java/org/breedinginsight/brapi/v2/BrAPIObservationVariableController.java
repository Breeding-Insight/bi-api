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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponseResult;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableSingleResponse;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.services.BrAPIObservationVariableService;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationVariableController {

    private final OntologyService ontologyService;
    private final BrAPIObservationVariableService observationVariableService;
    private final TraitService traitService;
    private final BrAPITrialService trialService;
    private final ProgramService programService;

    @Inject
    public BrAPIObservationVariableController(OntologyService ontologyService,
                                              BrAPIObservationVariableService observationVariableService,
                                              TraitService traitService,
                                              BrAPITrialService trialService,
                                              ProgramService programService) {
        this.ontologyService = ontologyService;
        this.observationVariableService = observationVariableService;
        this.traitService = traitService;
        this.trialService = trialService;
        this.programService = programService;
    }

    @Get("/variables")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIObservationVariableListResponse> variablesGet(@PathVariable("programId") UUID programId,
                                                                           @Nullable  @QueryValue("observationVariableDbId") String observationVariableDbId,
                                                                           @Nullable @QueryValue("observationVariableName") String observationVariableName,
                                                                           @Nullable @QueryValue("observationVariablePUI") String observationVariablePUI,
                                                                           @Nullable @QueryValue("traitClass") String traitClass,
                                                                           @Nullable @QueryValue("methodDbId") String methodDbId,
                                                                           @Nullable @QueryValue("methodName") String methodName,
                                                                           @Nullable @QueryValue("methodPUI") String methodPUI,
                                                                           @Nullable @QueryValue("scaleDbId") String scaleDbId,
                                                                           @Nullable @QueryValue("scaleName") String scaleName,
                                                                           @Nullable @QueryValue("scalePUI") String scalePUI,
                                                                           @Nullable @QueryValue("traitDbId") String traitDbId,
                                                                           @Nullable @QueryValue("traitName") String traitName,
                                                                           @Nullable @QueryValue("traitPUI") String traitPUI,
                                                                           @Nullable @QueryValue("ontologyDbId") String ontologyDbId,
                                                                           @Nullable @QueryValue("commonCropName") String commonCropName,
                                                                           @Nullable @QueryValue("trialDbId") String experimentId,
                                                                           @Nullable @QueryValue("studyDbId") String environmentId,
                                                                           @Nullable @QueryValue("externalReferenceID") String externalReferenceID,
                                                                           @Nullable @QueryValue("externalReferenceId") String externalReferenceId,
                                                                           @Nullable @QueryValue("externalReferenceSource") String externalReferenceSource,
                                                                           @Nullable @QueryValue("page") Integer page,
                                                                           @Nullable @QueryValue("pageSize") Integer pageSize, HttpRequest request) {

        try {
            List<Trait> programTraits;
            if (observationVariablePUI != null || methodPUI != null || scalePUI != null || traitPUI != null || commonCropName != null || externalReferenceID != null || externalReferenceId != null || externalReferenceSource != null) {
                log.debug("unsupported variable filters, returning");
                programTraits = new ArrayList<>();
            } else if(environmentId != null || experimentId != null) {
                programTraits = observationVariableService.getBrAPIObservationVariablesForExperiment(
                        programId, Optional.ofNullable(experimentId), Optional.ofNullable(environmentId));
            } else {
                log.debug("fetching variables for the program: " + programId);
                programTraits = ontologyService.getTraitsByProgramId(programId, true);

            }

            List<BrAPIObservationVariable> filteredObsVars = observationVariableService.filterVariables(programTraits,
                                                                                       Optional.ofNullable(observationVariableDbId),
                                                                                       Optional.ofNullable(observationVariableName),
                                                                                       Optional.ofNullable(traitClass),
                                                                                       Optional.ofNullable(methodDbId),
                                                                                       Optional.ofNullable(methodName),
                                                                                       Optional.ofNullable(scaleDbId),
                                                                                       Optional.ofNullable(scaleName),
                                                                                       Optional.ofNullable(traitDbId),
                                                                                       Optional.ofNullable(traitName),
                                                                                       Optional.ofNullable(ontologyDbId));

            BrAPIObservationVariableListResponse response = new BrAPIObservationVariableListResponse()
                    .metadata(new BrAPIMetadata()
                                      .pagination(new BrAPIIndexPagination()
                                                          .currentPage(0)
                                                          .totalPages(1)
                                                          .pageSize(filteredObsVars.size())
                                                          .totalCount(filteredObsVars.size())))
                    .result(new BrAPIObservationVariableListResponseResult().data(filteredObsVars));

            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.warn("Couldn't find object", e);
            return HttpResponse.notFound();
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "error fetching variables");
        }
    }

    @Get("/variables/{observationVariableDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIObservationVariableSingleResponse> variablesObservationVariableDbIdGet(@PathVariable("programId") UUID programId,
                                                            @PathVariable("observationVariableDbId") String observationVariableDbId) {
        log.debug("fetching variable: " + observationVariableDbId);
        UUID traitId;
        try {
            traitId = UUID.fromString(observationVariableDbId);
        } catch (IllegalArgumentException e) {
            return HttpResponse.notFound();
        }

        try {
            Optional<Trait> trait = traitService.getById(programId, traitId);

            if(trait.isEmpty()) {
                return HttpResponse.notFound();
            }

            BrAPIObservationVariableSingleResponse response = new BrAPIObservationVariableSingleResponse()
                    .metadata(new BrAPIMetadata())
                    .result(observationVariableService.convertToBrAPI(trait.get()));

            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            return HttpResponse.notFound();
        }
    }

    @Put("/variables/{observationVariableDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<?> variablesObservationVariableDbIdPut(@PathVariable("programId") UUID programId,
                                                            @PathVariable("observationVariableDbId") String observationVariableDbId,
                                                               @Body BrAPIObservationVariable body) {
        //DO NOT IMPLEMENT - Users are only able to update traits via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Post("/variables")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<?> variablesPost(@PathVariable("programId") UUID programId, @Body List<BrAPIObservationVariable> body) {
        //DO NOT IMPLEMENT - Users are only able to create new traits via the DeltaBreed UI
        return HttpResponse.notFound();
    }

}
