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
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.job.Job;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.job.JobService;

import javax.inject.Inject;
import java.util.ArrayList;
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
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<Job>>> getProgramJobs(@PathVariable UUID programId) {
        log.debug(String.format("fetching jobs for program: %s", programId));
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            var programJobs = jobService.getProgramJobs(programId);
            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            Pagination pagination = new Pagination(programJobs.size(), programJobs.size(), 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<Job>> response = new Response<>(metadata, new DataResponse<>(programJobs));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.notFound();
        }
    }
}
