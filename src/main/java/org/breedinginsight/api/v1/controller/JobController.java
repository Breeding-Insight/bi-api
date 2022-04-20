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

package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.job.Job;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.job.JobService;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class JobController {
    private SecurityService securityService;
    private JobService jobService;

    @Inject
    public JobController(SecurityService securityService, JobService jobService) {
        this.securityService = securityService;
        this.jobService = jobService;
    }

    @Get("programs/{programId}/jobs")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<List<Job>>> getProgramUploads(@PathVariable UUID programId) {
        log.debug(String.format("fetching jobs for program: %s", programId));
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            Response<List<Job>> response = new Response(jobService.getProgramJobs(programId));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.notFound();
        }
    }
}
