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
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.*;

@Singleton
public class BrAPIObservationUnitDAO {
    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramService programService;

    private final String referenceSource;

    @Inject
    public BrAPIObservationUnitDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider, ProgramService programService, @Property(name = "brapi.server.reference-source") String referenceSource) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
        this.programService = programService;
    }

    public List<BrAPIObservationUnit> getObservationUnitByName(List<String> observationUnitNames, Program program) throws ApiException {
        if(observationUnitNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationUnitSearchRequest.observationUnitNames(observationUnitNames);
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);
        return brAPIDAOUtil.search(
                api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest
        );
    }

    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList, UUID programId, ImportUpload upload) throws ApiException {
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        return brAPIDAOUtil.post(brAPIObservationUnitList, upload, api::observationunitsPost, importDAO::update);
    }

    public List<BrAPIObservationUnit> getObservationUnitsById(Collection<String> observationUnitExternalIds, Program program) throws ApiException {
        if(observationUnitExternalIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                                                                 .getProgramDbId()));
        observationUnitSearchRequest.externalReferenceIDs(new ArrayList<>(observationUnitExternalIds));
        observationUnitSearchRequest.externalReferenceSources(List.of(String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS.getName())));

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);
        return brAPIDAOUtil.search(api::searchObservationunitsPost,
                                   api::searchObservationunitsSearchResultsDbIdGet,
                                   observationUnitSearchRequest);
    }

    public List<BrAPIObservationUnit> getObservationUnitsForStudyDbId(@NotNull String studyDbId, Program program) throws ApiException {
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                .getProgramDbId()));
        observationUnitSearchRequest.studyDbIds(List.of(studyDbId));

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);
        return brAPIDAOUtil.search(api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest);
    }
    public List<BrAPIObservationUnit> getObservationUnitsForTrialDbId(@NotNull UUID programId, @NotNull String trialDbId) throws ApiException, DoesNotExistException {
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                .getProgramDbId()));
        observationUnitSearchRequest.trialDbIds(List.of(trialDbId));

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);
        return brAPIDAOUtil.search(api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest);
    }
}
