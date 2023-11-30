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
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationsController {

    @Get("/observations")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
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
        //TODO
        return HttpResponse.notFound();
    }

    @Get("/observations/{observationDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationsObservationDbIdGet(@PathVariable("programId") UUID programId,
                                                       @PathVariable("observationDbId") String observationDbId) {
        return HttpResponse.notFound();
    }

    @Put("/observations/{observationDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
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
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationsPost(@PathVariable("programId") UUID programId, @Body List<BrAPIObservation> body) {
        /*
            DO NOT IMPLEMENT - users must create observations via file upload
            TODO identify how observations uploaded via BrAPI will be separated from curated observations
         */
        return HttpResponse.notFound();
    }

    @Put("/observations")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationsPut(@PathVariable("programId") UUID programId, @Body Map<String, BrAPIObservation> body) {
        /*
            DO NOT IMPLEMENT - users must create observations via file upload
            TODO identify how observations uploaded via BrAPI will be separated from curated observations
         */
        return HttpResponse.notFound();
    }

    @Get("/observations/table")
    @Produces({"application/json", "text/csv", "text/tsv"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationsTableGet(@PathVariable("programId") UUID programId,
                                             @Nullable @Header("Accept") String accept,
                                             @Nullable @QueryValue("observationUnitDbId") String observationUnitDbId,
                                             @Nullable @QueryValue("observationVariableDbId") String observationVariableDbId,
                                             @Nullable @QueryValue("locationDbId") String locationDbId,
                                             @Nullable @QueryValue("seasonDbId") String seasonDbId,
                                             @Nullable @QueryValue("observationLevel") String observationLevel,
                                             @Nullable @QueryValue("searchResultsDbId") String searchResultsDbId,
                                             @Nullable @QueryValue("observationTimeStampRangeStart") Date observationTimeStampRangeStart,
                                             @Nullable @QueryValue("observationTimeStampRangeEnd") Date observationTimeStampRangeEnd,
                                             @Nullable @QueryValue("programDbId") String programDbId,
                                             @Nullable @QueryValue("trialDbId") String trialDbId,
                                             @Nullable @QueryValue("studyDbId") String studyDbId,
                                             @Nullable @QueryValue("germplasmDbId") String germplasmDbId,
                                             @Nullable @QueryValue("observationUnitLevelName") String observationUnitLevelName,
                                             @Nullable @QueryValue("observationUnitLevelOrder") String observationUnitLevelOrder,
                                             @Nullable @QueryValue("observationUnitLevelCode") String observationUnitLevelCode,
                                             @Nullable @QueryValue("observationUnitLevelRelationshipName") String observationUnitLevelRelationshipName,
                                             @Nullable @QueryValue("observationUnitLevelRelationshipOrder") String observationUnitLevelRelationshipOrder,
                                             @Nullable @QueryValue("observationUnitLevelRelationshipCode") String observationUnitLevelRelationshipCode,
                                             @Nullable @QueryValue("observationUnitLevelRelationshipDbId") String observationUnitLevelRelationshipDbId) {
        return HttpResponse.notFound();
    }
}
