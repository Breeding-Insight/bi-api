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

import io.micronaut.context.annotation.Property;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class BrAPIProgramDAO {

    private ProgramDAO programDAO;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIProgramDAO(ProgramDAO programDAO, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public Optional<BrAPIProgram> getProgram(UUID programId) throws ApiException {
        ProgramsApi programsApi = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ProgramsApi.class);
        ProgramQueryParams params = new ProgramQueryParams();
        params.externalReferenceID(programId.toString());
        params.externalReferenceSource(referenceSource);
        ApiResponse<BrAPIProgramListResponse> programsResponse = programsApi.programsGet(params);
        if (programsResponse.getBody() == null ||
                programsResponse.getBody().getResult() == null ||
                programsResponse.getBody().getResult().getData() == null
        ) {
            throw new ApiException("Query response was not properly structure.");
        }

        List<BrAPIProgram> programs = programsResponse.getBody().getResult().getData();
        return Utilities.getSingleOptional(programs);
    }
}
