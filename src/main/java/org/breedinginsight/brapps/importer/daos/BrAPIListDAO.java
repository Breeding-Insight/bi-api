package org.breedinginsight.brapps.importer.daos;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ListQueryParams;
import org.brapi.client.v2.modules.core.ListsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.request.BrAPIListSearchRequest;
import org.brapi.v2.model.core.response.*;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class BrAPIListDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIListDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider) {
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

    public List<BrAPIListSummary> getListByTypeAndExternalRef(@NotNull BrAPIListTypes listType, UUID programId, String externalReferenceSource, UUID externalReferenceId) throws ApiException {
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
    public List<String> updateBrAPIList(String brAPIListDbId, List<String> data, UUID programId) throws ApiException {
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);

        // Do manually, it doesn't like List<Object> to List<BrAPIListNewRequest> for some reason
        ApiResponse<BrAPIListResponse> response;
        try {
            response = api.listsListDbIdItemsPost(brAPIListDbId, data);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        }
        if(response != null) {
            BrAPIListResponse body = response.getBody();
            if (body == null) {
                throw new ApiException("Response is missing body");
            }
            BrAPIListDetails result = body.getResult();
            if (result == null) {
                throw new ApiException("Response body is missing result");
            }
            if (result.getData() == null) {
                throw new ApiException("Response result is missing data");
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
                throw new ApiException("Response is missing body");
            }
            BrAPIResponseResult<BrAPIListSummary> result = body.getResult();
            if (result == null) {
                throw new ApiException("Response body is missing result");
            }
            if (result.getData() == null) {
                throw new ApiException("Response result is missing data");
            }
            return result.getData();
        }

        throw new ApiException("No response after creating list");
    }
}
