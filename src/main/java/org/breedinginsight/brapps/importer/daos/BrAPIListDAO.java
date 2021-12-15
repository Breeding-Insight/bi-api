package org.breedinginsight.brapps.importer.daos;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.ListsApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.request.BrAPIListSearchRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

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
}
