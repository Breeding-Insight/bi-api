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

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.CrossesApi;
import org.brapi.v2.model.germ.BrAPICross;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class BrAPICrossDAO {

    private BrAPIProvider brAPIProvider;
    private final BrAPIDAOUtil brAPIDAOUtil;

    @Inject
    public BrAPICrossDAO(BrAPIProvider brAPIProvider, BrAPIDAOUtil brAPIDAOUtil) {
        this.brAPIProvider = brAPIProvider;
        this.brAPIDAOUtil = brAPIDAOUtil;
    }

    public List<BrAPICross> createBrAPICrosses(List<BrAPICross> brAPICrossList) throws ApiException {
        CrossesApi api = brAPIProvider.getCrossesApi(BrAPIClientType.CORE);
        return brAPIDAOUtil.post(brAPICrossList, api::crossesPost);
    }
}
