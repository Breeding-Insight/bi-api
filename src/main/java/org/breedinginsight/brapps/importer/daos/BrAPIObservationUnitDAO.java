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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.micronaut.context.annotation.Property;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationTreatment;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
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
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class BrAPIObservationUnitDAO {
    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramService programService;

    private final String referenceSource;

    private Gson gson = new JSON().getGson();
    private Type treatmentlistType = new TypeToken<ArrayList<BrAPIObservationTreatment>>(){}.getType();

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

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program.getId());
    }

    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList, UUID programId, ImportUpload upload) throws ApiException {
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        List<BrAPIObservationUnit> ous = brAPIDAOUtil.post(brAPIObservationUnitList, upload, api::observationunitsPost, importDAO::update);
        processObservationUnits(ous);
        return ous;
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

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program.getId());
    }

    public List<BrAPIObservationUnit> getObservationUnitsForStudyDbId(@NotNull String studyDbId, Program program) throws ApiException {
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                .getProgramDbId()));
        observationUnitSearchRequest.studyDbIds(List.of(studyDbId));

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program.getId());
    }
    public List<BrAPIObservationUnit> getObservationUnitsForTrialDbId(@NotNull UUID programId, @NotNull String trialDbId) throws ApiException, DoesNotExistException {
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                .getProgramDbId()));
        observationUnitSearchRequest.trialDbIds(List.of(trialDbId));

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, programId);
    }


    /**
     * Perform observation unit search and process returned observation units to handle any modifications to the data
     * to be returned by bi-api
     */
    private List<BrAPIObservationUnit> searchObservationUnitsAndProcess(BrAPIObservationUnitSearchRequest request, UUID programId) throws ApiException {

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        List<BrAPIObservationUnit> brapiObservationUnits = brAPIDAOUtil.search(api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                request);

        processObservationUnits(brapiObservationUnits);
        return brapiObservationUnits;
    }

    private void processObservationUnits(List<BrAPIObservationUnit> brapiObservationUnits) {

        // if has treatments in additionalInfo but not treatments property, copy to treatments property
        for (BrAPIObservationUnit ou : brapiObservationUnits) {
            JsonObject additionalInfo = ou.getAdditionalInfo();
            if (additionalInfo != null) {
                JsonElement treatmentsElement = additionalInfo.get(BrAPIAdditionalInfoFields.TREATMENTS);
                if (treatmentsElement != null) {
                    List<BrAPIObservationTreatment> treatments = gson.fromJson(treatmentsElement, treatmentlistType);
                    ou.setTreatments(treatments);
                }
            }
        }
    }
}
