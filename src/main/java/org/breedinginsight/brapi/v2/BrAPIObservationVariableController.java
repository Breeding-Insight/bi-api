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
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationVariableController {
    /*
    TODO
    - GET /variables
     */

    @Get("/variables")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse variablesGet(@PathVariable("programId") UUID programId,
                                     @QueryValue("observationVariableDbId") String observationVariableDbId,
                                     @QueryValue("observationVariableName") String observationVariableName,
                                     @QueryValue("observationVariablePUI") String observationVariablePUI,
                                     @QueryValue("traitClass") String traitClass,
                                     @QueryValue("methodDbId") String methodDbId,
                                     @QueryValue("methodName") String methodName,
                                     @QueryValue("methodPUI") String methodPUI,
                                     @QueryValue("scaleDbId") String scaleDbId,
                                     @QueryValue("scaleName") String scaleName,
                                     @QueryValue("scalePUI") String scalePUI,
                                     @QueryValue("traitDbId") String traitDbId,
                                     @QueryValue("traitName") String traitName,
                                     @QueryValue("traitPUI") String traitPUI,
                                     @QueryValue("ontologyDbId") String ontologyDbId,
                                     @QueryValue("commonCropName") String commonCropName,
                                     @QueryValue("programDbId") String programDbId,
                                     @QueryValue("trialDbId") String trialDbId,
                                     @QueryValue("studyDbId") String studyDbId,
                                     @QueryValue("externalReferenceID") String externalReferenceID,
                                     @QueryValue("externalReferenceId") String externalReferenceId,
                                     @QueryValue("externalReferenceSource") String externalReferenceSource,
                                     @QueryValue("page") Integer page,
                                     @QueryValue("pageSize") Integer pageSize) {
        return HttpResponse.notFound();
    }

    @Get("/variables/{observationVariableDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse variablesObservationVariableDbIdGet(@PathVariable("programId") UUID programId,
                                                            @PathVariable("observationVariableDbId") String observationVariableDbId) {
        return HttpResponse.notFound();
    }

    @Put("/variables/{observationVariableDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse variablesObservationVariableDbIdPut(@PathVariable("programId") UUID programId,
                                                            @PathVariable("observationVariableDbId") String observationVariableDbId,
                                                            BrAPIObservationVariable body) {
        //DO NOT IMPLEMENT - Users are only able to update traits via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Post("/variables")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse variablesPost(@PathVariable("programId") UUID programId, List<BrAPIObservationVariable> body) {
        //DO NOT IMPLEMENT - Users are only able to create new traits via the DeltaBreed UI
        return HttpResponse.notFound();
    }
}
