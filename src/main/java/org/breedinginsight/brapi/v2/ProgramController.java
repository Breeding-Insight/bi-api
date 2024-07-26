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
import org.brapi.v2.model.core.BrAPIProgram;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.breedinginsight.utilities.response.ResponseUtils.getBrapiSingleResponse;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProgramController {

    private final ProgramService programService;

    @Inject
    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    /**
     * Retrieves a list of programs by programId. Query parameters not implemented, will only ever return one program.
     *
     * @param programId The ID of the program.
     * @return HttpResponse containing a Response object with a DataResponse object that wraps a list of programs.
     * Returns HttpResponse.NOT_FOUND if the program is not found.
     */
    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<org.breedinginsight.api.model.v1.response.Response<DataResponse<List<String>>>> getPrograms(
            @PathVariable("programId") UUID programId) {

        Optional<Program> optProgram = programService.getById(programId);
        if (optProgram.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND, "Program not found");
        }

        Program program = optProgram.get();
        BrAPIProgram brapiProgram = program.getBrapiProgram();
        // Use name as in DeltaBreed remove program key
        brapiProgram.setProgramName(program.getName());
        List<BrAPIProgram> programs = List.of(brapiProgram);
        return getBrapiSingleResponse(programs);
    }

}
