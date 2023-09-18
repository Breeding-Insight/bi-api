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
import org.brapi.v2.model.core.BrAPIProgram;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIProgramsController {
    /*
    TODO
    - GET programs
     */

    //START - endpoints at root BrAPI url
    @Get(BrapiVersion.BRAPI_V2 + "/programs")
    public HttpResponse rootProgramsGet(@QueryValue("abbreviation") String abbreviation,
                                    @QueryValue("programType") String programType,
                                    @QueryValue("commonCropName") String commonCropName,
                                    @QueryValue("programDbId") String programDbId,
                                    @QueryValue("programName") String programName,
                                    @QueryValue("externalReferenceID") String externalReferenceID,
                                    @QueryValue("externalReferenceId") String externalReferenceId,
                                    @QueryValue("externalReferenceSource") String externalReferenceSource,
                                    @QueryValue("page") Integer page,
                                    @QueryValue("pageSize") Integer pageSize) {
        return HttpResponse.notFound();
    }

    @Post(BrapiVersion.BRAPI_V2 + "/programs")
    public HttpResponse rootProgramsPost(List<BrAPIProgram> body) {
        //DO NOT IMPLEMENT - Users should only be able to create new programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Get(BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    public HttpResponse rootProgramsProgramDbIdGet(@PathVariable("programDbId") String programDbId) {
        return HttpResponse.notFound();
    }

    @Put(BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    public HttpResponse rootProgramsProgramDbIdPut(@PathVariable("programDbId") String programDbId, BrAPIProgram body) {
        //DO NOT IMPLEMENT - Users should only be able to update programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }
    //END - endpoints at root BrAPI url


    //START - endpoints for within the context of a program
    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs")
    public HttpResponse programsGet(@PathVariable("programId") UUID programId, @QueryValue("abbreviation") String abbreviation,
                                    @QueryValue("programType") String programType,
                                    @QueryValue("commonCropName") String commonCropName,
                                    @QueryValue("programDbId") String programDbId,
                                    @QueryValue("programName") String programName,
                                    @QueryValue("externalReferenceID") String externalReferenceID,
                                    @QueryValue("externalReferenceId") String externalReferenceId,
                                    @QueryValue("externalReferenceSource") String externalReferenceSource,
                                    @QueryValue("page") Integer page,
                                    @QueryValue("pageSize") Integer pageSize) {
        return HttpResponse.notFound();
    }

    @Post("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs")
    public HttpResponse programsPost(@PathVariable("programId") UUID programId, List<BrAPIProgram> body) {
        //DO NOT IMPLEMENT - Users should only be able to create new programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    public HttpResponse programsProgramDbIdGet(@PathVariable("programId") UUID programId, @PathVariable("programDbId") String programDbId) {
        return HttpResponse.notFound();
    }

    @Put("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    public HttpResponse programsProgramDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("programDbId") String programDbId, BrAPIProgram body) {
        //DO NOT IMPLEMENT - Users should only be able to update programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }
    //END - endpoints for within the context of a program
}
