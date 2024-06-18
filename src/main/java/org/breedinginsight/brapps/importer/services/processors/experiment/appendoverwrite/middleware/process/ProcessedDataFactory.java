package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import com.google.gson.Gson;
import io.micronaut.context.annotation.Bean;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validator.FieldValidator;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.BRAPI_REFERENCE_SOURCE;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;


@Factory
public class ProcessedDataFactory {
    private final FieldValidator fieldValidator;
    private final StudyService studyService;

    @Inject
    public ProcessedDataFactory(FieldValidator fieldValidator,
                                StudyService studyService) {

        this.fieldValidator = fieldValidator;
        this.studyService = studyService;
    }

    public static InitialData initialData(boolean isCommit,
                                          String cellData,
                                          String phenoColumnName,
                                          Trait trait,
                                          ExperimentObservation row,
                                          UUID trialId,
                                          UUID studyId,
                                          String unitId,
                                          String studyYear,
                                          BrAPIObservationUnit observationUnit,
                                          User user,
                                          Program program,
                                          FieldValidator fieldValidator,
                                          StudyService studyService) {
        return new InitialData(isCommit, cellData, phenoColumnName, trait, row, trialId, studyId, unitId, studyYear, observationUnit, user, program, fieldValidator, studyService);
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
    public InitialData initialDataBean(boolean isCommit,
                                       String cellData,
                                       String phenoColumnName,
                                       Trait trait,
                                       ExperimentObservation row,
                                       UUID trialId,
                                       UUID studyId,
                                       String unitId,
                                       String studyYear,
                                       BrAPIObservationUnit observationUnit,
                                       User user,
                                       Program program) {
        return initialData(isCommit, cellData, phenoColumnName, trait, row, trialId, studyId, unitId, studyYear, observationUnit, user, program, fieldValidator, studyService);
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

