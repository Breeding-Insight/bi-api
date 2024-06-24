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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.data;

import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.data.VisitedObservationData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.AppendStatistic;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@Prototype
public class UnchangedData extends VisitedObservationData {
    BrAPIObservation observation;
    Program program;

    @Inject
    public UnchangedData(BrAPIObservation observation, Program program) {
        this.observation = observation;
        this.program = program;
    }
    @Override
    public Optional<List<ValidationError>> getValidationErrors() {
        return Optional.empty();
    }

    @Override
    public PendingImportObject<BrAPIObservation> constructPendingObservation() {
        // Construct a pending observation with a status set to EXISTING
        PendingImportObject<BrAPIObservation> pendingExistingObservation = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(observation, BrAPIObservation.class, program));

        return pendingExistingObservation;
    }

    @Override
    public void updateTally(AppendStatistic statistic) {
        statistic.incrementExistingCount(1);
    }
}
