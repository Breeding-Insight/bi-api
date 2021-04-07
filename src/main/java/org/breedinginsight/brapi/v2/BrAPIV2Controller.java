/*
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.brapi.v2.model.core.BrAPIServerInfo;
import org.brapi.v2.model.core.response.BrAPIServerInfoResponse;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Controller(BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIV2Controller {

    private final SecurityService securityService;
    private final ProgramService programService;

    @Inject
    public BrAPIV2Controller(SecurityService securityService, ProgramService programService) {
        this.securityService = securityService;
        this.programService = programService;
    }


    @Get("/serverinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public BrAPIServerInfoResponse serverinfo() {
        BrAPIServerInfo serverInfo = new BrAPIServerInfo();
        serverInfo.setOrganizationName("Breeding Insight Platform");
        serverInfo.setServerName("bi-api");
        serverInfo.setContactEmail("bidevteam@cornell.edu");
        serverInfo.setOrganizationURL("breedinginsight.org");

        return new BrAPIServerInfoResponse().result(serverInfo);
    }

    @Get("/{+path}")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<?> getCatchall(@PathVariable String path, HttpRequest<String> request) {
        return executeRequest(path, request, "GET");
    }

    @Post("/{+path}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> postCatchall(@PathVariable String path, HttpRequest<String> request) {
        return executeRequest(path, request, "POST");
    }

    @Put("/{+path}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> putCatchall(@PathVariable String path, HttpRequest<String> request) {
        return executeRequest(path, request, "PUT");
    }

    private HttpResponse<String> executeRequest(String path, HttpRequest<String> request, String method) {
        AuthenticatedUser actingUser = securityService.getUser();
        System.out.println(actingUser.getId());

        System.out.println("Params");
        request.getParameters()
               .forEach((key, vals) -> System.out.println(key + ": " + vals));

        /*
        TODO evaluate a few options:
        1. pass custom "programId" param for each BrAPI request
        2. embed the programId in the JWT (limited to one program)
        3. use "programDbId" so it matches BrAPI, but the value will be what's in BI's db, not the BrAPI service
        4. have brapi requests be per-program -> /programs/{programId}/brapi/v2

        Another question...do we want to swap out all programDbIds with the programId in BI's db?
         */
        if (request.getParameters().get("programId") != null) {
            String programId = request.getParameters().get("programId");
            ProgramBrAPIEndpoints programBrAPIEndpoints;
            try {
                programBrAPIEndpoints = programService.getBrapiEndpoints(UUID.fromString(Objects.requireNonNull(programId)));
            } catch (DoesNotExistException e) {
                return HttpResponse.notFound("Program does not exist");
            }

            OkHttpClient client = new OkHttpClient();

            if(programBrAPIEndpoints.getCoreUrl().isEmpty()) {
                log.error("Program: " + programId + " is missing BrAPI URL config");
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "");
            }
            var programBrAPIBaseUrl = programBrAPIEndpoints.getCoreUrl().get();
            programBrAPIBaseUrl = programBrAPIBaseUrl.endsWith("/") ? programBrAPIBaseUrl.substring(0, programBrAPIBaseUrl.length() - 1) : programBrAPIBaseUrl;
            var requestUrl = HttpUrl.parse(programBrAPIBaseUrl + BrapiVersion.BRAPI_V2 + "/" + path)
                                    .newBuilder();

            request.getParameters()
                   .asMap()
                   .entrySet()
                   .stream()
                   .filter(param -> !param.getKey()
                                          .equals("programId"))
                   .forEach(param -> param.getValue()
                                          .forEach(val -> requestUrl.addQueryParameter(param.getKey(), val)));

            var brapiRequest = new Request.Builder().url(requestUrl.build())
//                                                    .addHeader("Authorization", "Bearer " + token) //TODO
                                                    .method(method, request.getBody().isPresent() ? RequestBody.create(request.getBody().get(), okhttp3.MediaType.get(MediaType.APPLICATION_JSON)) : null)
                                                    .build();

            try (Response brapiResponse = client.newCall(brapiRequest).execute()) {
                if(brapiResponse.isSuccessful()) {
                    try(ResponseBody body = brapiResponse.body()) {
                        String respBody = body == null ? "" : body.string();
                        return HttpResponse.ok(respBody);
                    }
                } else if(brapiResponse.code() == HttpStatus.NOT_FOUND.getCode()) {
                    return HttpResponse.notFound();
                }
            } catch (IOException e) {
                log.error("Error calling BrAPI Service", e);
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error calling BrAPI Service");
            }
        }

        throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized BrAPI Request");
    }
}
