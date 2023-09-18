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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.model.request.query.StudyQuery;
import org.breedinginsight.brapi.v2.services.BrAPIStudyService;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.StudyQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIStudiesController {

    private final BrAPIStudyService studyService;
    private final StudyQueryMapper studyQueryMapper;


    @Inject
    public BrAPIStudiesController(BrAPIStudyService studyService, StudyQueryMapper studyQueryMapper) {
        this.studyService = studyService;
        this.studyQueryMapper = studyQueryMapper;
    }

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/studies{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<List<BrAPIStudy>>>> getStudies(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = StudyQueryMapper.class) @Valid StudyQuery queryParams) {
        try {
            log.debug("fetching studies for program: " + programId);

            List<BrAPIStudy> studies = studyService.getStudies(programId);
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
        }
    }

    /*
    TODO
    - GET studies/{id}
     */
    @Post("/studies")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse studiesPost(@PathVariable("programId") UUID programId, List<BrAPIStudy> body) {
        //DO NOT IMPLEMENT - Users are only able to create new studies via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Get("/studies/{studyDbId}")
    @Produces({"application/json"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse studiesStudyDbIdGet(@PathVariable("programId") UUID programId, @PathVariable("studyDbId") String studyDbId) {
        return HttpResponse.notFound();
    }

    @Put("/studies/{studyDbId}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse studiesStudyDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("studyDbId") String studyDbId,
                                            BrAPIStudy body) {
        //DO NOT IMPLEMENT - Users are only able to update studies via the DeltaBreed UI
        return HttpResponse.notFound();
    }
}
