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
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class BrAPITrialDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private ProgramService programService;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    private final String referenceSource;

    @Inject
    public BrAPITrialDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, ProgramService programService, @Property(name = "brapi.server.reference-source") String referenceSource, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programService = programService;
        this.referenceSource = referenceSource;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPITrial> getTrialsByName(List<String> trialNames, Program program) throws ApiException {
        if(trialNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        trialSearch.trialNames(trialNames);
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), TrialsApi.class);
        return brAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch
        );
    }

    public List<BrAPITrial> createBrAPITrials(List<BrAPITrial> brAPITrialList, UUID programId, ImportUpload upload) throws ApiException {
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), TrialsApi.class);
        return brAPIDAOUtil.post(brAPITrialList, upload, api::trialsPost, importDAO::update);
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
        trialSearch.externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS)));
        trialSearch.externalReferenceIDs(List.of(programId.toString()));

        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));

        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), TrialsApi.class);
        return processExperimentsForDisplay(brAPIDAOUtil.search(
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

    public Optional<BrAPITrial> getTrialByDbId(String trialDbId, Program program) throws ApiException {
        List<BrAPITrial> trials = getTrialsByDbIds(List.of(trialDbId), program);

        return Utilities.getSingleOptional(trials);
    }

    public List<BrAPITrial> getTrialsByDbIds(Collection<String> trialDbIds, Program program) throws ApiException {
        if(trialDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        trialSearch.trialDbIds(new ArrayList<>(trialDbIds));
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), TrialsApi.class);
        return brAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch
        );
    }
}


