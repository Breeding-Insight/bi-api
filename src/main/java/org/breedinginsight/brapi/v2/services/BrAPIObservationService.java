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

package org.breedinginsight.brapi.v2.services;

import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.services.ProgramService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class BrAPIObservationService {
    private final ProgramService programService;
    private final BrAPITrialService trialService;
    private final BrAPIObservationDAO observationDAO;
    private final String referenceSource;

    @Inject
    public BrAPIObservationService(
            ProgramService programService,
            BrAPITrialService trialService,
            BrAPIObservationDAO observationDAO,
            @Property(name = "brapi.server.reference-source") String referenceSource) {
        this.programService = programService;
        this.trialService = trialService;
        this.observationDAO = observationDAO;
        this.referenceSource = referenceSource;
    }

    // TODO: add parameters.
    public BrAPIObservationTable getBrAPIObservationTable() {
        BrAPIObservationTable observationTable = new BrAPIObservationTable()
                .headerRow(Arrays.asList(BrAPIObservationTableHeaderRowEnum.values()))
                .observationVariables(getTableObservationVariables())
                .data(getTableData());
        return observationTable;
    }

    private List<BrAPIObservationTableObservationVariables> getTableObservationVariables() {
        throw new NotImplementedException();  // TODO: implement.
    }

    private List<List<String>> getTableData() {
        throw new NotImplementedException();  // TODO: implement.
    }

}
