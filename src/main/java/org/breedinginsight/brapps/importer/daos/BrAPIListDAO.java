package org.breedinginsight.brapps.importer.daos;

import lombok.extern.slf4j.Slf4j;
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
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.base.daos.ImportDAO;
import org.breedinginsight.brapps.importer.base.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class BrAPIListDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;

    @Inject
    public BrAPIListDAO(ProgramDAO programDAO, ImportDAO importDAO) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
    }

    public List<BrAPIListSummary> getListByName(List<String> listNames, UUID programId) throws ApiException {
        BrAPIListSearchRequest listSearch = new BrAPIListSearchRequest();
        listSearch.listNames(listNames);
        ListsApi api = new ListsApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.search(
                api::searchListsPost,
                api::searchListsSearchResultsDbIdGet,
                listSearch
        );
    }

    public BrAPIListsSingleResponse getListById(String listId, UUID programId) throws ApiException {
        ListsApi api = new ListsApi(programDAO.getCoreClient(programId));
        ApiResponse<BrAPIListsSingleResponse> response = api.listsListDbIdGet(listId);
        return response.getBody();
    }

    public List<BrAPIListSummary> getListByTypeAndExternalRef(BrAPIListTypes listType, UUID programId, String externalReferenceSource, UUID externalReferenceId) throws ApiException {
        BrAPIListSearchRequest searchRequest = new BrAPIListSearchRequest()
                .externalReferenceIDs(List.of(externalReferenceId.toString()))
                .externalReferenceSources(List.of(externalReferenceSource))
                .listType(listType);

        ListsApi api = new ListsApi(programDAO.getCoreClient(programId));
        return processListsForProgram(BrAPIDAOUtil.search(
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

    public List<BrAPIObservation> createBrAPILists(List<BrAPIListNewRequest> brapiLists, UUID programId, ImportUpload upload) throws ApiException {
        ListsApi api = new ListsApi(programDAO.getCoreClient(programId));
        // Do manually, it doesn't like List<Object> to List<BrAPIListNewRequest> for some reason
        ApiResponse response =  api.listsPost(brapiLists);
        if (response.getBody() == null) throw new ApiException("Response is missing body");
        BrAPIResponse body = (BrAPIResponse) response.getBody();
        if (body.getResult() == null) throw new ApiException("Response body is missing result");
        BrAPIResponseResult result = (BrAPIResponseResult) body.getResult();
        if (result.getData() == null) throw new ApiException("Response result is missing data");
        return result.getData();
    }
}
