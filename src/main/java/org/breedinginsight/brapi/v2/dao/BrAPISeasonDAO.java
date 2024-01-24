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

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.SeasonQueryParams;
import org.brapi.client.v2.modules.core.SeasonsApi;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.response.BrAPISeasonListResponse;
import org.brapi.v2.model.core.response.BrAPISeasonListResponseResult;
import org.brapi.v2.model.core.response.BrAPISeasonSingleResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
public class BrAPISeasonDAO {

    private ProgramDAO programDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPISeasonDAO(ProgramDAO programDAO, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPISeason> getSeasonsByYear(String year, UUID programId) throws ApiException {
        SeasonsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), SeasonsApi.class);
        SeasonQueryParams queryParams =
                SeasonQueryParams.builder()
                        .year( year )
                        .pageSize( 10000 )
                        .build();
        ApiResponse<BrAPISeasonListResponse> apiResponse = api.seasonsGet( queryParams );
        BrAPISeasonListResponse seasonListResponse = apiResponse.getBody();
        BrAPISeasonListResponseResult result = seasonListResponse.getResult();
        return result.getData();
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
