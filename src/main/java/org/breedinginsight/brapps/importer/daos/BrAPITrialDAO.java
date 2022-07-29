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

import io.micronaut.context.annotation.Property;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class BrAPITrialDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private ProgramService programService;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Inject
    public BrAPITrialDAO(ProgramDAO programDAO, ImportDAO importDAO, ProgramService programService) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.programService = programService;
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

    /**
     * Fetch formatted trials/experiments for this program
     * @param programId
     * @return List<formatted BrAPITrial>
     * @throws ApiException
     */
    public List<BrAPITrial> getTrials(UUID programId) throws ApiException, DoesNotExistException {
        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        //TODO check external references filter works once implemented in BI-1552
        trialSearch.externalReferenceSources(List.of(String.format("%s/programs", referenceSource)));
        trialSearch.externalReferenceIDs(List.of(programId.toString()));

        Optional<Program> optionalProgram = programService.getById(programId);
        if (!optionalProgram.isPresent())
        {
            throw new DoesNotExistException("Program id does not exist");
        }
        Program program = optionalProgram.get();
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));

        TrialsApi api = new TrialsApi(programDAO.getCoreClient(programId));
        return processExperimentsForDisplay(BrAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch), program.getKey(), programId, true);
    }

    //Removes program key from trial name and adds dataset information
    private List<BrAPITrial> processExperimentsForDisplay(List<BrAPITrial> trials, String programKey, UUID programId, boolean metadata) throws ApiException {
        List<BrAPITrial> displayExperiments = new ArrayList<>();
        for (BrAPITrial trial: trials) {
            trial.setTrialName(Utilities.removeProgramKey(trial.getTrialName(), programKey, ""));
            List<String> datasets = new ArrayList<>();

            if (metadata) {
                //todo presumably BI-1193 replace dummy value with list of datasets once datasets implemented
                datasets.add("Observation Dataset");
                trial.putAdditionalInfoItem("datasets", datasets);
            }

            displayExperiments.add(trial);
        }
        return displayExperiments;
    }

}


