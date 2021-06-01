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
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ObservationDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Singleton
public class BrAPIObservationDAO {

    private ProgramDAO programDAO;
    private ObservationDAO observationDAO;
    private ImportDAO importDAO;

    @Inject
    public BrAPIObservationDAO(ProgramDAO programDAO, ObservationDAO observationDAO, ImportDAO importDAO) {
        this.programDAO = programDAO;
        this.observationDAO = observationDAO;
        this.importDAO = importDAO;
    }

    public List<BrAPIObservation> createBrAPIObservation(List<BrAPIObservation> brAPIObservationList, UUID programId, ImportUpload upload) throws ApiException {
        ObservationsApi api = new ObservationsApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.post(brAPIObservationList, upload, api::observationsPost, importDAO::update);
    }

}
