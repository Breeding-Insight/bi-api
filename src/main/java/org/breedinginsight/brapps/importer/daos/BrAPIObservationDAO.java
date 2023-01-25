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
package org.breedinginsight.brapps.importer.daos;

import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.request.BrAPIObservationSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;

@Singleton
public class BrAPIObservationDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIObservationDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPIObservation> getObservationsByStudyName(List<String> studyNames, Program program) throws ApiException {

        BrAPIObservationSearchRequest observationSearchRequest = new BrAPIObservationSearchRequest();
        observationSearchRequest.setProgramDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationSearchRequest.setStudyNames(new ArrayList<>(studyNames));
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationsApi.class);
        return brAPIDAOUtil.search(
                api::searchObservationsPost,
                (brAPIWSMIMEDataTypes, searchResultsDbId, page, pageSize) -> searchObservationsSearchResultsDbIdGet(program.getId(), searchResultsDbId, page, pageSize),
                observationSearchRequest
        );
    }

    @NotNull
    private ApiResponse<Pair<Optional<BrAPIObservationListResponse>, Optional<BrAPIAcceptedSearchResponse>>>
    searchObservationsSearchResultsDbIdGet(UUID programId, String searchResultsDbId, Integer page, Integer pageSize) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        return api.searchObservationsSearchResultsDbIdGet(APPLICATION_JSON, searchResultsDbId, page, pageSize);
    }

    public List<BrAPIObservation> createBrAPIObservations(List<BrAPIObservation> brAPIObservationList, UUID programId, ImportUpload upload) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        return brAPIDAOUtil.post(brAPIObservationList, upload, api::observationsPost, importDAO::update);
    }

}
