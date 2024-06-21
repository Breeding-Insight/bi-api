package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import com.google.gson.Gson;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.validator.field.FieldValidator;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Prototype
public class InitialData extends VisitedObservationData {
    String brapiReferenceSource;
    boolean isCommit;
    String germplasmName;
    BrAPIStudy study;
    String cellData;
    String phenoColumnName;
    Trait trait;
    ExperimentObservation row;
    UUID trialId;
    UUID studyId;
    UUID unitId;
    String studyYear;
    BrAPIObservationUnit observationUnit;
    User user;
    Program program;
    private final FieldValidator fieldValidator;
    private final StudyService studyService;
    private final ObservationService observationService;
    Gson gson;

    InitialData(String brapiReferenceSource,
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
        this.brapiReferenceSource = brapiReferenceSource;
        this.isCommit = isCommit;
        this.germplasmName = germplasmName;
        this.study = study;
        this.cellData = cellData;
        this.phenoColumnName = phenoColumnName;
        this.trait = trait;
        this.row = row;
        this.trialId = trialId;
        this.studyId = studyId;
        this.unitId = unitId;
        this.studyYear = studyYear;
        this.observationUnit = observationUnit;
        this.user = user;
        this.program = program;
        this.fieldValidator = fieldValidator;
        this.studyService = studyService;
        this.observationService = observationService;
        this.gson = new Gson();
    }
    @Override
    public Optional<List<ValidationError>> getValidationErrors() {
        List<ValidationError> errors = new ArrayList<>();

        // Validate observation value
        fieldValidator.validateField(phenoColumnName, cellData, trait).ifPresent(errors::add);

        return Optional.ofNullable(errors.isEmpty() ? null : errors);
    }

    @Override
    public PendingImportObject<BrAPIObservation> constructPendingObservation() {
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
                cellData,
                trialId,
                studyId,
                unitId,
                observationId,
                brapiReferenceSource,
                user,
                program);

        // Construct a pending observation with a status set to NEW
        return new PendingImportObject<>(ImportObjectState.NEW, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(newObservation, BrAPIObservation.class, program));

    }

    @Override
    public void updateTally(AppendStatistic statistic) {
        statistic.incrementNewCount(1);
    }
}
