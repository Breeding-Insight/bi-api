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
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
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

    @Inject
    public BrAPIV2Controller(SecurityService securityService, ProgramService programService) {
        this.securityService = securityService;
        this.programService = programService;
    }


    @Get("/${micronaut.bi.api.version}/" + BrapiVersion.BRAPI_V2 + "/serverinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public BrAPIServerInfoResponse serverinfo() {
        BrAPIServerInfo serverInfo = new BrAPIServerInfo();
        setBrAPIServerInfo(serverInfo);
        serverInfo.setServerDescription("BrAPI endpoints are not implemented at the root of this domain.  Please make BrAPI calls in the context of a program (ex: https://app.breedinginsight.net/v1/programs/{programId}/brapi/v2)");

        serverInfo.setCalls(
                new ServiceBuilder().versions("2.0", "2.1")
                        .setBase("serverinfo").GET().build()
        );

        return new BrAPIServerInfoResponse().result(serverInfo);
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/serverinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public BrAPIServerInfoResponse programServerinfo(@PathVariable("programId") UUID programId) {
        String programBrAPIBase = String.format("v1/programs/%s%s/", programId, BrapiVersion.BRAPI_V2);

        List<BrAPIService> programServices = new ServiceBuilder()
                .versions("2.0", "2.1")
                //CORE
                .setBase("serverinfo").GET().build()
                .setBase("commoncropnames").GET().build()
                .setBase("lists").GET().POST().addPath("{listDbId}").GET().PUT().withSearch()
                .setBase("locations").GET().addPath("{locationDbId}").GET().withSearch()
                .setBase("people").GET().addPath("{personDbId}").GET().withSearch()
                .setBase("programs").GET().addPath("{programDbId}").GET().withSearch()
                .setBase("seasons").GET().addPath("{seasonDbId}").GET().build()
                .setBase("studies").GET().addPath("{studyDbId}").GET().withSearch()
                .setBase("studytypes").GET().build()
                .setBase("trials").GET().addPath("{trialDbId}").GET().withSearch()
                //GERMPLASM
                .setBase("attributes").GET().addPath("{attributeDbId}").GET().setPath("categories").GET().withSearch()
                .setBase("attributevalues").GET().addPath("{attributeValueDbId}").GET().withSearch()
                .setBase("breedingmethods").GET().addPath("{breedingMethodDbId}").GET().build()
                .setBase("crosses").GET().build()
                .setBase("plannedcrosses").GET().build()
                .setBase("crossingprojects").GET().addPath("{crossingProjectDbId}").GET().build()
                .setBase("seedlots").GET().addPath("transactions").GET().setPath("{seedLotDbId}").GET().addPath("transactions").GET().build()
                .setBase("germplasm").GET().addPath("{germplasmDbId}").GET().addPath("mcpd").GET().withSearch()
                //PHENOTYPING
                .setBase("events").GET().build()
                .setBase("images").GET().addPath("{imageDbId}").GET().addPath("imagecontent").withSearch()
                .setBase("ontologies").GET().build()
                .setBase("traits").GET().addPath("{traitDbId}").GET().build()
                .setBase("methods").GET().addPath("{methodDbId}").GET().build()
                .setBase("scales").GET().addPath("{scaleDbId}").GET().build()
                .setBase("variables").GET().addPath("{observationVariableDbId}").GET().withSearch()
                .setBase("observationunits").GET().addPath("{observationUnitDbId}").GET().setPath("table").GET().withSearch()
                .setBase("observations").GET().addPath("{observationDbId}").GET().setPath("table").GET().withSearch()
                .setBase("observationlevels").GET().build()
                //GENOTYPING - TODO
//                .setBase("calls").GET().withSearch()
//                .setBase("callsets").GET().addPath("{callSetDbId}").GET().addPath("calls").GET().withSearch()
//                .setBase("maps").GET().addPath("{mapDbId}").GET().addPath("linkagegroups").GET().build()
//                .setBase("markerpositions").GET().withSearch()
//                .setBase("references").GET().addPath("{referenceDbId}").GET().addPath("bases").GET().withSearch()
//                .setBase("referencesets").GET().addPath("{referenceSetDbId}").GET().withSearch()
//                .setBase("samples").GET().addPath("{sampleDbId}").GET().withSearch()
//                .setBase("variants").GET().addPath("{variantDbId}").GET().addPath("calls").GET().withSearch()
//                .setBase("variantsets").GET().addPath("extract").setPath("{variantSetDbId}").GET()
//                .addPath("calls").GET().setPath("callsets").GET().setPath("variants").GET().withSearch()
//                .setBase("vendor").addPath("specifications").GET().setPath("plates").addPath("{submissionId}").build()
//                .setBase("vendor/orders").GET().addPath("{orderId}").addPath("plates").GET().setPath("results").GET().setPath("status").GET().build()

                //V2.0 only
                .versions("2.0")
                .setBase("germplasm").addPath("{germplasmDbId}").addPath("pedigree").GET().setPath("progeny").GET().build()
                .setBase("lists").addPath("{listDbId}").addPath("items").build()
//                .setBase("samples").addPath("{sampleDbId}").build() //TODO
                //V2.1 only
                .versions("2.1")
//                .setBase("allelematrix").GET().withSearch() //TODO
//                .setBase("calls").build() //TODO
                .setBase("delete").addPath("images").setPath("observations").build()
                .setBase("lists").addPath("{listDbId}").addPath("data").POST().build()
                .setBase("ontologies").addPath("{ontologyDbId}").GET().build()
                .setBase("pedigree").GET().withSearch()
//                .setBase("plates").GET().addPath("{plateDbId}").GET().withSearch() //TODO
//                .setBase("samples").build() //TODO
                .build();

                for(BrAPIService service : programServices) {
                    service.setService(programBrAPIBase + service.getService());
                }

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
