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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class BrAPIObservationUnitDAO {

    public static String OU_ID_REFERENCE_SOURCE = "ou_id";

    private ProgramDAO programDAO;

    @Inject
    public BrAPIObservationUnitDAO(ProgramDAO programDAO) {
        this.programDAO = programDAO;
    }

    /*
    public List<BrAPIObservationUnit> getObservationUnitsByNameAndStudyName(List<Pair<String, String>> nameStudyPairs, BrAPIProgram brAPIProgram) throws ApiException {

        List<String> observationUnitNames = nameStudyPairs.stream().map(Pair::getLeft).collect(Collectors.toList());
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.setObservationUnitNames(new ArrayList<>(observationUnitNames));
        observationUnitSearchRequest.setProgramDbIds(List.of(brAPIProgram.getProgramDbId()));
        ObservationUnitsApi api = brAPIProvider.getObservationUnitApi(BrAPIClientType.CORE);
        List<BrAPIObservationUnit> observationUnits = BrAPIDAOUtil.search(
                api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest
        );

        // TODO: Select for study as well
        return observationUnits;
    }
     */

    public List<BrAPIObservationUnit> getObservationUnitByName(List<String> observationUnitNames, UUID programId) throws ApiException {
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        // could also add programId but
        observationUnitSearchRequest.observationUnitNames(observationUnitNames);
        observationUnitSearchRequest.setPageSize(BrAPIDAOUtil.RESULTS_PER_QUERY);
        ObservationUnitsApi api = new ObservationUnitsApi(programDAO.getPhenoClient(programId));
        return BrAPIDAOUtil.search(
                api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest
        );
    }

    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList, UUID programId) throws ApiException {
        ObservationUnitsApi api = new ObservationUnitsApi(programDAO.getPhenoClient(programId));
        return BrAPIDAOUtil.post(brAPIObservationUnitList, api::observationunitsPost);
    }
}
