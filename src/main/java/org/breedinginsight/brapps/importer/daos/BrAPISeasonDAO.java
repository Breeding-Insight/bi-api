package org.breedinginsight.brapps.importer.daos;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.SeasonQueryParams;
import org.brapi.client.v2.modules.core.ListsApi;
import org.brapi.client.v2.modules.core.SeasonsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.request.BrAPIListSearchRequest;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.core.response.BrAPISeasonListResponse;
import org.brapi.v2.model.core.response.BrAPISeasonListResponseResult;
import org.brapi.v2.model.core.response.BrAPISeasonSingleResponse;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
public class BrAPISeasonDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPISeasonDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPISeason> getSeasonByYear(String year, UUID programId) throws ApiException {
        SeasonsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), SeasonsApi.class);
        SeasonQueryParams queryParams =
                SeasonQueryParams.builder()
                        .year( year )
                        .pageSize( 10000 )
                        .build();
        List<BrAPISeason> seasons = new ArrayList<>();
        ApiResponse<BrAPISeasonListResponse> apiResponse = api.seasonsGet( queryParams );
        BrAPISeasonListResponse seasonListResponse = apiResponse.getBody();
        BrAPISeasonListResponseResult result = seasonListResponse.getResult();
        seasons = result.getData();

        return seasons;
    }

    public BrAPISeason getSeasonById(String id, UUID programId) throws ApiException {
        SeasonsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), SeasonsApi.class);
        ApiResponse<BrAPISeasonSingleResponse> apiResponse = api.seasonsSeasonDbIdGet(id);
        BrAPISeasonSingleResponse seasonListResponse = apiResponse.getBody();
        BrAPISeason season = seasonListResponse.getResult();
        return season;
    }

    public BrAPISeason addOneSeason(BrAPISeason season, UUID programId) throws ApiException {
        BrAPISeason resultSeason = null;
        SeasonsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), SeasonsApi.class);

        ApiResponse<BrAPISeasonListResponse> apiResponse = api.seasonsPost(Arrays.asList(season));
        BrAPISeasonListResponse seasonListResponse = apiResponse.getBody();
        BrAPISeasonListResponseResult result = seasonListResponse.getResult();
        List<BrAPISeason> seasons = result.getData();
        if (seasons.size() > 0) {
            resultSeason = seasons.get(0);
        }
        return resultSeason;
    }
    
}
