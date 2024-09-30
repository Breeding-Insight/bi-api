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
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIProgramListResponseResult;
import org.brapi.v2.model.core.response.BrAPIProgramSingleResponse;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIProgramsController {

    private final SecurityService securityService;
    private final ProgramService programService;

    @Inject
    public BrAPIProgramsController(SecurityService securityService, ProgramService programService) {
        this.securityService = securityService;
        this.programService = programService;
    }

    /*
    TODO
    - GET programs
     */

    //START - endpoints at root BrAPI url
    @Get(BrapiVersion.BRAPI_V2 + "/programs")
    public HttpResponse<BrAPIProgramListResponse> rootProgramsGet(
            @Nullable  @QueryValue("abbreviation") String abbreviation,
            @Nullable @QueryValue("programType") String programType,
            @Nullable @QueryValue("commonCropName") String commonCropName,
            @Nullable @QueryValue("programDbId") String programDbId,
            @Nullable @QueryValue("programName") String programName,
            @Nullable @QueryValue("externalReferenceID") String externalReferenceID,
            @Nullable @QueryValue("externalReferenceId") String externalReferenceId,
            @Nullable @QueryValue("externalReferenceSource") String externalReferenceSource,
            @Nullable @QueryValue("page") Integer page,
            @Nullable @QueryValue("pageSize") Integer pageSize) {

        Optional<String> abbreviationOptional = Optional.ofNullable(abbreviation);
        Optional<String> programDbIdOptional = Optional.ofNullable(programDbId);
        Optional<String> programNameOptional = Optional.ofNullable(programName);

        List<BrAPIProgram> programs = programService.getAll(securityService.getUser()).stream().filter(program -> {
            boolean matches = abbreviationOptional.map(abbr -> abbr.equals(program.getKey())).orElse(true);
            matches = matches && programDbIdOptional.map(id -> id.equals(program.getId().toString())).orElse(true);
            return matches && programNameOptional.map(name -> name.equals(program.getName())).orElse(true);
        }).map(this::convertToBrAPI).collect(Collectors.toList());

        return HttpResponse.ok(new BrAPIProgramListResponse().metadata(new BrAPIMetadata().pagination(new BrAPIIndexPagination().currentPage(0)
                                                                                                                                .totalPages(1)
                                                                                                                                .totalCount(programs.size())
                                                                                                                                .pageSize(programs.size())))
                                                             .result(new BrAPIProgramListResponseResult().data(programs)));
    }

    @Post(BrapiVersion.BRAPI_V2 + "/programs")
    public HttpResponse<?> rootProgramsPost(@Body List<BrAPIProgram> body) {
        //DO NOT IMPLEMENT - Users should only be able to create new programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Get(BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    public HttpResponse<BrAPIProgramSingleResponse> rootProgramsProgramDbIdGet(@PathVariable("programDbId") String programDbId) {
        HttpResponse<BrAPIProgramListResponse> brAPIProgramListResponseHttpResponse = this.rootProgramsGet(null, null, null,
                                                                                                           programDbId,
                                                                                                           null,
                                                                                                           null,
                                                                                                           null,
                                                                                                           null,
                                                                                                           null,
                                                                                                           null);
        Optional<BrAPIProgram> program = Optional.ofNullable(brAPIProgramListResponseHttpResponse.body())
                                              .orElse(new BrAPIProgramListResponse())
                                                                      .getResult()
                                                                      .getData().stream().findFirst();

        return HttpResponse.ok(new BrAPIProgramSingleResponse().result(program.orElse(null)));
    }

    @Put(BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    public HttpResponse<?> rootProgramsProgramDbIdPut(@PathVariable("programDbId") String programDbId, @Body BrAPIProgram body) {
        //DO NOT IMPLEMENT - Users should only be able to update programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }
    //END - endpoints at root BrAPI url


    //START - endpoints for within the context of a program
    /* Retrieves a list of programs
        * If programId supplied will only ever return one program.
        *
        * @param programId The ID of the program.
        *
        * @return HttpResponse containing BrAPIProgramListResponse
        * Returns HttpResponse.NOT_FOUND if the program is not found.
    */
    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIProgramListResponse> programsGet(@PathVariable("programId") UUID programId,
                                    @QueryValue("abbreviation") Optional<String> abbreviation,
                                    @QueryValue("programType") Optional<String> programType,
                                    @QueryValue("commonCropName") Optional<String> commonCropName,
                                    @QueryValue("programDbId") Optional<String> programDbId,
                                    @QueryValue("programName") Optional<String> programName,
                                    @QueryValue("externalReferenceID") Optional<String> externalReferenceID,
                                    @QueryValue("externalReferenceId") Optional<String> externalReferenceId,
                                    @QueryValue("externalReferenceSource") Optional<String> externalReferenceSource,
                                    @QueryValue("page") Optional<Integer> page,
                                    @QueryValue("pageSize") Optional<Integer> pageSize) {

        //If programId supplied, check if program exists
        if (programId != null) {
            Optional<Program> optProgram = programService.getById(programId);
            if (optProgram.isEmpty()) {
                return HttpResponse.status(HttpStatus.NOT_FOUND, "Program not found");
            }
        }

        List<BrAPIProgram> programs = programService.getById(programId).stream().filter(program -> {
            boolean matches = abbreviation.map(abbr -> abbr.equals(program.getKey())).orElse(true);
            matches = matches && programDbId.map(id -> id.equals(program.getId().toString())).orElse(true);
            return matches && programName.map(name -> name.equals(program.getName())).orElse(true);
        }).map(this::convertToBrAPI).collect(Collectors.toList());

        return HttpResponse.ok(new BrAPIProgramListResponse().metadata(new BrAPIMetadata().pagination(new BrAPIIndexPagination().currentPage(0)
                                                                                                                                .totalPages(1)
                                                                                                                                .totalCount(programs.size())
                                                                                                                                .pageSize(programs.size())))
                                                             .result(new BrAPIProgramListResponseResult().data(programs)));
    }

    @Post("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<?> programsPost(@PathVariable("programId") UUID programId, @Body List<BrAPIProgram> body) {
        //DO NOT IMPLEMENT - Users should only be able to create new programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIProgramSingleResponse> programsProgramDbIdGet(@PathVariable("programId") UUID programId, @PathVariable("programDbId") String programDbId) {
        Optional<BrAPIProgram> program = programService.getById(programId)
                                                       .stream()
                                                       .filter(p -> programDbId.equals(programId.toString()))
                                                       .map(this::convertToBrAPI)
                                                       .findFirst();

        return HttpResponse.ok(new BrAPIProgramSingleResponse().result(program.orElse(null)));
    }

    @Put("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs/{programDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<?> programsProgramDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("programDbId") String programDbId, @Body BrAPIProgram body) {
        //DO NOT IMPLEMENT - Users should only be able to update programs via the DeltaBreed UI
        return HttpResponse.notFound();
    }
    //END - endpoints for within the context of a program

    private BrAPIProgram convertToBrAPI(Program program) {
        return new BrAPIProgram().programName(program.getName())
                                 .programDbId(program.getId().toString())
                                 .abbreviation(program.getKey())
                                 .commonCropName(program.getSpecies().getCommonName());
    }
}
