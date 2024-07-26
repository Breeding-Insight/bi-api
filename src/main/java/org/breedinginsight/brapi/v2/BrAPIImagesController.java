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
import org.brapi.v2.model.pheno.BrAPIImage;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIImagesController {
    /*
    TODO
    - POST images
    - PUT imagesImageDbIdImagecontent
    - PUT imagesImageDbIdPut
     */
    @Get("/images")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse imagesGet(@PathVariable("programId") UUID programId,
                                  @QueryValue("imageDbId") String imageDbId,
                                  @QueryValue("imageName") String imageName,
                                  @QueryValue("observationUnitDbId") String observationUnitDbId,
                                  @QueryValue("observationDbId") String observationDbId,
                                  @QueryValue("descriptiveOntologyTerm") String descriptiveOntologyTerm,
                                  @QueryValue("commonCropName") String commonCropName,
                                  @QueryValue("programDbId") String programDbId,
                                  @QueryValue("externalReferenceID") String externalReferenceID,
                                  @QueryValue("externalReferenceId") String externalReferenceId,
                                  @QueryValue("externalReferenceSource") String externalReferenceSource,
                                  @QueryValue("page") Integer page,
                                  @QueryValue("pageSize") Integer pageSize) {
        return HttpResponse.notFound();
    }

    @Get("/images/{imageDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse imagesImageDbIdGet(@PathVariable("programId") UUID programId,
                                           @PathVariable("imageDbId") String imageDbId) {
        return HttpResponse.notFound();
    }

    @Put("/images/{imageDbId}/imagecontent")
    @Consumes({"image/_*"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse imagesImageDbIdImagecontentPut(@PathVariable("programId") UUID programId,
                                                       @PathVariable("imageDbId") String imageDbId,
                                                       @Body Object body) {
        return HttpResponse.notFound();
    }

    @Put("/images/{imageDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse imagesImageDbIdPut(@PathVariable("programId") UUID programId,
                                           @PathVariable("imageDbId") String imageDbId,
                                           @Body BrAPIImage body) {
        return HttpResponse.notFound();
    }

    @Post("/images")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse imagesPost(@PathVariable("programId") UUID programId, @Body List<BrAPIImage> body) {
        return HttpResponse.notFound();
    }
}
