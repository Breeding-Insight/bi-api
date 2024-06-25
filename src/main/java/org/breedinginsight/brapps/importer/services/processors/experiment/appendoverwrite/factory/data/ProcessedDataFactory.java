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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.validator.field.FieldValidator;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;

import javax.inject.Inject;
import java.util.UUID;


@Factory
public class ProcessedDataFactory {
    private final FieldValidator fieldValidator;
    private final StudyService studyService;
    private final ObservationService observationService;

    @Inject
    public ProcessedDataFactory(FieldValidator fieldValidator,
                                StudyService studyService,
                                ObservationService observationService) {

        this.fieldValidator = fieldValidator;
        this.studyService = studyService;
        this.observationService = observationService;
    }

    public static InitialData initialData(String brapiReferenceSource,
                                          boolean isCommit,
                                          String germplasmName,
                                          BrAPIStudy study,
                                          String cellData,
                                          String timestamp,
                                          String phenoColumnName,
                                          String timestampColumnName,
                                          Trait trait,
                                          ExperimentObservation row,
                                          UUID trialId,
                                          UUID studyId,
                                          UUID unitId,
                                          String studyYear,
                                          BrAPIObservationUnit observationUnit,
                                          User user,
                                          Program program,
                                          FieldValidator fieldValidator,
                                          StudyService studyService,
                                          ObservationService observationService) {
        return new InitialData(brapiReferenceSource, isCommit, germplasmName, study, cellData, timestamp, phenoColumnName, timestampColumnName, trait, row, trialId, studyId, unitId, studyYear, observationUnit, user, program, fieldValidator, studyService, observationService);
    }

    public static OverwrittenData overwrittenData(boolean canOverwrite,
                                                  boolean isCommit,
                                                  String unitId,
                                                  Trait trait,
                                                  String phenoColumnName,
                                                  String timestampColumnName,
                                                  String cellData,
                                                  String timestamp,
                                                  String reason,
                                                  BrAPIObservation observation,
                                                  UUID userId,
                                                  Program program,
                                                  FieldValidator fieldValidator,
                                                  ObservationService observationService) {
        return new OverwrittenData(canOverwrite, isCommit, unitId, trait, phenoColumnName, timestampColumnName, cellData, timestamp, reason, observation, userId, program, fieldValidator, observationService);
    }

    public static UnchangedData unchangedData(BrAPIObservation observation, Program program) {
        return new UnchangedData(observation, program);
    }

    @Bean
    @Prototype
    public InitialData initialDataBean(String brapiReferenceSource,
                                       boolean isCommit,
                                       String germplasmName,
                                       BrAPIStudy study,
                                       String cellData,
                                       String timestamp,
                                       String phenoColumnName,
                                       String timestampColumnName,
                                       Trait trait,
                                       ExperimentObservation row,
                                       UUID trialId,
                                       UUID studyId,
                                       UUID unitId,
                                       String studyYear,
                                       BrAPIObservationUnit observationUnit,
                                       User user,
                                       Program program) {
        return initialData(brapiReferenceSource, isCommit, germplasmName, study, cellData, timestamp, phenoColumnName, timestampColumnName, trait, row, trialId, studyId, unitId, studyYear, observationUnit, user, program, fieldValidator, studyService, observationService);
    }

    @Bean
    @Prototype
    public OverwrittenData overwrittenDataBean(boolean canOverwrite,
                                               boolean isCommit,
                                               String unitId,
                                               Trait trait,
                                               String phenoColumnName,
                                               String timestampColumnName,
                                               String cellData,
                                               String timestamp,
                                               String reason,
                                               BrAPIObservation observation,
                                               UUID userId,
                                               Program program) {
        return overwrittenData(canOverwrite, isCommit, unitId, trait, phenoColumnName, timestampColumnName, cellData, timestamp, reason, observation, userId, program, fieldValidator, observationService);
    }

    @Bean
    @Prototype
    public UnchangedData unchangedDataBean(BrAPIObservation observation, Program program) {
        return unchangedData(observation, program);
    }
}

