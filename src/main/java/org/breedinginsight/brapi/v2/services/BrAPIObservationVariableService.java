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

package org.breedinginsight.brapi.v2.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIOntologyReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.*;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIObservationVariableService {
    private final ProgramService programService;
    private final BrAPITrialService trialService;
    private final String referenceSource;

    @Inject
    public BrAPIObservationVariableService(
            ProgramService programService, BrAPITrialService trialService, String referenceSource) {
        this.programService = programService;
        this.trialService = trialService;
        this.referenceSource = referenceSource;
    }

    public List<Trait> getBrAPIObservationVariablesForExperiment(
            UUID programId,
            Optional<String> experimentId,
            Optional<String> environmentId
    ) throws DoesNotExistException, ApiException {
        log.debug(String.format("fetching variables for experiment.  expId: %s, envId: %s ",
                experimentId.orElse(""), environmentId.orElse("")));
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            throw new DoesNotExistException("Could not find program: " + programId);
        }
        UUID expId;
        if(experimentId.isPresent()) {
            expId = UUID.fromString(experimentId.get());
        } else {
            UUID envId = UUID.fromString(environmentId.orElseThrow(() -> new IllegalStateException("no environment id found")));
            BrAPIStudy environment = trialService.getEnvironment(program.get(), envId);
            expId = UUID.fromString(Utilities.getExternalReference(environment.getExternalReferences(),
                    Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS))
                    .orElseThrow(() -> new IllegalStateException("no external reference found")).getReferenceId());
        }

        BrAPITrial experiment = trialService.getExperiment(program.get(), expId);
        if(experiment
                .getAdditionalInfo().getAsJsonObject()
                .has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            String obsDatasetId = experiment
                    .getAdditionalInfo().getAsJsonObject()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString();
            return trialService.getDatasetObsVars(obsDatasetId, program.get());
        }

        return new ArrayList<>();
    }

    @NotNull
    public List<BrAPIObservationVariable> filterVariables(List<Trait> programTraits,
                                                           Optional<String> observationVariableDbId,
                                                           Optional<String> observationVariableName,
                                                           Optional<String> traitClass,
                                                           Optional<String> methodDbId,
                                                           Optional<String> methodName,
                                                           Optional<String> scaleDbId,
                                                           Optional<String> scaleName,
                                                           Optional<String> traitDbId,
                                                           Optional<String> traitName,
                                                           Optional<String> ontologyDbId) throws DoesNotExistException {
        log.debug("filtering variables:\n" +
                "observationVariableDbId: " + observationVariableDbId + "\n" +
                "observationVariableName: " + observationVariableName + "\n" +
                "traitClass: " + traitClass + "\n" +
                "methodDbId: " + methodDbId + "\n" +
                "methodName: " + methodName + "\n" +
                "scaleDbId: " + scaleDbId + "\n" +
                "scaleName: " + scaleName + "\n" +
                "traitDbId: " + traitDbId + "\n" +
                "traitName: " + traitName + "\n" +
                "ontologyDbId: " + ontologyDbId);

        return programTraits.stream()
                .filter(trait -> {
                    boolean matches = true;

                    Map<String, Pair<Optional<String>, String>> filterParams = new HashMap<>();
                    filterParams.put("observationVariableDbId",
                            Pair.of(observationVariableDbId,
                                    trait.getId()
                                            .toString()));
                    filterParams.put("observationVariableName", Pair.of(observationVariableName, trait.getObservationVariableName()));
                    filterParams.put("traitClass", Pair.of(traitClass, trait.getTraitClass()));
                    filterParams.put("methodDbId",
                            Pair.of(methodDbId,
                                    trait.getMethodId()
                                            .toString()));
                    filterParams.put("methodName",
                            Pair.of(methodName,
                                    trait.getMethod()
                                            .getDescription()));
                    filterParams.put("scaleDbId",
                            Pair.of(scaleDbId,
                                    trait.getScale()
                                            .getId()
                                            .toString()));
                    filterParams.put("scaleName",
                            Pair.of(scaleName,
                                    trait.getScale()
                                            .getScaleName()));
                    filterParams.put("traitDbId",
                            Pair.of(traitDbId,
                                    trait.getId()
                                            .toString()));
                    filterParams.put("traitName", Pair.of(traitName, trait.getObservationVariableName()));
                    filterParams.put("ontologyDbId",
                            Pair.of(ontologyDbId,
                                    trait.getProgramOntologyId()
                                            .toString()));

                    for (Map.Entry<String, Pair<Optional<String>, String>> filter : filterParams.entrySet()) {
                        if (filter.getValue().getLeft().isPresent()) {
                            log.debug("filtering traits by: " + filter.getKey());
                            matches = StringUtils.equals(filter.getValue()
                                            .getLeft()
                                            .get(),
                                    filter.getValue()
                                            .getRight());
                        }
                        if (!matches) {
                            break;
                        }
                    }

                    return matches;
                })
                .map(this::convertToBrAPI)
                .collect(Collectors.toList());
    }

    public BrAPIObservationVariable convertToBrAPI(Trait trait) {
        BrAPIOntologyReference brAPIOntologyReference = new BrAPIOntologyReference().ontologyDbId(trait.getProgramOntologyId()
                .toString());
        String status = trait.getActive() ? "active" : "inactive";
        List<String> synonyms = prepSynonyms(trait);
        return new BrAPIObservationVariable().observationVariableDbId(trait.getId().toString())
                .observationVariableName(trait.getObservationVariableName())
                .defaultValue(trait.getDefaultValue())
                .status(status)
                .synonyms(synonyms)
                .trait(new BrAPITrait().ontologyReference(brAPIOntologyReference)
                        .traitName(trait.getObservationVariableName())
                        .traitDbId(trait.getId().toString())
                        .entity(trait.getEntity())
                        .attribute(trait.getAttribute())
                        .status(status)
                        .synonyms(synonyms))
                .method(new BrAPIMethod().ontologyReference(brAPIOntologyReference)
                        .methodDbId(trait.getMethod().getId().toString())
                        .methodClass(trait.getMethod().getMethodClass())
                        .description(trait.getMethod().getDescription())
                        .formula(trait.getMethod().getFormula()))
                .scale(new BrAPIScale().ontologyReference(brAPIOntologyReference)
                        .scaleDbId(trait.getScale().getId().toString())
                        .scaleName(trait.getScale().getScaleName())
                        .dataType(BrAPITraitDataType.fromValue(trait.getScale().getDataType().getLiteral()))
                        .decimalPlaces(trait.getScale().getDecimalPlaces())
                        .validValues(new BrAPIScaleValidValues().max(trait.getScale().getValidValueMax())
                                .min(trait.getScale().getValidValueMin())
                                .categories(trait.getScale().getCategories())));
    }

    /**
     * Create a list of synonyms, and ensure that there the first element matches the name of the trait<br><br>
     * This is primarily needed to ensure any system using the first synonym as a display shows the actual name of the ontology term
     * @param trait
     * @return list of synonyms with at least one value (the name of the trait)
     */
    private List<String> prepSynonyms(Trait trait) {
        List<String> preppedSynonyms = new ArrayList<>();
        if(trait.getSynonyms() != null) {
            preppedSynonyms = trait.getSynonyms();
            int traitNameIdx = -1;
            for(int i = 0; i < preppedSynonyms.size(); i++) {
                if(preppedSynonyms.get(i).equals(trait.getObservationVariableName())) {
                    traitNameIdx = i;
                    break;
                }
            }
            if(traitNameIdx > -1) {
                String temp = preppedSynonyms.get(traitNameIdx);
                preppedSynonyms.set(traitNameIdx, preppedSynonyms.get(0));
                preppedSynonyms.set(0, temp);
            } else {
                preppedSynonyms.add(0, trait.getObservationVariableName());
            }
        } else {
            preppedSynonyms.add(trait.getObservationVariableName());
        }
        return preppedSynonyms;
    }
}
