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
package org.breedinginsight.daos;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.request.BrAPIObservationSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;


@Singleton
@Slf4j
public class ObservationDAO {
    private final BrAPIDAOUtil brAPIDAOUtil;
    private ProgramDAO programDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public ObservationDAO(BrAPIDAOUtil brAPIDAOUtil, ProgramDAO programDAO, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programDAO = programDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPIObservation> getObservationsByVariableDbId(String observationVariableDbId, UUID programId) {

        ApiResponse<BrAPIObservationListResponse> brapiObservations;
        ObservationQueryParams observationsRequest = new ObservationQueryParams()
                .observationVariableDbId(observationVariableDbId);
        try {
            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationsApi api = brAPIEndpointProvider.get(client, ObservationsApi.class);
            brapiObservations = api.observationsGet(observationsRequest);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        return brapiObservations.getBody().getResult().getData();
    }

    // search by ObservationVariableDbIds
    public List<BrAPIObservation> getObservationsByVariableDbIds(List<String> observationVariableDbIds, UUID programId) {
        if(observationVariableDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            BrAPIObservationSearchRequest request = new BrAPIObservationSearchRequest()
                    .observationVariableDbIds(observationVariableDbIds);

            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationsApi api = brAPIEndpointProvider.get(client, ObservationsApi.class);
            return brAPIDAOUtil.search(
                    api::searchObservationsPost,
                    api::searchObservationsSearchResultsDbIdGet,
                    request
            );
        } catch (ApiException e) {
            throw new InternalServerException("Observations brapi search error", e);
        }

    }

    public List<BrAPIObservation> getObservationsByVariableAndBrAPIProgram(String brapiProgramId, UUID programId, List<String> observationVariableDbIds) {
        if(observationVariableDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            BrAPIObservationSearchRequest request = new BrAPIObservationSearchRequest()
                    .observationVariableDbIds(observationVariableDbIds)
                    .programDbIds(List.of(brapiProgramId));

            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationsApi api = brAPIEndpointProvider.get(client, ObservationsApi.class);
            return brAPIDAOUtil.search(
                    api::searchObservationsPost,
                    api::searchObservationsSearchResultsDbIdGet,
                    request
            );
        } catch (ApiException e) {
            throw new InternalServerException("Observations brapi search error", e);
        }

    }

}
