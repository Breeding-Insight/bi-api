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
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest;
import org.breedinginsight.brapps.importer.base.daos.ImportDAO;
import org.breedinginsight.brapps.importer.base.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Singleton
public class BrAPITrialDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;

    @Inject
    public BrAPITrialDAO(ProgramDAO programDAO, ImportDAO importDAO) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
    }

    public List<BrAPITrial> getTrialByName(List<String> trialNames, Program program) throws ApiException {
        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        trialSearch.trialNames(trialNames);
        TrialsApi api = new TrialsApi(programDAO.getCoreClient(program.getId()));
        return BrAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch
        );
    }

    public List<BrAPITrial> createBrAPITrial(List<BrAPITrial> brAPITrialList, UUID programId, ImportUpload upload) throws ApiException {
        TrialsApi api = new TrialsApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.post(brAPITrialList, upload, api::trialsPost, importDAO::update);
    }

}


