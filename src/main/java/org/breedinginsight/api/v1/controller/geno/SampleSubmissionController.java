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

package org.breedinginsight.api.v1.controller.geno;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.geno.BrAPIVendorOrderSubmission;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.model.*;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.SampleSubmissionService;
import org.breedinginsight.services.UserService;
import org.breedinginsight.utilities.Utilities;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class SampleSubmissionController {

    private final boolean brapiSubmissionEnabled;
    private final SampleSubmissionService sampleSubmissionService;
    private final ProgramService programService;
    private final SecurityService securityService;
    private final UserService userService;

    private final Gson gson;

    @Inject
    public SampleSubmissionController(@Property(name = "brapi.vendors.submission-enabled") boolean brapiSubmissionEnabled, SampleSubmissionService sampleSubmissionService, ProgramService programService, SecurityService securityService, UserService userService) {
        this.brapiSubmissionEnabled = brapiSubmissionEnabled;
        this.sampleSubmissionService = sampleSubmissionService;
        this.programService = programService;
        this.securityService = securityService;
        this.userService = userService;
        this.gson = new Gson();
    }

    @Get("programs/{programId}/submissions")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = ProgramSecuredRoleGroup.ALL)
    public HttpResponse<Response<DataResponse<SampleSubmission>>> getProgramSampleSubmissions(@PathVariable UUID programId) {
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.info(String.format("programId not found: %s", programId.toString()));
            return HttpResponse.notFound();
        }

        List<SampleSubmission> submissions = sampleSubmissionService.getProgramSubmissions(program.get());
        Metadata metadata = new Metadata(new Pagination(submissions.size(), submissions.size(), 1, 0),
                List.of(new Status(StatusCode.INFO, "Successful Query")));
        Response<DataResponse<SampleSubmission>> response = new Response<>(metadata, new DataResponse<>(submissions));
        return HttpResponse.ok(response);
    }

    @Get("programs/{programId}/submissions/{submissionId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = ProgramSecuredRoleGroup.ALL)
    public HttpResponse<Response<SampleSubmission>> getSubmissionById(@PathVariable UUID programId, @PathVariable UUID submissionId, @QueryValue(value = "details", defaultValue = "false") @Nullable Boolean fetchDetails) {
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.info(String.format("programId not found: %s", programId.toString()));
            return HttpResponse.notFound();
        }

        try {
            Optional<SampleSubmission> submission = sampleSubmissionService.getSampleSubmission(program.get(), submissionId, fetchDetails);

            if(submission.isEmpty()) {
                return HttpResponse.notFound();
            }

            Metadata metadata = new Metadata(new Pagination(1, 1, 1, 0),
                    List.of(new Status(StatusCode.INFO, "Successful Query")));
            Response<SampleSubmission> response = new Response<>(metadata, submission.get());
            return HttpResponse.ok(response);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }
    }

    @Put("programs/{programId}/submissions/{submissionId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<SampleSubmission>> updateSubmissionStatus(@PathVariable UUID programId, @PathVariable UUID submissionId, @Body String body) {
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.info(String.format("programId not found: %s", programId.toString()));
            return HttpResponse.notFound();
        }

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<User> user = userService.getById(actingUser.getId());
        if (user.isEmpty()) {
            return HttpResponse.unauthorized();
        }

        SampleSubmission.Status status = SampleSubmission.Status.fromValue(gson.fromJson(body, JsonObject.class).get("status").getAsString());
        if(status == null) {
            HttpResponse response = HttpResponse.badRequest("Unrecognized status");
            return response;
        }

        try {
            Optional<SampleSubmission> submission = sampleSubmissionService.updateSubmissionStatus(program.get(), submissionId, status, user.get());

            if(submission.isEmpty()) {
                return HttpResponse.notFound();
            }

            Metadata metadata = new Metadata(new Pagination(1, 1, 1, 0),
                    List.of(new Status(StatusCode.INFO, "Successful Update")));
            Response<SampleSubmission> response = new Response<>(metadata, submission.get());
            return HttpResponse.ok(response);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }
    }


    @Get("/programs/{programId}/submissions/{submissionId}/dart")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(value={"text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"})
    public HttpResponse<StreamedFile> generateDArTFile(@PathVariable UUID programId, @PathVariable UUID submissionId) {
        try {
            Optional<Program> program = programService.getById(programId);
            if(program.isEmpty()) {
                return HttpResponse.notFound();
            }
            Optional<DownloadFile> downloadFile = sampleSubmissionService.generateDArTFile(program.get(), submissionId);
            if(downloadFile.isEmpty()) {
                return HttpResponse.notFound();
            }
            HttpResponse<StreamedFile> response = HttpResponse
                    .ok(downloadFile.get().getStreamedFile())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + downloadFile.get().getFileName());
            return response;
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        } catch (IOException e) {
            log.error("Error generating DArT file", e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating DArT file").contentType(MediaType.TEXT_PLAIN).body("Error generating DArT file");
            return response;
        }
    }

    @Get("/programs/{programId}/submissions/{submissionId}/lookup")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    @Produces(value={"text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"})
    public HttpResponse<StreamedFile> generateLookupFile(@PathVariable UUID programId, @PathVariable UUID submissionId) {
        try {
            Optional<Program> program = programService.getById(programId);
            if(program.isEmpty()) {
                return HttpResponse.notFound();
            }
            Optional<DownloadFile> downloadFile = sampleSubmissionService.generateLookupFile(program.get(), submissionId);
            if(downloadFile.isEmpty()) {
                return HttpResponse.notFound();
            }
            HttpResponse<StreamedFile> response = HttpResponse
                    .ok(downloadFile.get().getStreamedFile())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + downloadFile.get().getFileName());
            return response;
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        } catch (IOException e) {
            log.error("Error generating lookup file", e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating lookup file").contentType(MediaType.TEXT_PLAIN).body("Error generating lookup file");
            return response;
        }
    }

    @Post("programs/{programId}/submissions/{submissionId}/submit")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<BrAPIVendorOrderSubmission>> submitOrder(@PathVariable UUID programId, @PathVariable UUID submissionId, @QueryValue(value = "vendor") @NotBlank String vendorName) {
        if(!brapiSubmissionEnabled) {
            return HttpResponse.notFound();
        }

        try {
            GenotypeVendor vendor = GenotypeVendor.fromName(vendorName);
            if(vendor != null) {
                Optional<Program> program = programService.getById(programId);
                if (program.isEmpty()) {
                    return HttpResponse.notFound();
                }

                AuthenticatedUser actingUser = securityService.getUser();
                Optional<User> user = userService.getById(actingUser.getId());
                if (user.isEmpty()) {
                    return HttpResponse.unauthorized();
                }

                Optional<BrAPIVendorOrderSubmission> order = sampleSubmissionService.submitOrder(program.get(), submissionId, user.get(), vendor);
                if (order.isEmpty()) {
                    return HttpResponse.notFound();
                }

                Metadata metadata = new Metadata(new Pagination(1, 1, 1, 0),
                        List.of(new Status(StatusCode.INFO, "Successful submission")));
                Response<BrAPIVendorOrderSubmission> response = new Response<>(metadata, order.get());
                return HttpResponse.ok(response);
            } else {
                HttpResponse response = HttpResponse.badRequest("Unrecognized vendor");
                return response;
            }
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }
    }

    @Get("programs/{programId}/submissions/{submissionId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<SampleSubmission>> checkVendorStatus(@PathVariable UUID programId, @PathVariable UUID submissionId) {
        if(!brapiSubmissionEnabled) {
            return HttpResponse.notFound();
        }

        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.info(String.format("programId not found: %s", programId.toString()));
            return HttpResponse.notFound();
        }

        try {
            Optional<SampleSubmission> submission = sampleSubmissionService.checkVendorStatus(program.get(), submissionId);

            if(submission.isEmpty()) {
                return HttpResponse.notFound();
            }

            Metadata metadata = new Metadata(new Pagination(1, 1, 1, 0),
                    List.of(new Status(StatusCode.INFO, "Successful Query")));
            Response<SampleSubmission> response = new Response<>(metadata, submission.get());
            return HttpResponse.ok(response);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }
    }
}
