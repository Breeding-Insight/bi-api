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
package org.breedinginsight.brapi.v1.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapi.v1.model.*;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrapiObservationVariableService {

    private ProgramUserService programUserService;
    private TraitService traitService;

    @Inject
    public BrapiObservationVariableService(ProgramUserService programUserService, TraitService traitService) {
        this.programUserService = programUserService;
        this.traitService = traitService;
    }

    public List<ObservationVariable> getBrapiObservationVariablesForUser(AuthenticatedUser actingUser) throws DoesNotExistException {

        // find all programs user is in
        List<ProgramUser> programUsers = programUserService.getProgramUsersByUserId(actingUser.getId());

        List<UUID> programIds = programUsers.stream()
                .map(programUser -> programUser.getProgram().getId())
                .collect(Collectors.toList());

        // get traits for all programs user is in
        List<org.breedinginsight.model.Trait> biTraits = traitService.getByProgramIds(programIds, true);

        List<ObservationVariable> variables = biTraits.stream()
                .map(biTrait -> mapBiTraitToBrapiV1ObservationVariable(biTrait))
                .collect(Collectors.toList());

        return variables;
    }

    private TraitDataType mapBiDataTypeToBrapiV1TraitDataType(DataType dataType) {
        TraitDataType traitDataType;

        switch (dataType) {
            case DATE:
                traitDataType = TraitDataType.DATE;
                break;
            case TEXT:
                traitDataType = TraitDataType.TEXT;
                break;
            case NOMINAL:
                traitDataType = TraitDataType.NOMINAL;
                break;
            case ORDINAL:
                traitDataType = TraitDataType.ORDINAL;
                break;
            case DURATION:
                traitDataType = TraitDataType.DURATION;
                break;
            case NUMERICAL:
                traitDataType = TraitDataType.NUMERICAL;
                break;
            default:
                traitDataType = TraitDataType.TEXT;
                break;
        }

        return traitDataType;
    }

    private ObservationVariable mapBiTraitToBrapiV1ObservationVariable(org.breedinginsight.model.Trait trait) {

        org.breedinginsight.model.Method biMethod = trait.getMethod();
        org.breedinginsight.model.Scale biScale = trait.getScale();

        Method brapiMethod = Method.builder()
                .methodName(String.format("%s %s", trait.getObservationVariableName(), biMethod.getMethodClass()))
                .description(biMethod.getDescription())
                .methodDbId(biMethod.getId().toString())
                .propertyClass(biMethod.getMethodClass())
                .formula(biMethod.getFormula())
                //.ontologyRefernce() // TODO: once we pass this to brapi service in bi trait service
                //.reference() // don't have this in brapi 2.0
                .build();

        TraitDataType dataType = mapBiDataTypeToBrapiV1TraitDataType(biScale.getDataType());

        List<String> categories = biScale.getCategories().stream()
                .map(category -> category.getValue()).collect(Collectors.toList());

        ValidValues validValues = ValidValues.builder()
                .categories(categories)
                .min(biScale.getValidValueMin())
                .max(biScale.getValidValueMax())
                .build();

        // TODO: Fix Field Book bug
        // field book has a bug checking categories, if list is empty will crash
        // can't just set validValues to null because min/max are mixed in with categories
        if (validValues.getCategories().isEmpty()) {
            validValues.getCategories().add("bug");
        }

        Scale brapiScale = Scale.builder()
                .dataType(dataType)
                .decimalPlaces(biScale.getDecimalPlaces())
                //.ontologyRefernce() // TODO: once we pass this to brapi service in bi trait service
                .scaleDbId(biScale.getId().toString())
                .scaleName(biScale.getScaleName())
                .validValues(validValues)
                .xref(biScale.getId().toString())
                .build();

        Trait brapiTrait = Trait.builder()
                //.alternativeAbbreviations(Arrays.asList(trait.getAbbreviations().clone()))
                .attribute(trait.getAttribute())
                .propertyClass(trait.getTraitClass())
                .entity(trait.getEntity())
                .mainAbbreviation(trait.getMainAbbreviation())
                .description(trait.getMethod().getDescription())
                //.ontologyRefernce() // TODO: once we pass this to brapi service in bi trait service
                //.status(trait.getStatus())
                .synonyms(trait.getSynonyms())
                .traitDbId(trait.getId().toString())
                .traitName(trait.getObservationVariableName())
                .xref(trait.getId().toString())
                .build();

        List<String> synonyms = new ArrayList<>();
        synonyms.add(trait.getObservationVariableName());

        ObservationVariable brapiVariable = ObservationVariable.builder()
                //.contextOfUse() // not stored in brapi service
                //.crop(trait.getProgramOntology().getProgram().getSpecies().getCommonName()) //TODO: not populated in trait model
                .defaultValue(trait.getDefaultValue() != null ? trait.getDefaultValue() : "") // TODO: fix need a default value for field book bug
                //.documentationURL() // not stored in brapi service
                //.growthStage() // not stored in brapi service
                //.institution() // missing from bi trait model but stored in BrAPI service
                //.language() // missing from bi trait model but stored in BrAPI service
                .method(brapiMethod)
                //.ontologyRefernce() // TODO: once we pass this to brapi service in bi trait service
                .scale(brapiScale)
                //.scientist() // missing from bi trait model but stored in BrAPI service
                //.status(trait.getStatus())
                .submissionTimestamp(trait.getCreatedAt())
                .synonyms(trait.getSynonyms().isEmpty() ? synonyms : trait.getSynonyms()) // TODO: fix need to have synonym for field book bug
                .trait(brapiTrait)
                .xref(trait.getId().toString())
                .observationVariableDbId(trait.getId().toString())
                .observationVariableName(trait.getObservationVariableName())
                .build();

        return brapiVariable;
    }

}
