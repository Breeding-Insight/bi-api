package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.http.HttpStatus;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validator.FieldValidator;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OverwrittenData extends VisitedObservationData {
    @Inject
    FieldValidator fieldValidator;
    Gson gson;
    boolean canOverwrite;
    boolean isCommit;
    String unitId;
    Trait trait;
    String phenoColumnName;
    String timestampColumnName;
    String cellData;
    String timestamp;
    String reason;
    BrAPIObservation observation;
    UUID userId;
    Program program;

    public OverwrittenData(boolean canOverwrite,
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
        this.canOverwrite = canOverwrite;
        this.isCommit = isCommit;
        this.unitId = unitId;
        this.trait = trait;
        this.phenoColumnName = phenoColumnName;
        this.timestampColumnName = timestampColumnName;
        this.cellData = cellData;
        this.timestamp = timestamp;
        this.reason = reason;
        this.observation = observation;
        this.userId = userId;
        this.program = program;
        this.gson = new Gson();
    }

    @Override
    public Optional<List<ValidationError>> getValidationErrors() {
        List<ValidationError> errors = new ArrayList<>();

        // Errors for trying to change protected data
        if (!canOverwrite) {
            if (!isValueMatched()) {
                errors.add(new ValidationError(phenoColumnName, String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", unitId, phenoColumnName), HttpStatus.UNPROCESSABLE_ENTITY));
            }
            if (!isTimestampMatched()) {
                errors.add(new ValidationError(timestampColumnName, String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", unitId, timestampColumnName), HttpStatus.UNPROCESSABLE_ENTITY));
            }
        }

        // Validate observation value
        fieldValidator.validateField(phenoColumnName, cellData, trait).ifPresent(errors::add);

        // Validate timestamp
        fieldValidator.validateField(timestampColumnName, timestamp, null).ifPresent(errors::add);

        return Optional.ofNullable(errors.isEmpty() ? null : errors);
    }

    @Override
    public PendingImportObject<BrAPIObservation> constructPendingObservation() {
        // Construct a pending observation with a status set to MUTATED
        PendingImportObject<BrAPIObservation> pendingUpdatedObservation = new PendingImportObject<>(ImportObjectState.MUTATED, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(observation, BrAPIObservation.class, program));
        BrAPIObservation update = pendingUpdatedObservation.getBrAPIObject();
        String original = null;

        if (!isValueMatched()) {
            // Update the observation value
            update.setValue(cellData);

            // Record original observation value for changelog entry
            original = observation.getValue();
        }

        if (!isTimestampMatched()) {
            // Update the timestamp
            DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
            String formattedTimeStampValue = formatter.format(OffsetDateTime.parse(timestamp));
            update.setObservationTimeStamp(OffsetDateTime.parse(formattedTimeStampValue));

            // Add original timestamp to changelog entry
            original = Optional.ofNullable(original).map(o -> o + " " + observation.getObservationTimeStamp()).orElse(String.valueOf(observation.getObservationTimeStamp()));
        }

        // If the change is to be committed, attach a record of the change as BrAPI observation additional info
        if (isCommit) {
            // Create the changelog field in observation additional info if it does not already exist
            createAdditionalInfoChangeLog(update);

            // Construct a changelog entry
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
            String rightNow = formatter.format(OffsetDateTime.now());
            ChangeLogEntry entry = new ChangeLogEntry(original, Optional.ofNullable(reason).orElse(""), userId, rightNow);

            // Add the entry to the changelog
            update.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CHANGELOG).getAsJsonArray().add(gson.toJsonTree(entry).getAsJsonObject());
        }

        return pendingUpdatedObservation;
    }

    @Override
    public void updateTally(AppendStatistic statistic) {
        statistic.incrementMutatedCount(1);
    }

    private void createAdditionalInfoChangeLog(BrAPIObservation update) {
        if (update.getAdditionalInfo().isJsonNull()) {
            update.setAdditionalInfo(new JsonObject());
            update.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
        }
        if (update.getAdditionalInfo() != null && !update.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CHANGELOG)) {
            update.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
        }
    }

    private boolean isValueMatched() {
        return !cellData.equals(observation.getValue());
    }

    private boolean isTimestampMatched() {
        if (timestamp == null) {
            return observation.getObservationTimeStamp() == null;
        } else {
            return !OffsetDateTime.parse(timestamp).equals(observation.getObservationTimeStamp());
        }
    }
//    private void validateTimeStampValue(String value,
//                                        String columnHeader, ValidationErrors validationErrors, int row) {
//        if (StringUtils.isBlank(value)) {
//            log.debug(String.format("skipping validation of observation timestamp because there is no value.\n\tvariable: %s\n\trow: %d", columnHeader, row));
//            return;
//        }
//        if (!validDateValue(value) && !validDateTimeValue(value)) {
//            addRowError(columnHeader, "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm", validationErrors, row);
//        }
//
//    }

    private boolean validDateValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    private boolean validDateTimeValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

}
