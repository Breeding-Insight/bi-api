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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.services.ProgramUploadService;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UploadController {

    @Inject
    private ProgramUploadService uploadService;
    @Inject
    private SecurityService securityService;

    @Post("/programs/{programId}/uploads")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    // TODO: can't get micronaut to deserialize body json into programUploadRequest so using string for now and deserializing manually in service
    public HttpResponse<Response<DataResponse<ProgramUpload>>> uploadCompleted(@PathVariable UUID programId, @Part String body, @Part CompletedFileUpload file) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ProgramUpload programUpload = uploadService.create(programId, body, file, actingUser);
            Response<DataResponse<ProgramUpload>> response = new Response(programUpload);
            return HttpResponse.ok(response);
        } catch (UnprocessableEntityException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

}
