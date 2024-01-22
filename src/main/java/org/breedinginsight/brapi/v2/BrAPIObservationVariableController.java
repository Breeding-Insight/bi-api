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

package org.breedinginsight.brapi.v2;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIOntologyReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.*;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponseResult;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableSingleResponse;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationVariableController {
    private final String referenceSource;

    private final OntologyService ontologyService;
    private final TraitService traitService;

    private final BrAPITrialService trialService;

    private final ProgramService programService;

    @Inject
    public BrAPIObservationVariableController(OntologyService ontologyService,
                                              TraitService traitService,
                                              BrAPITrialService trialService,
                                              ProgramService programService,
                                              @Property(name = "brapi.server.reference-source") String referenceSource) {
        this.ontologyService = ontologyService;
        this.traitService = traitService;
        this.trialService = trialService;
        this.programService = programService;
        this.referenceSource = referenceSource;
    }

    @Get("/variables")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIObservationVariableListResponse> variablesGet(@PathVariable("programId") UUID programId,
                                                                           @Nullable  @QueryValue("observationVariableDbId") String observationVariableDbId,
                                                                           @Nullable @QueryValue("observationVariableName") String observationVariableName,
                                                                           @Nullable @QueryValue("observationVariablePUI") String observationVariablePUI,
                                                                           @Nullable @QueryValue("traitClass") String traitClass,
                                                                           @Nullable @QueryValue("methodDbId") String methodDbId,
                                                                           @Nullable @QueryValue("methodName") String methodName,
                                                                           @Nullable @QueryValue("methodPUI") String methodPUI,
                                                                           @Nullable @QueryValue("scaleDbId") String scaleDbId,
                                                                           @Nullable @QueryValue("scaleName") String scaleName,
                                                                           @Nullable @QueryValue("scalePUI") String scalePUI,
                                                                           @Nullable @QueryValue("traitDbId") String traitDbId,
                                                                           @Nullable @QueryValue("traitName") String traitName,
                                                                           @Nullable @QueryValue("traitPUI") String traitPUI,
                                                                           @Nullable @QueryValue("ontologyDbId") String ontologyDbId,
                                                                           @Nullable @QueryValue("commonCropName") String commonCropName,
                                                                           @Nullable @QueryValue("trialDbId") String experimentId,
                                                                           @Nullable @QueryValue("studyDbId") String environmentId,
                                                                           @Nullable @QueryValue("externalReferenceID") String externalReferenceID,
                                                                           @Nullable @QueryValue("externalReferenceId") String externalReferenceId,
                                                                           @Nullable @QueryValue("externalReferenceSource") String externalReferenceSource,
                                                                           @Nullable @QueryValue("page") Integer page,
                                                                           @Nullable @QueryValue("pageSize") Integer pageSize, HttpRequest request) {

        try {
            List<Trait> programTraits;
            if (observationVariablePUI != null || methodPUI != null || scalePUI != null || traitPUI != null || commonCropName != null || externalReferenceID != null || externalReferenceId != null || externalReferenceSource != null) {
                log.debug("unsupported variable filters, returning");
                programTraits = new ArrayList<>();
            } else if(environmentId != null || experimentId != null) {
                programTraits = getBrAPIObservationVariablesForExperiment(programId, Optional.ofNullable(experimentId), Optional.ofNullable(environmentId));
            } else {
                log.debug("fetching variables for the program: " + programId);
                programTraits = ontologyService.getTraitsByProgramId(programId, true);

            }

            List<BrAPIObservationVariable> filteredObsVars = filterVariables(programTraits,
                                                                                       Optional.ofNullable(observationVariableDbId),
                                                                                       Optional.ofNullable(observationVariableName),
                                                                                       Optional.ofNullable(traitClass),
                                                                                       Optional.ofNullable(methodDbId),
                                                                                       Optional.ofNullable(methodName),
                                                                                       Optional.ofNullable(scaleDbId),
                                                                                       Optional.ofNullable(scaleName),
                                                                                       Optional.ofNullable(traitDbId),
                                                                                       Optional.ofNullable(traitName),
                                                                                       Optional.ofNullable(ontologyDbId));

            BrAPIObservationVariableListResponse response = new BrAPIObservationVariableListResponse()
                    .metadata(new BrAPIMetadata()
                                      .pagination(new BrAPIIndexPagination()
                                                          .currentPage(0)
                                                          .totalPages(1)
                                                          .pageSize(filteredObsVars.size())
                                                          .totalCount(filteredObsVars.size())))
                    .result(new BrAPIObservationVariableListResponseResult().data(filteredObsVars));

            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.warn("Couldn't find object", e);
            return HttpResponse.notFound();
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "error fetching variables");
        }
    }

    @Get("/variables/{observationVariableDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIObservationVariableSingleResponse> variablesObservationVariableDbIdGet(@PathVariable("programId") UUID programId,
                                                            @PathVariable("observationVariableDbId") String observationVariableDbId) {
        log.debug("fetching variable: " + observationVariableDbId);
        UUID traitId;
        try {
            traitId = UUID.fromString(observationVariableDbId);
        } catch (IllegalArgumentException e) {
            return HttpResponse.notFound();
        }

        try {
            Optional<Trait> trait = traitService.getById(programId, traitId);

            if(trait.isEmpty()) {
                return HttpResponse.notFound();
            }

            BrAPIObservationVariableSingleResponse response = new BrAPIObservationVariableSingleResponse()
                    .metadata(new BrAPIMetadata())
                    .result(convertToBrAPI(trait.get()));

            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            return HttpResponse.notFound();
        }
    }

    @Put("/variables/{observationVariableDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> variablesObservationVariableDbIdPut(@PathVariable("programId") UUID programId,
                                                            @PathVariable("observationVariableDbId") String observationVariableDbId,
                                                               @Body BrAPIObservationVariable body) {
        //DO NOT IMPLEMENT - Users are only able to update traits via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Post("/variables")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> variablesPost(@PathVariable("programId") UUID programId, @Body List<BrAPIObservationVariable> body) {
        //DO NOT IMPLEMENT - Users are only able to create new traits via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    private BrAPIObservationVariable convertToBrAPI(Trait trait) {
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

    @NotNull
    private List<BrAPIObservationVariable> filterVariables(List<Trait> programTraits,
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

    private List<Trait> getBrAPIObservationVariablesForExperiment(UUID programId, Optional<String> experimentId, Optional<String> environmentId) throws DoesNotExistException, ApiException {
        log.debug(String.format("fetching variables for experiment.  expId: %s, envId: %s ", experimentId.orElse(""), environmentId.orElse("")));
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            throw new DoesNotExistException("Could not find program: " + programId);
        }
        UUID expId;
        if(experimentId.isPresent()) {
            expId = UUID.fromString(experimentId.get());
        } else {
            UUID envId = UUID.fromString(environmentId.get());
            BrAPIStudy environment = trialService.getEnvironment(program.get(), envId);
            expId = UUID.fromString(Utilities.getExternalReference(environment.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS)).get().getReferenceID());
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
}
