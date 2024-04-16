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

import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.PedigreeApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.germ.BrAPIPedigreeNode;
import org.brapi.v2.model.germ.request.BrAPIPedigreeSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIPedigreeListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;

@Singleton
public class BrAPIPedigreeDAO {

    private ProgramDAO programDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIPedigreeDAO(ProgramDAO programDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPIPedigreeNode> getPedigree(Program program) throws ApiException {
        BrAPIPedigreeSearchRequest pedigreeSearchRequest = new BrAPIPedigreeSearchRequest();
        pedigreeSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        // TODO: add pagination support
        // .page(page)
        // .pageSize(pageSize);
        // TODO: other parameters

        PedigreeApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), PedigreeApi.class);

        /*
        return brAPIDAOUtil.<BrAPIPedigreeListResponse, BrAPIPedigreeSearchRequest, BrAPIPedigreeNode>search(
                api::searchPedigreePost,
                api::searchPedigreeSearchResultsDbIdGet,
                pedigreeSearchRequest
        );
         */
        return new ArrayList<>();
    }

}