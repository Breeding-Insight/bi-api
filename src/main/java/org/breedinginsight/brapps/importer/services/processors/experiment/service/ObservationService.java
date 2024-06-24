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

package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ObservationService {
    private final ExperimentUtilities experimentUtilities;

    @Inject
    public ObservationService(ExperimentUtilities experimentUtilities) {
        this.experimentUtilities = experimentUtilities;
    }

    public boolean validCategory(List<BrAPIScaleValidValuesCategories> categories, String value) {
        Set<String> categoryValues = categories.stream()
                .map(category -> category.getValue().toLowerCase())
                .collect(Collectors.toSet());
        return categoryValues.contains(value.toLowerCase());
    }
    public boolean validNumericRange(BigDecimal value, Scale validValues) {
        // account for empty min or max in valid determination
        return (validValues.getValidValueMin() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMin())) >= 0) &&
                (validValues.getValidValueMax() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMax())) <= 0);
    }
    public Optional<BigDecimal> validNumericValue(String value) {
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(number);
    }

    public boolean isBlankObservation(String value) {
        return StringUtils.isBlank(value);
    }
    public boolean isNAObservation(String value){
        return value.equalsIgnoreCase("NA");
    }
    public boolean validDateTimeValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    public boolean validDateValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }
    public String getObservationHash(String observationUnitName, String variableName, String studyName) {
        String concat = DigestUtils.sha256Hex(observationUnitName) +
                DigestUtils.sha256Hex(variableName) +
                DigestUtils.sha256Hex(StringUtils.defaultString(studyName));
        return DigestUtils.sha256Hex(concat);
    }

    public BrAPIObservation constructNewBrAPIObservation(boolean commit,
                                                      String germplasmName,
                                                      String variableName,
                                                      BrAPIStudy study,
                                                      String seasonDbId,
                                                      BrAPIObservationUnit obsUnit,
                                                      String value,
                                                      UUID trialId,
                                                      UUID studyId,
                                                      UUID obsUnitId,
                                                      UUID observationId,
                                                      String referenceSource,
                                                      User user,
                                                      Program program) {
        BrAPIObservation observation = new BrAPIObservation();
        observation.setGermplasmName(germplasmName);

        observation.putAdditionalInfoItem(BrAPIAdditionalInfoFields.STUDY_NAME, Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), program.getKey()));

        observation.setObservationVariableName(variableName);
        observation.setObservationUnitDbId(obsUnit.getObservationUnitDbId());
        observation.setObservationUnitName(obsUnit.getObservationUnitName());
        observation.setValue(value);

        // The BrApi server needs this.  Breedbase does not.
        BrAPISeason season = new BrAPISeason();
        season.setSeasonDbId(seasonDbId);
        observation.setSeason(season);

        if(commit) {
            Map<String, Object> createdBy = new HashMap<>();
            createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID, user.getId());
            createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME, user.getName());
            observation.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_BY, createdBy);
            observation.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_DATE, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));

            observation.setExternalReferences(experimentUtilities.constructBrAPIExternalReferences(program, referenceSource, trialId,null, studyId, obsUnitId, observationId));
        }
        return observation;
    }
}
