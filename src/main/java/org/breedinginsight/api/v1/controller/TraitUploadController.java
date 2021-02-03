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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.HttpBadRequestException;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.model.v1.validators.SearchValid;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.TraitUploadService;
import org.breedinginsight.services.exceptions.*;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.TraitQueryMapper;

import javax.validation.Valid;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TraitUploadController {

    private TraitUploadService traitUploadService;
    private SecurityService securityService;
    private TraitQueryMapper traitQueryMapper;

    public TraitUploadController(TraitUploadService traitUploadService, SecurityService securityService,
                                 TraitQueryMapper traitQueryMapper) {
        this.traitUploadService = traitUploadService;
        this.securityService = securityService;
        this.traitQueryMapper = traitQueryMapper;
    }

    // only allowing one trait upload to exist (per user per program) so put is more appropriate than post
    // singleton resource since only one trait upload can exist
    // no need for an id, trait-upload is the resource identifier
    @Put("/programs/{programId}/trait-upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<ProgramUpload>> putTraitUpload(@PathVariable UUID programId, @Part CompletedFileUpload file) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ProgramUpload programUpload = traitUploadService.updateTraitUpload(programId, file, actingUser);
            Response<ProgramUpload> response = new Response(programUpload);
            return HttpResponse.ok(response);
        } catch (HttpBadRequestException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (UnsupportedTypeException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
        } catch (ValidatorException e){
            log.info(e.getErrors().toString());
            HttpResponse response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getErrors());
            return response;
        }
    }

    @Get("/programs/{programId}/trait-upload{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<ProgramUpload>> getTraitUpload(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = TraitQueryMapper.class) @Valid QueryParams queryParams) {

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<ProgramUpload<Trait>> programUpload = traitUploadService.getTraitUpload(programId, actingUser);

        if(programUpload.isPresent()) {
            return ResponseUtils.getUploadQueryResponse(programUpload.get(), traitQueryMapper, queryParams);
        } else {
            log.info("Trait upload not found");
            return HttpResponse.notFound();
        }
    }

    @Post("/programs/{programId}/trait-upload/search{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<ProgramUpload>> searchTraitUpload(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = TraitQueryMapper.class) @Valid QueryParams queryParams,
            @Body @SearchValid(using = TraitQueryMapper.class) SearchRequest searchRequest) {

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<ProgramUpload<Trait>> programUpload = traitUploadService.getTraitUpload(programId, actingUser);

        if(programUpload.isPresent()) {
            return ResponseUtils.getUploadQueryResponse(programUpload.get(), traitQueryMapper, searchRequest, queryParams);
        } else {
            log.info("Trait upload not found");
            return HttpResponse.notFound();
        }
    }

    @Delete("/programs/{programId}/trait-upload")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse deleteTraitUpload(@PathVariable UUID programId) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            traitUploadService.deleteTraitUpload(programId, actingUser);
            return HttpResponse.ok();
        } catch(DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }

    }


}
