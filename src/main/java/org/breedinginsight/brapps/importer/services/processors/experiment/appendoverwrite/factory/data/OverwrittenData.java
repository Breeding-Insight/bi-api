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

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.AppendStatistic;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.validator.field.FieldValidator;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Prototype
public class OverwrittenData extends VisitedObservationData {

    FieldValidator fieldValidator;
    ObservationService observationService;
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

    @Inject
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
                           Program program,
                           FieldValidator fieldValidator,
                           ObservationService observationService) {
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
        this.fieldValidator = fieldValidator;
        this.observationService = observationService;
        this.gson = new GsonBuilder().registerTypeAdapterFactory(new GeometryAdapterFactory()).create();
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
            String formattedTimeStampValue = formatter.format(observationService.parseDateTime(timestamp));
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
        return cellData.equals(observation.getValue());
    }

    private boolean isTimestampMatched() {
        if (StringUtils.isBlank(timestamp)) {
            return observation.getObservationTimeStamp() == null;
        } else {
            return observationService.parseDateTime(timestamp).equals(observation.getObservationTimeStamp());
        }
    }
}
