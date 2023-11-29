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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.genotype.PlatesApi;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.request.BrAPIPlateNewRequest;
import org.brapi.v2.model.geno.request.BrAPIPlateSearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIPlateDAO {

    private final String referenceSource;

    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIPlateDAO(ProgramDAO programDAO,
                         ImportDAO importDAO,
                         BrAPIDAOUtil brAPIDAOUtil,
                         BrAPIEndpointProvider brAPIEndpointProvider,
                         @Property(name = "brapi.server.reference-source") String referenceSource) {
        this.referenceSource = referenceSource;
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPIPlate> createPlates(Program program, List<BrAPIPlate> platesToSave, ImportUpload upload) throws ApiException {
        PlatesApi platesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), PlatesApi.class);
        List<BrAPIPlateNewRequest> newPlatesRequests = platesToSave.stream()
                                                                   .map(plate -> new BrAPIPlateNewRequest().additionalInfo(plate.getAdditionalInfo())
                                                                                                           .externalReferences(plate.getExternalReferences())
                                                                                                           .plateBarcode(plate.getPlateBarcode())
                                                                                                           .plateFormat(plate.getPlateFormat())
                                                                                                           .plateName(plate.getPlateName())
                                                                                                           .programDbId(plate.getProgramDbId())
                                                                                                           .sampleType(plate.getSampleType())
                                                                                                           .studyDbId(plate.getStudyDbId())
                                                                                                           .trialDbId(plate.getTrialDbId())
                                                                   )
                                                                   .collect(Collectors.toList());
        return brAPIDAOUtil.post(newPlatesRequests, upload, platesApi::platesPost, importDAO::update);
    }

    public List<BrAPIPlate> readPlatesByIds(Program program, List<String> plateExternalIds) throws ApiException {
        PlatesApi platesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), PlatesApi.class);

        BrAPIPlateSearchRequest request = new BrAPIPlateSearchRequest().externalReferenceIDs(plateExternalIds)
                                                                       .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATES)));

        return brAPIDAOUtil.search(platesApi::searchPlatesPost, platesApi::searchPlatesSearchResultsDbIdGet, request);
    }

    public List<BrAPIPlate> readPlatesBySubmissionIds(Program program, List<String> submissionExternalIds) throws ApiException {
        PlatesApi platesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), PlatesApi.class);

        BrAPIPlateSearchRequest request = new BrAPIPlateSearchRequest().externalReferenceIDs(submissionExternalIds)
                                                                       .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATE_SUBMISSIONS)));

        return brAPIDAOUtil.search(platesApi::searchPlatesPost, platesApi::searchPlatesSearchResultsDbIdGet, request);
    }
}
