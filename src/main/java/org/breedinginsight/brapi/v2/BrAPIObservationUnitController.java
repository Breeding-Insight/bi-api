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
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationUnitController {

    /*
        TODO
        - GET observationLevels
        - GET observationUnits
     */
    @Get("/observationunits")
    public HttpResponse observationunitsGet(@PathVariable("programId") UUID programId,
                                            @QueryValue("observationUnitDbId") String observationUnitDbId,
                                            @QueryValue("observationUnitName") String observationUnitName,
                                            @QueryValue("locationDbId") String locationDbId,
                                            @QueryValue("seasonDbId") String seasonDbId,
                                            @QueryValue("includeObservations") Boolean includeObservations,
                                            @QueryValue("observationUnitLevelName") String observationUnitLevelName,
                                            @QueryValue("observationUnitLevelOrder") String observationUnitLevelOrder,
                                            @QueryValue("observationUnitLevelCode") String observationUnitLevelCode,
                                            @QueryValue("observationUnitLevelRelationshipName") String observationUnitLevelRelationshipName,
                                            @QueryValue("observationUnitLevelRelationshipOrder") String observationUnitLevelRelationshipOrder,
                                            @QueryValue("observationUnitLevelRelationshipCode") String observationUnitLevelRelationshipCode,
                                            @QueryValue("observationUnitLevelRelationshipDbId") String observationUnitLevelRelationshipDbId,
                                            @QueryValue("commonCropName") String commonCropName,
                                            @QueryValue("programDbId") String programDbId,
                                            @QueryValue("trialDbId") String trialDbId,
                                            @QueryValue("studyDbId") String studyDbId,
                                            @QueryValue("germplasmDbId") String germplasmDbId,
                                            @QueryValue("externalReferenceID") String externalReferenceID,
                                            @QueryValue("externalReferenceId") String externalReferenceId,
                                            @QueryValue("externalReferenceSource") String externalReferenceSource,
                                            @QueryValue("page") Integer page,
                                            @QueryValue("pageSize") Integer pageSize) {
        return HttpResponse.notFound();
    }

    @Get("/observationunits/{observationUnitDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationunitsObservationUnitDbIdGet(@PathVariable("programId") UUID programId, @PathVariable("observationUnitDbId") String observationUnitDbId) {
        return HttpResponse.notFound();
    }

    @Put("/observationunits/{observationUnitDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationunitsObservationUnitDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("observationUnitDbId") String observationUnitDbId, BrAPIObservationUnit body) {
        return HttpResponse.notFound();
    }

    @Post("/observationunits")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationunitsPost(@PathVariable("programId") UUID programId, List<BrAPIObservationUnit> body) {
        return HttpResponse.notFound();
    }

    @Put("/observationunits")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationunitsPut(@PathVariable("programId") UUID programId, Map<String, BrAPIObservationUnit> body) {
        return HttpResponse.notFound();
    }

    @Get("/observationunits/table")
    @Produces({"application/json", "text/csv", "text/tsv"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse observationunitsTableGet(@PathVariable("programId") UUID programId,
                                                 @Header("Accept") String accept,
                                                 @QueryValue("observationUnitDbId") String observationUnitDbId,
                                                 @QueryValue("observationVariableDbId") String observationVariableDbId,
                                                 @QueryValue("locationDbId") String locationDbId,
                                                 @QueryValue("seasonDbId") String seasonDbId,
                                                 @QueryValue("observationLevel") String observationLevel,
                                                 @QueryValue("programDbId") String programDbId,
                                                 @QueryValue("trialDbId") String trialDbId,
                                                 @QueryValue("studyDbId") String studyDbId,
                                                 @QueryValue("germplasmDbId") String germplasmDbId,
                                                 @QueryValue("observationUnitLevelName") String observationUnitLevelName,
                                                 @QueryValue("observationUnitLevelOrder") String observationUnitLevelOrder,
                                                 @QueryValue("observationUnitLevelCode") String observationUnitLevelCode,
                                                 @QueryValue("observationUnitLevelRelationshipName") String observationUnitLevelRelationshipName,
                                                 @QueryValue("observationUnitLevelRelationshipOrder") String observationUnitLevelRelationshipOrder,
                                                 @QueryValue("observationUnitLevelRelationshipCode") String observationUnitLevelRelationshipCode,
                                                 @QueryValue("observationUnitLevelRelationshipDbId") String observationUnitLevelRelationshipDbId) {
        return HttpResponse.notFound();
    }
}
