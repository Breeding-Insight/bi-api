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
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIServerInfo;
import org.brapi.v2.model.core.response.BrAPIListsListResponse;
import org.brapi.v2.model.core.response.BrAPIServerInfoResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v2.model.response.mappers.GermplasmQueryMapper;
import org.breedinginsight.brapi.v2.model.response.mappers.ListQueryMapper;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIV2Controller {

    private final SecurityService securityService;
    private final ProgramService programService;
    private final BrAPIGermplasmService germplasmService;
    private ListQueryMapper listQueryMapper;

    @Inject
    public BrAPIV2Controller(SecurityService securityService, ProgramService programService, BrAPIGermplasmService germplasmService,
                             ListQueryMapper listQueryMapper) {
        this.securityService = securityService;
        this.programService = programService;
        this.germplasmService = germplasmService;
        this.listQueryMapper = listQueryMapper;
    }


    @Get(BrapiVersion.BRAPI_V2 + "/serverinfo")
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

    //@Get(BrapiVersion.BRAPI_V2 + "/lists")
    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/lists{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse getLists(@PathVariable("programId") UUID programId, HttpRequest<String> request,
                                 @QueryValue @QueryValid(using = ListQueryMapper.class) @Valid BrapiQuery queryParams
    ) throws DoesNotExistException, ApiException {
        List<BrAPIListSummary> brapiLists = germplasmService.getGermplasmListsByProgramId(programId, request);
        return ResponseUtils.getBrapiQueryResponse(brapiLists, listQueryMapper, queryParams);
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/{+path}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> getCatchall(@PathVariable("path") String path, @PathVariable("programId") UUID programId, HttpRequest<String> request) {
        return executeRequest(path, programId, request, "GET");
    }

    @Post("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/{+path}")
    @Consumes(MediaType.ALL)
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<String> postCatchall(@PathVariable("path") String path, @PathVariable("programId") UUID programId, HttpRequest<byte[]> request,
                                             @Header("Content-Type") String contentType) {
        return executeByteRequest(path, programId, request, contentType, "POST");
    }

    @Put("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/{+path}")
    @Consumes(MediaType.ALL)
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<String> putCatchall(@PathVariable("path") String path, @PathVariable("programId") UUID programId, HttpRequest<byte[]> request,
                                            @Header("Content-Type") String contentType) {
        return executeByteRequest(path, programId, request, contentType, "PUT");
    }

    private HttpResponse<String> executeByteRequest(String path, UUID programId, HttpRequest<byte[]> request, String contentType, String method) {
        AuthenticatedUser actingUser = securityService.getUser();

        logCall(path, request);
        if (programId != null) {
            HttpUrl requestUrl = getUrl(programId, path, request);

            var brapiRequest = new Request.Builder().url(requestUrl)
//                                                    .addHeader("Authorization", "Bearer " + token) //TODO
                    .method(method, request.getBody().isPresent() ? RequestBody.create(request.getBody().get()) : null)
                    .addHeader("Content-Type", contentType)
                    .build();

            return makeCall(brapiRequest);
        }

        throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized BrAPI Request");
    }

    private HttpResponse<String> executeRequest(String path, UUID programId, HttpRequest<String> request, String method) {
        AuthenticatedUser actingUser = securityService.getUser();

        logCall(path, request);
        if (programId != null) {
            HttpUrl requestUrl = getUrl(programId, path, request);

            var brapiRequest = new Request.Builder().url(requestUrl)
//                                                    .addHeader("Authorization", "Bearer " + token) //TODO
                                                    .method(method, request.getBody().isPresent() ? RequestBody.create(request.getBody().get(), okhttp3.MediaType.get(MediaType.APPLICATION_JSON)) : null)
                                                    .build();

            return makeCall(brapiRequest);
        }

        throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized BrAPI Request");
    }

    private HttpResponse<String> makeCall(Request brapiRequest) {
        OkHttpClient client = new OkHttpClient();
        try (Response brapiResponse = client.newCall(brapiRequest).execute()) {
            if(brapiResponse.isSuccessful()) {
                try(ResponseBody body = brapiResponse.body()) {
                    String respBody = body == null ? "" : body.string();
                    return HttpResponse.ok(respBody);
                } catch (Exception e) {
                    return HttpResponse.ok("");
                }
            } else {
                return HttpResponse.status(HttpStatus.valueOf(brapiResponse.code()));
            }
        } catch (IOException e) {
            log.error("Error calling BrAPI Service", e);
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error calling BrAPI Service");
        }
    }

    private HttpUrl getUrl(UUID programId, String path, HttpRequest<?> request) {

        ProgramBrAPIEndpoints programBrAPIEndpoints;
        try {
            programBrAPIEndpoints = programService.getBrapiEndpoints(programId);
        } catch (DoesNotExistException e) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Program does not exist");
        }

        if(programBrAPIEndpoints.getCoreUrl().isEmpty()) {
            log.error("Program: " + programId + " is missing BrAPI URL config");
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "");
        }
        var programBrAPIBaseUrl = programBrAPIEndpoints.getCoreUrl().get();
        programBrAPIBaseUrl = programBrAPIBaseUrl.endsWith("/") ? programBrAPIBaseUrl.substring(0, programBrAPIBaseUrl.length() - 1) : programBrAPIBaseUrl;
        String urlString = programBrAPIBaseUrl.endsWith(BrapiVersion.BRAPI_V2) ? programBrAPIBaseUrl : programBrAPIBaseUrl + BrapiVersion.BRAPI_V2;
        var requestUrl = HttpUrl.parse(urlString + "/" + path)
                .newBuilder();

        request.getParameters()
                .asMap()
                .entrySet()
                .stream()
                .filter(param -> !param.getKey()
                        .equals("programId"))
                .forEach(param -> param.getValue()
                        .forEach(val -> requestUrl.addQueryParameter(param.getKey(), val)));

        return requestUrl.build();
    }

    private void logCall(String path, HttpRequest<?> request) {
        log.debug("Params for brapi proxy call: " + String.join("\n",
                String.format("\npath = %s\n", path),
                request.getParameters()
                        .asMap()
                        .entrySet()
                        .stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(Collectors.joining("\n"))
        ));
    }
}
