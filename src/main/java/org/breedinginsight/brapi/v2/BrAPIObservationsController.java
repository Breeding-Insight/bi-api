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
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIStatus;
import org.brapi.v2.model.BrAPIWSMIMEDataTypes;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.response.*;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapi.v2.model.request.query.ObservationQuery;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.Utilities;


import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationsController {

    private final ProgramService programService;
    private final ProgramDAO programDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final BrAPIObservationDAO observationDAO;

    @Inject
    public BrAPIObservationsController(ProgramService programService,
                                       ProgramDAO programDAO1,
                                       BrAPIStudyDAO brAPIStudyDAO,
                                       BrAPIEndpointProvider brAPIEndpointProvider,
                                       BrAPIObservationDAO brAPIObservationDAO) {
        this.programService = programService;
        this.programDAO = programDAO1;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.observationDAO = brAPIObservationDAO;
    }

    @Get("/observations")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse observationsGet(@PathVariable("programId") UUID programId,
                                        @Nullable @QueryValue("observationDbId") String observationDbId,
                                        @Nullable @QueryValue("observationUnitDbId") String observationUnitDbId,
                                        @Nullable @QueryValue("observationVariableDbId") String observationVariableDbId,
                                        @Nullable @QueryValue("locationDbId") String locationDbId,
                                        @Nullable @QueryValue("seasonDbId") String seasonDbId,
                                        @Nullable @QueryValue("observationTimeStampRangeStart") Date observationTimeStampRangeStart,
                                        @Nullable @QueryValue("observationTimeStampRangeEnd") Date observationTimeStampRangeEnd,
                                        @Nullable @QueryValue("observationUnitLevelName") String observationUnitLevelName,
                                        @Nullable @QueryValue("observationUnitLevelOrder") String observationUnitLevelOrder,
                                        @Nullable @QueryValue("observationUnitLevelCode") String observationUnitLevelCode,
                                        @Nullable @QueryValue("observationUnitLevelRelationshipName") String observationUnitLevelRelationshipName,
                                        @Nullable @QueryValue("observationUnitLevelRelationshipOrder") String observationUnitLevelRelationshipOrder,
                                        @Nullable @QueryValue("observationUnitLevelRelationshipCode") String observationUnitLevelRelationshipCode,
                                        @Nullable @QueryValue("observationUnitLevelRelationshipDbId") String observationUnitLevelRelationshipDbId,
                                        @Nullable @QueryValue("commonCropName") String commonCropName,
                                        @Nullable @QueryValue("programDbId") String programDbId,
                                        @Nullable @QueryValue("trialDbId") String trialDbId,
                                        @Nullable @QueryValue("studyDbId") String studyDbId,
                                        @Nullable @QueryValue("germplasmDbId") String germplasmDbId,
                                        @Nullable @QueryValue("externalReferenceID") String externalReferenceID,
                                        @Nullable @QueryValue("externalReferenceId") String externalReferenceId,
                                        @Nullable @QueryValue("externalReferenceSource") String externalReferenceSource,
                                        @Nullable @QueryValue("page") Integer page,
                                        @Nullable @QueryValue("pageSize") Integer pageSize) {
        log.debug("observationsGet: fetching observations by filters");
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("Program id: " + programId + " not found");
            return HttpResponse.notFound();
        }

        try {

            // TODO: BI-2506 - implement support for all query parameters.
            List<Object> unsupportedParams = Stream.of(
                    observationDbId,
                    observationUnitDbId,
                    observationVariableDbId,
                    locationDbId,
                    seasonDbId,
                    observationTimeStampRangeStart,
                    observationTimeStampRangeEnd,
                    observationUnitLevelName,
                    observationUnitLevelOrder,
                    observationUnitLevelCode,
                    observationUnitLevelRelationshipName,
                    observationUnitLevelRelationshipOrder,
                    observationUnitLevelRelationshipCode,
                    observationUnitLevelRelationshipDbId,
                    commonCropName,
                    programDbId,
                    trialDbId,
                    germplasmDbId,
                    externalReferenceID,
                    externalReferenceId,
                    externalReferenceSource
            ).filter(Objects::nonNull).collect(Collectors.toList());

            if (!unsupportedParams.isEmpty()) {
                return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED).body(
                        new BrAPIObservationListResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                                .message("Unsupported query parameter. Only studyDbId, page, and pageSize are supported."))))
                );
            }

            // Get a filtered list of observations.
            // TODO: Handle filtering and pagination of this request entirely on BrAPI side once study and ou cache is removed: [BI-2964]
            List<BrAPIObservation> observations = observationDAO.getObservationsByFilters(program.get(), studyDbId);

            // If page is not provided, set it to the default value 0.
            if (page == null) page = 0;
            // If pageSize is not provided, set it to the default value 1000.
            if (pageSize == null) pageSize = 1000;

            // Total number of records in the unpaged super set.
            int totalCount = observations.size();
            // The least of pageSize and totalCount, unless pageSize is zero, in which case use totalCount.
            int requestedPageSize = pageSize > 0 ? Math.min(pageSize, totalCount) : totalCount;
            // Integer division and round up.
            int totalPages = totalCount / requestedPageSize + ((totalCount % requestedPageSize == 0) ? 0 : 1);
            log.info("(Pagination) totalCount: " + totalCount + " page (0-indexed): " + page + " requestedPageSize: " + requestedPageSize + " totalPages: " + totalPages);

            // Determine validity of pagination query parameters.
            boolean pageSizeValid = pageSize > 0;
            boolean pageValid = page >= 0 && page < totalPages;

            // Only paginate if valid pagination values were sent.
            if (pageSizeValid && pageValid) {
                int start = page * requestedPageSize;
                // Account for last page, which may have fewer than requestedPageSize items, or exactly requestedPageSize items.
                int end = (page == (totalPages - 1) && totalCount % requestedPageSize != 0) ? (start + (totalCount % requestedPageSize)) : Math.min(((page + 1) * requestedPageSize), totalCount);
                log.info("(Pagination) start " + start + " end " + end);
                // Sort observations so that paging is consistent and coherent.
                observations.sort(Comparator.comparing(BrAPIObservation::getObservationDbId));
                // Paginate response.
                observations = observations.subList(start, end);
            } else {
                String errorMessage = "Invalid query parameters: page, pageSize";
                return HttpResponse.badRequest(new BrAPIObservationListResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                        .message(errorMessage)))));
            }

            return HttpResponse.ok(new BrAPIObservationListResponse().metadata(new BrAPIMetadata().pagination(new BrAPIIndexPagination()
                            .currentPage(page)
                            .totalPages(totalPages)
                            .pageSize(observations.size())
                            .totalCount(totalCount)))
                    .result(new BrAPIObservationListResponseResult().data(observations)));

        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError(new BrAPIObservationListResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                    .message("Error fetching observations")))));
        } catch (Exception e) {
            log.error("Error fetching Observations", e);
            return HttpResponse.serverError(new BrAPIObservationListResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                    .message("Error fetching observations")))));
        }
    }

    @Get("/observations/{observationDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse observationsObservationDbIdGet(@PathVariable("programId") UUID programId,
                                                       @PathVariable("observationDbId") String observationDbId) {
        return HttpResponse.notFound();
    }

    @Put("/observations/{observationDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse observationsObservationDbIdPut(@PathVariable("programId") UUID programId,
                                                       @PathVariable("observationDbId") String observationDbId,
                                                       @Body BrAPIObservation body) {
        /*
            DO NOT IMPLEMENT - users must create observations via file upload
            TODO identify how observations uploaded via BrAPI will be separated from curated observations
         */
        return HttpResponse.notFound();
    }

    @Post("/observations")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse observationsPost(@PathVariable("programId") UUID programId, @Body List<BrAPIObservation> body) {
        /*
            DO NOT IMPLEMENT - users must create observations via file upload
            TODO identify how observations uploaded via BrAPI will be separated from curated observations
         */
        return HttpResponse.notFound();
    }

    @Put("/observations")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse observationsPut(@PathVariable("programId") UUID programId, @Body Map<String, BrAPIObservation> body) {
        /*
            DO NOT IMPLEMENT - users must create observations via file upload
            TODO identify how observations uploaded via BrAPI will be separated from curated observations
         */
        return HttpResponse.notFound();
    }

    @Get("/observations/table{?queryParams*}")
    @Produces({"application/json", "text/csv", "text/tsv"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIObservationTableResponse> observationsTableGet(
            @PathVariable("programId") UUID programId,
            @QueryValue ObservationQuery queryParams) {

        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            return HttpResponse.notFound();
        }

        try {
            ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
            ApiResponse<BrAPIObservationTableResponse> response = api.observationsTableGet(BrAPIWSMIMEDataTypes.APPLICATION_JSON, queryParams.toBrAPIQueryParams());

            return HttpResponse.ok(response.getBody());
        } catch (InternalServerException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving observations table");
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving observations table");
        }
    }
}
