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
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Singleton
public class BrAPIStudyDAO {

    private ProgramDAO programDAO;

    @Inject
    public BrAPIStudyDAO(ProgramDAO programDAO) {
        this.programDAO = programDAO;
    }

    public List<BrAPIStudy> getStudyByName(List<String> studyNames, UUID programId) throws ApiException {
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        // could also add programId but
        studySearch.studyNames(studyNames);
        studySearch.setPageSize(BrAPIDAOUtil.RESULTS_PER_QUERY);
        StudiesApi api = new StudiesApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        );
    }

    public List<BrAPIStudy> createBrAPIStudy(List<BrAPIStudy> brAPIStudyList, UUID programId) throws ApiException {
        StudiesApi api = new StudiesApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.post(brAPIStudyList, api::studiesPost);
    }
}