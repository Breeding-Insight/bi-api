package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import com.google.gson.Gson;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validate.field.FieldValidator;
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

public class InitialData extends VisitedObservationData {
    boolean isCommit;
    String cellData;
    String phenoColumnName;
    Trait trait;
    ExperimentObservation row;
    UUID trialId;
    UUID studyId;
    String unitId;
    String studyYear;
    BrAPIObservationUnit observationUnit;
    User user;
    Program program;
    @Inject
    FieldValidator fieldValidator;
    @Inject
    StudyService studyService;
    @Inject
    Gson gson;

    public InitialData(boolean isCommit,
                       String cellData,
                       String phenoColumnName,
                       Trait trait,
                       ExperimentObservation row,
                       UUID trialId,
                       UUID studyId,
                       String unitId,
                       String studyYear,
                       BrAPIObservationUnit observationUnit, User user,
                       Program program) {
        this.isCommit = isCommit;
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
        String seasonDbId = studyService.seasonDbIdToYear(studyYear, program.getId());

        // Generate a new ID for the observation
        UUID observationID = UUID.randomUUID();

        // Construct the new observation
        BrAPIObservation newObservation = row.constructBrAPIObservation(cellData, phenoColumnName, seasonDbId, observationUnit, isCommit, program, user, BRAPI_REFERENCE_SOURCE, trialId, studyId, UUID.fromString(unitId), observationID);

        // Construct a pending observation with a status set to NEW
        return new PendingImportObject<>(ImportObjectState.NEW, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(newObservation, BrAPIObservation.class, program));

    }
}
