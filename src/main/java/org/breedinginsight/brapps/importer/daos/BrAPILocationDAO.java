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

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.request.BrAPILocationSearchRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Singleton
public class BrAPILocationDAO {

    private ProgramDAO programDAO;

    @Inject
    public BrAPILocationDAO(ProgramDAO programDAO) {
        this.programDAO = programDAO;
    }

    public List<BrAPILocation> getLocationsByName(List<String> locationNames, UUID programId) throws ApiException {

        BrAPILocationSearchRequest locationSearchRequest = new BrAPILocationSearchRequest();
        locationSearchRequest.setLocationNames(new ArrayList<>(locationNames));
        //TODO: Locations don't connect to programs. How to get locations for the program?
        LocationsApi api = new LocationsApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.search(
                api::searchLocationsPost,
                api::searchLocationsSearchResultsDbIdGet,
                locationSearchRequest
        );
    }


}
