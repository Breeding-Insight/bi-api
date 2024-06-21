package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

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
                                          String phenoColumnName,
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
        return new InitialData(brapiReferenceSource, isCommit, germplasmName, study, cellData, phenoColumnName, trait, row, trialId, studyId, unitId, studyYear, observationUnit, user, program, fieldValidator, studyService, observationService);
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
                                                  FieldValidator fieldValidator) {
        return new OverwrittenData(canOverwrite, isCommit, unitId, trait, phenoColumnName, timestampColumnName, cellData, timestamp, reason, observation, userId, program, fieldValidator);
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
                                       String phenoColumnName,
                                       Trait trait,
                                       ExperimentObservation row,
                                       UUID trialId,
                                       UUID studyId,
                                       UUID unitId,
                                       String studyYear,
                                       BrAPIObservationUnit observationUnit,
                                       User user,
                                       Program program) {
        return initialData(brapiReferenceSource, isCommit, germplasmName, study, cellData, phenoColumnName, trait, row, trialId, studyId, unitId, studyYear, observationUnit, user, program, fieldValidator, studyService, observationService);
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
        return overwrittenData(canOverwrite, isCommit, unitId, trait, phenoColumnName, timestampColumnName, cellData, timestamp, reason, observation, userId, program, fieldValidator);
    }

    @Bean
    @Prototype
    public UnchangedData unchangedDataBean(BrAPIObservation observation, Program program) {
        return unchangedData(observation, program);
    }
}

