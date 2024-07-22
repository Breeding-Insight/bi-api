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
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.AppendStatistic;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Prototype
public class EmptyData extends VisitedObservationData {
    String brapiReferenceSource;
    boolean isCommit;
    String germplasmName;
    BrAPIStudy study;
    String phenoColumnName;
    UUID trialId;
    UUID studyId;
    UUID unitId;
    String studyYear;
    BrAPIObservationUnit observationUnit;
    User user;
    Program program;
    private final StudyService studyService;
    private final ObservationService observationService;

    public EmptyData(String brapiReferenceSource,
                     boolean isCommit,
                     String germplasmName,
                     BrAPIStudy study,
                     String phenoColumnName,
                     UUID trialId,
                     UUID studyId,
                     UUID unitId,
                     String studyYear,
                     BrAPIObservationUnit observationUnit,
                     User user,
                     Program program,
                     StudyService studyService,
                     ObservationService observationService) {
        this.brapiReferenceSource = brapiReferenceSource;
        this.isCommit = isCommit;
        this.germplasmName = germplasmName;
        this.study = study;
        this.phenoColumnName = phenoColumnName;
        this.trialId = trialId;
        this.studyId = studyId;
        this.unitId = unitId;
        this.studyYear = studyYear;
        this.observationUnit = observationUnit;
        this.user = user;
        this.program = program;
        this.studyService = studyService;
        this.observationService = observationService;
    }

    @Override
    public Optional<List<ValidationError>> getValidationErrors() {
        return Optional.empty();
    }

    @Override
    public PendingImportObject<BrAPIObservation> constructPendingObservation() {
        /**
         * TODO: fix the front end experiment import preview table so that it won't break if a table row has
         * an empty observations array when there are phenotype columns. Once this is fixed on the front end,
         * delete the work-around below and simply have this method return null.
          */

        String seasonDbId = studyService.yearToSeasonDbIdFromDatabase(studyYear, program.getId());

        // Generate a new ID for the observation
        UUID observationId = UUID.randomUUID();

        // Construct the new observation
        BrAPIObservation newObservation = observationService.constructNewBrAPIObservation(isCommit,
                germplasmName,
                phenoColumnName,
                study,
                seasonDbId,
                observationUnit,
                "", // the value of the observation is empty
                trialId,
                studyId,
                unitId,
                observationId,
                brapiReferenceSource,
                user,
                program);

        // Construct a pending observation with a status set to NEW
        return new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(newObservation, BrAPIObservation.class, program));
    }

    @Override
    public void updateTally(AppendStatistic statistic) {

    }
}
