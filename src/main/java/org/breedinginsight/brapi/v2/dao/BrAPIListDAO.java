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

package org.breedinginsight.brapi.v2.dao;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.ListsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.request.BrAPIListSearchRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsListResponse;
import org.brapi.v2.model.core.response.BrAPIListsListResponseResult;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BrAPIListDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIListDAO(ProgramDAO programDAO,
                        ImportDAO importDAO,
                        BrAPIDAOUtil brAPIDAOUtil,
                        BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPIListSummary> getListByName(List<String> listNames, UUID programId) throws ApiException {
        if(listNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIListSearchRequest listSearch = new BrAPIListSearchRequest();
        listSearch.listNames(listNames);
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        return brAPIDAOUtil.search(
                api::searchListsPost,
                api::searchListsSearchResultsDbIdGet,
                listSearch
        );
    }

    public BrAPIListsSingleResponse getListById(String listId, UUID programId) throws ApiException {
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        ApiResponse<BrAPIListsSingleResponse> response = api.listsListDbIdGet(listId);
        return response.getBody();
    }

    public List<BrAPIListSummary> getListsBySearch(@NotNull BrAPIListSearchRequest searchRequest, UUID programId) throws ApiException {
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        List<BrAPIListSummary> programLists = brAPIDAOUtil.search(api::searchListsPost, api::searchListsSearchResultsDbIdGet, searchRequest);
        if (searchRequest.getExternalReferenceSources() != null && searchRequest.getExternalReferenceIDs() != null) {
            programLists = processListsForProgram(programLists,
                    UUID.fromString(searchRequest.getExternalReferenceIDs().get(0)),
                    searchRequest.getExternalReferenceSources().get(0));
        }
        return programLists;

    }

    public List<BrAPIListSummary> getListsByTypeAndExternalRef(@NotNull BrAPIListTypes listType, UUID programId, String externalReferenceSource, UUID externalReferenceId) throws ApiException {
        BrAPIListSearchRequest searchRequest = new BrAPIListSearchRequest()
                .externalReferenceIDs(List.of(externalReferenceId.toString()))
                .externalReferenceSources(List.of(externalReferenceSource))
                .listType(listType);

        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        return processListsForProgram(brAPIDAOUtil.search(
                api::searchListsPost,
                api::searchListsSearchResultsDbIdGet,
                searchRequest
        ), externalReferenceId, externalReferenceSource);

    }

    private List<BrAPIListSummary> processListsForProgram(List<BrAPIListSummary> programLists, UUID externalReferenceId, String externalReferenceSource) {
        // check that all lists were created via the BI UI in case the BrAPI service silently ignores the search by
        // externalReference source and ID
        List<BrAPIListSummary> filteredLists = new ArrayList<>();
        for (BrAPIListSummary list: programLists) {
            if (list.getExternalReferences() == null || list.getExternalReferences().size() == 0) {
                continue;
            } else {
                for (BrAPIExternalReference ref: list.getExternalReferences()) {
                    if (ref.getReferenceID().equals(externalReferenceId.toString()) &&
                            ref.getReferenceSource().equals(externalReferenceSource)) {
                        filteredLists.add(list);
                    }
                }
            }
        }
        return filteredLists;
    }
    public List<String> updateBrAPIList(String brAPIListDbId, BrAPIListDetails mutatedList, UUID programId) throws ApiException {
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        BrAPIListNewRequest request = new BrAPIListNewRequest();
        request.setAdditionalInfo(mutatedList.getAdditionalInfo());
        request.setDateCreated(mutatedList.getDateCreated());
        request.setDateModified(mutatedList.getDateModified());
        request.setExternalReferences(mutatedList.getExternalReferences());
        request.setListDescription(mutatedList.getListDescription());
        request.setListName(mutatedList.getListName());
        request.setListOwnerName(mutatedList.getListOwnerName());
        request.setListOwnerPersonDbId(mutatedList.getListOwnerPersonDbId());
        request.setListSize(mutatedList.getListSize());
        request.setListSource(mutatedList.getListSource());
        request.setListType(mutatedList.getListType());
        request.setData(mutatedList.getData());

        // Do manually, it doesn't like List<Object> to List<BrAPIListNewRequest> for some reason
        ApiResponse<BrAPIListsSingleResponse> response;
        try {
            response = api.listsListDbIdPut(brAPIListDbId, request);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        }
        if(response != null) {
            BrAPIListsSingleResponse body = response.getBody();
            if (body == null) {
                throw new ApiException("Response is missing body", 0, response.getHeaders(), null);
            }
            BrAPIListDetails result = body.getResult();
            if (result == null) {
                throw new ApiException("Response body is missing result", 0, response.getHeaders(), response.getBody().toString());
            }
            if (result.getData() == null) {
                throw new ApiException("Response result is missing data", 0, response.getHeaders(), response.getBody().toString());
            }
            return result.getData();
        }

        throw new ApiException("No response after creating list");
    }

    public List<BrAPIListSummary> createBrAPILists(List<BrAPIListNewRequest> brapiLists, UUID programId, ImportUpload upload) throws ApiException {
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        // Do manually, it doesn't like List<Object> to List<BrAPIListNewRequest> for some reason
        ApiResponse<BrAPIListsListResponse> response;
        try {
            response = api.listsPost(brapiLists);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        }
        if(response != null) {
            BrAPIResponse<BrAPIListsListResponseResult> body = response.getBody();
            if (body == null) {
                throw new ApiException("Response is missing body", 0, response.getHeaders(), null);
            }
            BrAPIResponseResult<BrAPIListSummary> result = body.getResult();
            if (result == null) {
                throw new ApiException("Response body is missing result", 0, response.getHeaders(), response.getBody().toString());
            }
            if (result.getData() == null) {
                throw new ApiException("Response result is missing data", 0, response.getHeaders(), response.getBody().toString());
            }
            return result.getData();
        }

        throw new ApiException("No response after creating list");
    }

    public void deleteBrAPIList(String listDbId, UUID programId, boolean hardDelete) throws ApiException {
        // TODO: Switch to using the ListsApi from the BrAPI client library once the delete endpoints are merged into it
        var programBrAPIBaseUrl = brAPIDAOUtil.getProgramBrAPIBaseUrl(programId);
        var requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/lists/" + listDbId).newBuilder();
        requestUrl.addQueryParameter("hardDelete", Boolean.toString(hardDelete));
        HttpUrl url = requestUrl.build();
        var brapiRequest = new Request.Builder().url(url)
                .method("DELETE", null)
                .addHeader("Content-Type", "application/json")
                .build();

        brAPIDAOUtil.makeCall(brapiRequest);
    }

}
