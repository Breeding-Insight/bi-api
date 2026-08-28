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
import org.brapi.v2.model.core.BrAPIServerInfo;
import org.brapi.v2.model.core.BrAPIService;
import org.brapi.v2.model.core.response.BrAPIServerInfoResponse;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIV2Controller {

    private final SecurityService securityService;
    private final ProgramService programService;

    @Inject
    public BrAPIV2Controller(SecurityService securityService, ProgramService programService) {
        this.securityService = securityService;
        this.programService = programService;
    }


    @Get("/${micronaut.bi.api.version}" + BrapiVersion.BRAPI_V2 + "/serverinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public BrAPIServerInfoResponse serverinfo() {
        BrAPIServerInfo serverInfo = new BrAPIServerInfo();
        setBrAPIServerInfo(serverInfo);
        serverInfo.setServerDescription("DeltaBreed provides server information and program discovery at this root. Program-scoped BrAPI calls use https://app.breedinginsight.net/v1/programs/{programId}/brapi/v2");

        serverInfo.setCalls(
                new ServiceBuilder().versions("2.0", "2.1")
                        .setBase("serverinfo").GET().build()
                        .setBase("programs").GET().addPath("{programDbId}").GET().build()
        );

        return new BrAPIServerInfoResponse().result(serverInfo);
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/serverinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public BrAPIServerInfoResponse programServerinfo(@PathVariable("programId") UUID programId) {
        List<BrAPIService> programServices = new ServiceBuilder()
                .versions("2.0", "2.1")
                //CORE
                .setBase("serverinfo").GET().build()
                .setBase("commoncropnames").GET().build()
                .setBase("lists").GET().addPath("{listDbId}").DELETE().build()
                .setBase("programs").GET().POST().addPath("{programDbId}").GET().PUT().build()
                .setBase("studies").GET().POST().addPath("{studyDbId}").GET().PUT().build()
                .setBase("trials").GET().POST().addPath("{trialDbId}").GET().PUT().build()
                //GERMPLASM
                .setBase("germplasm").GET().addPath("{germplasmDbId}").GET().build()
                .setBase("search/germplasm").POST().addPath("{searchResultId}").GET().build()
                //PHENOTYPING
                .setBase("images").GET().POST().addPath("{imageDbId}").GET().PUT().addPath("imagecontent").PUT().build()
                .setBase("observationlevels").GET().build()
                .setBase("observationunits").GET().POST().PUT().addPath("{observationUnitDbId}").GET().PUT().build()
                .setBase("observationunits/table").GET().build()
                .setBase("variables").GET().POST().addPath("{observationVariableDbId}").GET().PUT().build()
                .setBase("observations").GET().POST().PUT().addPath("{observationDbId}").GET().PUT().build()
                .setBase("observations/table").GET().build()
                //V2.0 only
                .versions("2.0")
                .setBase("germplasm").addPath("{germplasmDbId}").addPath("pedigree").GET().setPath("progeny").GET().build()
                //V2.1 only
                .versions("2.1")
                .setBase("pedigree").GET().POST().PUT()
                .build();

        BrAPIServerInfo programServerInfo = new BrAPIServerInfo();
        setBrAPIServerInfo(programServerInfo);
        programServerInfo.setCalls(programServices);

        return new BrAPIServerInfoResponse().result(programServerInfo);
    }

    private void setBrAPIServerInfo(BrAPIServerInfo serverInfo) {
        serverInfo.setOrganizationName("Breeding Insight");
        serverInfo.setServerName("DeltaBreed");
        serverInfo.setContactEmail("bidevteam@cornell.edu");
        serverInfo.setOrganizationURL("https://breedinginsight.org");
        serverInfo.setServerDescription("DeltaBreed - breeding data management system");
        serverInfo.setLocation("Cornell University, Ithaca, NY, USA");
        serverInfo.setDocumentationURL("https://brapi.org/specification");
    }

    // Explicit match for /seasons GET endpoint, to allow Experimental Collaborator access.
    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/seasons{?queryParams}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN, ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.READ_ONLY, ProgramSecuredRole.EXPERIMENTAL_COLLABORATOR})
    public HttpResponse<?> getSeasons(@PathVariable("programId") UUID programId, HttpRequest<String> request, @PathVariable Optional<String> queryParams) {
        String path = "seasons";
        if (queryParams.isPresent()) {
            path = path + queryParams.get();
        }
        return executeRequest(path, programId, request, "GET");
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/{+path}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<?> getCatchall(@PathVariable("path") String path, @PathVariable("programId") UUID programId, HttpRequest<String> request) {
        return executeRequest(path, programId, request, "GET");
    }

    @Post("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/{+path}")
    @Consumes(MediaType.ALL)
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<String> postCatchall(@PathVariable("path") String path, @PathVariable("programId") UUID programId, HttpRequest<byte[]> request,
                                             @Header("Content-Type") String contentType) {
        return executeByteRequest(path, programId, request, contentType, "POST");
    }

    @Put("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/{+path}")
    @Consumes(MediaType.ALL)
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
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
        // TODO: use config parameter for timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
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
        var programBrAPIBaseUrl = getProgramBrAPIBaseUrl(programId);

        var requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/" + path).newBuilder();

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

    private String getProgramBrAPIBaseUrl(UUID programId) {
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
        return programBrAPIBaseUrl.endsWith(BrapiVersion.BRAPI_V2) ? programBrAPIBaseUrl : programBrAPIBaseUrl + BrapiVersion.BRAPI_V2;
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
