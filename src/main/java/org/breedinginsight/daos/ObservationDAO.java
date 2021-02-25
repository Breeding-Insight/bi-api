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
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;

import javax.inject.Inject;
import java.util.List;

public class ObservationDAO {
    private BrAPIProvider brAPIProvider;

    @Inject
    public ObservationDAO(BrAPIProvider brAPIProvider) {
        this.brAPIProvider = brAPIProvider;
    }

    public List<BrAPIObservation> getObservationsByVariableDbId(String observationVariableDbId) {

        ApiResponse<BrAPIObservationListResponse> brapiObservations;
        ObservationQueryParams observationsRequest = new ObservationQueryParams()
                .observationVariableDbId(observationVariableDbId);
        try {
            brapiObservations = brAPIProvider.getObservationsAPI(BrAPIClientType.PHENO).observationsGet(observationsRequest);
        } catch (ApiException e) {
            throw new InternalServerException("Error making BrAPI call", e);
        }

        return brapiObservations.getBody().getResult().getData();
    }

}
