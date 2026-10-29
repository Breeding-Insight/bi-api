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

import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.brapi.v2.model.pheno.request.BrAPIObservationVariableSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableSingleResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Singleton
public class BrAPIObservationVariableDAO {

    private ProgramDAO programDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIObservationVariableDAO(ProgramDAO programDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPIObservationVariable> getVariableByName(List<String> variableNames, UUID programId) throws ApiException {
        if(variableNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIObservationVariableSearchRequest variableSearch = new BrAPIObservationVariableSearchRequest();
        variableSearch.observationVariableNames(variableNames);
        ObservationVariablesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationVariablesApi.class);
        return brAPIDAOUtil.search(
                api::searchVariablesPost,
                api::searchVariablesSearchResultsDbIdGet,
                variableSearch
        );
    }

    public BrAPIObservationVariableSingleResponse getVariableByIdWithResponse(String observationVariableDbId, UUID programId) throws ApiException {
        ObservationVariablesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationVariablesApi.class);

        ApiResponse<BrAPIObservationVariableSingleResponse> apiResponse = api.variablesObservationVariableDbIdGet(observationVariableDbId);

        if (apiResponse.getBody() != null && apiResponse.getStatusCode() == 200) {
            return apiResponse.getBody();
        }

        throw new InternalServerException(String.format("Unable to retrieve BrAPI Observation Variable using id [%s] for BI Program with id: [%s]", observationVariableDbId, programId));
    }

}
