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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponseResult;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.Utilities;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationLevelsController {

    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramDAO programDAO;
    private final ProgramService programService;
    private final BrAPITrialDAO trialDAO;
    private final BrAPIStudyDAO studyDAO;

    @Inject
    public BrAPIObservationLevelsController(BrAPIEndpointProvider brAPIEndpointProvider, ProgramDAO programDAO, ProgramService programService, BrAPITrialDAO trialDAO, BrAPIStudyDAO studyDAO) {
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.programDAO = programDAO;
        this.programService = programService;
        this.trialDAO = trialDAO;
        this.studyDAO = studyDAO;
    }

    @Get("/observationlevels")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIObservationLevelListResponse> observationlevelsGet(@PathVariable("programId") UUID programId,
                                                                                @Nullable @QueryValue("trialDbId") String experimentId,
                                                                                @Nullable @QueryValue("studyDbId") String environmentId,
                                                                                @Nullable @QueryValue("page") Integer page,
                                                                                @Nullable @QueryValue("pageSize") Integer pageSize) {

        log.debug("fetching observation levels for programId: " + programId);

        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("Program id: " + programId + " not found");
            return HttpResponse.notFound();
        }

        String programDbId = program.get().getBrapiProgram().getProgramDbId();
        String studyDbId = null;
        String trialDbId = null;

        if(environmentId != null) {
            try {
                Optional<BrAPIStudy> study = studyDAO.getStudyByEnvironmentId(UUID.fromString(environmentId), program.get());
                if(study.isPresent()) {
                    studyDbId = study.get().getStudyDbId();
                } else {
                    studyDbId = environmentId;
                }
            } catch (ApiException e) {
                log.error(Utilities.generateApiExceptionLogMessage(e), "Error fetching environment");
                return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error finding observation levels");
            }
        } else if(experimentId != null) {
            try {
                List<BrAPITrial> trial = trialDAO.getTrialsByExperimentIds(List.of(UUID.fromString(experimentId)), program.get());
                if(trial.size() == 1) {
                    trialDbId = trial.get(0).getTrialDbId();
                } else {
                    trialDbId = experimentId;
                }
            } catch (ApiException e) {
                log.error(Utilities.generateApiExceptionLogMessage(e), "Error fetching experiments");
                return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error finding observation levels");
            }
        }

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        try {
            ApiResponse<BrAPIObservationLevelListResponse> response = api.observationlevelsGet(studyDbId, trialDbId, programDbId, page == null ? 0 : page, pageSize == null ? 1000 : pageSize);
            BrAPIIndexPagination responsePagination = (BrAPIIndexPagination) response.getBody().getMetadata().getPagination();
            List<BrAPIObservationUnitHierarchyLevel> levels = new ArrayList<>();
            if(response.getBody() != null) {
                levels = response.getBody().getResult().getData().stream().filter(level -> !level.getLevelName().equals(BrAPIConstants.REPLICATE.getValue()) && !level.getLevelName().equals(BrAPIConstants.BLOCK.getValue())).collect(
                        Collectors.toList());
            }
            log.debug(String.format("found %d observation levels", levels.size()));
            return HttpResponse.ok(new BrAPIObservationLevelListResponse().metadata(new BrAPIMetadata().pagination(responsePagination))
                                                                          .result(new BrAPIObservationLevelListResponseResult().data(levels)));
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), "Error fetching observation levels");
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error finding observation levels");
        }
    }
}
