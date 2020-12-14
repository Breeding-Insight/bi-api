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

package org.breedinginsight.daos;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.APIException;
import org.brapi.client.v2.model.exceptions.HttpException;
import org.brapi.client.v2.modules.phenotype.VariablesAPI;
import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.phenotyping.model.*;
import org.brapi.v2.phenotyping.model.request.VariablesRequest;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.TraitDao;
import org.breedinginsight.model.*;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.dao.db.Tables.*;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.values;

@Singleton
public class TraitDAO extends TraitDao {

    private DSLContext dsl;
    private BrAPIProvider brAPIProvider;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Inject
    public TraitDAO(Configuration config, DSLContext dsl, BrAPIProvider brAPIProvider) {
        super(config);
        this.dsl = dsl;
        this.brAPIProvider = brAPIProvider;
    }

    public List<Trait> getTraitsFullByProgramId(UUID programId) {
        List<UUID> programIds = new ArrayList<>();
        programIds.add(programId);
        return getTraitsFullByProgramIds(programIds);
    }

    public List<Trait> getTraitsFullByProgramIds(List<UUID> programIds) {

        // Get our db traits (equivalent to brapi variables)
        List<Trait> dbVariables = getTraitsByProgramIds(programIds.toArray(UUID[]::new));
        if (dbVariables.size() == 0){
            return new ArrayList<>();
        }
        Map<UUID, Trait> dbVariablesMap = dbVariables.stream().collect(Collectors.toMap(Trait::getId, p -> p));

        // Get brapi variables
        VariablesRequest variablesRequest = VariablesRequest.builder()
                .externalReferenceSource(referenceSource)
                .build();
        List<BrApiVariable> brApiVariables;
        try {
            brApiVariables = brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO).getVariables(variablesRequest);
        } catch (HttpException | APIException e) {
            throw new InternalServerException(e.getMessage());
        }

        Map<String, BrApiVariable> brApiVariableMap = new HashMap<>();
        for (BrApiVariable brApiVariable: brApiVariables) {
            List<BrApiExternalReference> brApiExternalReferences = brApiVariable.getExternalReferences();
            for (BrApiExternalReference brApiExternalReference: brApiExternalReferences){
                if (brApiExternalReference.getReferenceID() != null) {
                    brApiVariableMap.put(brApiExternalReference.getReferenceID(), brApiVariable);
                }
            }
        }

        List<Trait> saturatedTraits = new ArrayList<>();
        for (Trait trait: dbVariables) {
            // assumes external reference id is unique to each brapi variable
            if (brApiVariableMap.containsKey(trait.getId().toString())){
                BrApiVariable brApiVariable = brApiVariableMap.get(trait.getId().toString());
                trait.setBrAPIProperties(brApiVariable);

                Method method = trait.getMethod();
                method.setBrAPIProperties(brApiVariable.getMethod());

                Scale scale = trait.getScale();
                scale.setBrAPIProperties(brApiVariable.getScale());

                saturatedTraits.add(trait);
            } else {
                throw new InternalServerException("Could not find trait in returned brapi server results");
            }
        }

        return saturatedTraits;
    }

    public List<Trait> getTraitsByProgramId(UUID programId) {
        return getTraitsByProgramIds(programId);
    }

    public List<Trait> getTraitsByProgramIds(UUID ...programIds) {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        Result<Record> recordResult = getTraitSql(createdByUser, updatedByUser)
                .where(PROGRAM_ONTOLOGY.PROGRAM_ID.in(programIds))
                .fetch();

        List<Trait> traitResults = new ArrayList<>();
        for (Record record: recordResult) {
            Trait trait = parseTraitRecord(record, createdByUser, updatedByUser);
            traitResults.add(trait);
        }

        return traitResults;
    }

    public List<Trait> getTraitsById(UUID ...traitIds){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        Result<Record> recordResult = getTraitSql(createdByUser, updatedByUser)
                .and(TRAIT.ID.in(traitIds))
                .fetch();

        List<Trait> traitResults = new ArrayList<>();
        for (Record record: recordResult) {
            Trait trait = parseTraitRecord(record, createdByUser, updatedByUser);
            traitResults.add(trait);
        }

        return traitResults;
    }

    public Optional<Trait> getTraitFull(UUID programId, UUID traitId){

        Optional<Trait> optionalDbTrait = getTrait(programId, traitId);
        if (!optionalDbTrait.isPresent()){
            return Optional.empty();
        }
        Trait dbTrait = optionalDbTrait.get();

        List<BrApiVariable> brApiVariables;

        VariablesRequest variablesRequest = VariablesRequest.builder()
                .externalReferenceID(traitId.toString())
                .externalReferenceSource(referenceSource)
                .build();
        try {
            brApiVariables = brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO).getVariables(variablesRequest);
        } catch (HttpException | APIException e) {
            // If variable is not found, is still a server exception
            throw new InternalServerException(e.getMessage());
        }

        BrApiVariable brApiVariable;
        if (brApiVariables.size() > 0){
            brApiVariable = brApiVariables.get(0);
        } else {
            throw new InternalServerException("No variable found in brapi server");
        }

        dbTrait.setBrAPIProperties(brApiVariable);
        Method method = dbTrait.getMethod();
        method.setBrAPIProperties(brApiVariable.getMethod());
        dbTrait.setMethod(method);

        Scale scale = dbTrait.getScale();
        scale.setBrAPIProperties(brApiVariable.getScale());
        dbTrait.setScale(scale);

        return Optional.of(dbTrait);
    }

    public Optional<Trait> getTrait(UUID programId, UUID traitId) {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        Record record = getTraitSql(createdByUser, updatedByUser)
                .where(PROGRAM_ONTOLOGY.PROGRAM_ID.eq(programId))
                .and(TRAIT.ID.eq(traitId))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(parseTraitRecord(record, createdByUser, updatedByUser));
    }

    public List<Trait> createTraitsBrAPI(List<Trait> traits, User actingUser, Program program){

        //TODO: Pass ontology reference

        // Convert our traits into BrAPI traits
        List<BrApiVariable> brApiVariables = new ArrayList<>();
        for (Trait trait: traits) {

            // Construct method
            BrApiExternalReference methodReference = BrApiExternalReference.builder()
                    .referenceID(trait.getMethod().getId().toString())
                    .referenceSource(referenceSource)
                    .build();
            BrApiMethod brApiMethod = BrApiMethod.builder()
                    .methodName(String.format("%s %s", trait.getTraitName(), trait.getMethod().getMethodClass()))
                    .externalReferences(List.of(methodReference))
                    .methodClass(trait.getMethod().getMethodClass())
                    .description(trait.getMethod().getDescription())
                    .formula(trait.getMethod().getFormula())
                    .build();

            // Construct scale
            BrApiExternalReference scaleReference = BrApiExternalReference.builder()
                    .referenceID(trait.getScale().getId().toString())
                    .referenceSource(referenceSource)
                    .build();
            BrApiTraitDataType brApiTraitDataType = BrApiTraitDataType.valueOf(trait.getScale().getDataType().toString());
            BrApiScaleValidValues brApiScaleValidValues = BrApiScaleValidValues.builder()
                    .categories(trait.getScale().getCategories())
                    .max(trait.getScale().getValidValueMax())
                    .min(trait.getScale().getValidValueMin())
                    .build();
            BrApiScale brApiScale = BrApiScale.builder()
                    .scaleName(trait.getScale().getScaleName())
                    .externalReferences(List.of(scaleReference))
                    .dataType(brApiTraitDataType)
                    .decimalPlaces(trait.getScale().getDecimalPlaces())
                    .validValues(brApiScaleValidValues)
                    .build();

            // Construct trait
            BrApiExternalReference traitReference = BrApiExternalReference.builder()
                    .referenceID(trait.getId().toString())
                    .referenceSource(referenceSource)
                    .build();
            BrApiTrait brApiTrait = BrApiTrait.builder()
                    .traitName(trait.getTraitName())
                    .traitDescription(trait.getMethod().getDescription())
                    .synonyms(trait.getSynonyms())
                    .status("active")
                    .entity(trait.getProgramObservationLevel().getName())
                    .mainAbbreviation(trait.getMainAbbreviation())
                    .alternativeAbbreviations(trait.getAbbreviations() != null ? List.of(trait.getAbbreviations()) : null)
                    .traitClass(trait.getTraitClass())
                    .externalReferences(List.of(traitReference))
                    .attribute(trait.getAttribute())
                    .build();

            BrApiExternalReference variableReference = BrApiExternalReference.builder()
                    .referenceID(trait.getId().toString())
                    .referenceSource(referenceSource)
                    .build();
            BrApiVariable brApiVariable = BrApiVariable.builder()
                    .method(brApiMethod)
                    .scale(brApiScale)
                    .trait(brApiTrait)
                    .externalReferences(List.of(variableReference))
                    .observationVariableName(trait.getTraitName())
                    .status("active")
                    .language("english")
                    .scientist(actingUser.getName())
                    .defaultValue(trait.getDefaultValue())
                    .synonyms(trait.getSynonyms())
                    .institution(program.getName())
                    .commonCropName(program.getSpecies().getCommonName())
                    .build();

                    // Unused
                    //.contextOfUse()
                    //.documentationURL()
                    //.growthStage()

            brApiVariables.add(brApiVariable);
        }


        // POST variables to each brapi service
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        List<BrApiVariable> createdVariables = new ArrayList<>();
        try {
            List<VariablesAPI> variablesAPIS = brAPIProvider.getAllUniqueVariablesAPI();
            for (VariablesAPI variablesAPI: variablesAPIS){
                createdVariables = variablesAPI.createVariables(brApiVariables);
            }
        } catch (HttpException | APIException e) {
            throw new InternalServerException(e.getMessage());
        }

        // Pull our traits from the db
        List<UUID> traitIds = traits.stream().map(trait -> trait.getId()).collect(Collectors.toList());
        List<Trait> createdTraits = getTraitsById(traitIds.toArray(UUID[]::new));

        // Saturate our traits from the brapi return information
        for (Trait trait: createdTraits){
            for (BrApiVariable variable: createdVariables){
                if (variable.getExternalReferences() != null) {
                    for (BrApiExternalReference brApiExternalReference: variable.getExternalReferences()){
                        if (brApiExternalReference.getReferenceSource().equals(referenceSource) &&
                                brApiExternalReference.getReferenceID().equals(trait.getId().toString())){

                            trait.setBrAPIProperties(variable);
                            Method method = trait.getMethod();
                            method.setBrAPIProperties(variable.getMethod());
                            Scale scale = trait.getScale();
                            scale.setBrAPIProperties(variable.getScale());
                        }
                    }
                }
            }
        }

        return createdTraits;
    }

    private Trait parseTraitRecord(Record record, BiUserTable createdByUser, BiUserTable updatedByUser) {
        Trait trait = Trait.parseSqlRecord(record);
        Scale scale = Scale.parseSqlRecord(record);
        Method method = Method.parseSqlRecord(record);
        ProgramOntology programOntology = ProgramOntology.parseSqlRecord(record);
        ProgramObservationLevel programObservationLevel = ProgramObservationLevel.parseSqlRecord(record);
        User createUser = User.parseSQLRecord(record, createdByUser);
        User updateUser = User.parseSQLRecord(record, updatedByUser);

        trait.setScale(scale);
        trait.setMethod(method);
        trait.setProgramOntology(programOntology);
        trait.setProgramObservationLevel(programObservationLevel);
        trait.setCreatedByUser(createUser);
        trait.setUpdatedByUser(updateUser);

        return trait;
    }

    private SelectOnConditionStep<Record> getTraitSql(BiUserTable createdByTableAlias, BiUserTable updatedByTableAlias) {
        return dsl.select()
                .from(TRAIT)
                .join(METHOD).on(TRAIT.METHOD_ID.eq(METHOD.ID))
                .join(SCALE).on(TRAIT.SCALE_ID.eq(SCALE.ID))
                .join(PROGRAM_OBSERVATION_LEVEL).on(TRAIT.PROGRAM_OBSERVATION_LEVEL_ID.eq(PROGRAM_OBSERVATION_LEVEL.ID))
                .join(PROGRAM_ONTOLOGY).on(TRAIT.PROGRAM_ONTOLOGY_ID.eq(PROGRAM_ONTOLOGY.ID))
                .join(createdByTableAlias).on(TRAIT.CREATED_BY.eq(createdByTableAlias.ID))
                .join(updatedByTableAlias).on(TRAIT.UPDATED_BY.eq(updatedByTableAlias.ID));
    }

    public List<Trait> getTraitsByTraitName(UUID programId, List<Trait> traits){

        RowN[] valueRows = traits.stream()
                .filter(trait -> trait.getTraitName() != null)
                .map(trait -> (RowN) row(trait.getTraitName()))
                .collect(Collectors.toList()).toArray(RowN[]::new);

        List<Trait> traitResults = new ArrayList<>();
        if (valueRows.length > 0){
            Table newTraits = dsl.select()
                    .from(values(valueRows).as("newTraits", "new_trait_name")).asTable("newTraits");

            Result<Record> records = dsl.select()
                    .from(newTraits)
                    .join(TRAIT).on(TRAIT.TRAIT_NAME.likeIgnoreCase(newTraits.field("new_trait_name")))
                    .join(PROGRAM_ONTOLOGY).on(TRAIT.PROGRAM_ONTOLOGY_ID.eq(PROGRAM_ONTOLOGY.ID))
                    .join(PROGRAM).on(PROGRAM_ONTOLOGY.PROGRAM_ID.eq(PROGRAM.ID))
                    .join(SCALE).on(TRAIT.SCALE_ID.eq(SCALE.ID))
                    .join(METHOD).on(TRAIT.METHOD_ID.eq(METHOD.ID))
                    .where(PROGRAM.ID.eq(programId))
                    .fetch();

            for (Record record: records) {
                Trait trait = Trait.parseSqlRecord(record);
                Scale scale = Scale.parseSqlRecord(record);
                Method method = Method.parseSqlRecord(record);
                trait.setScale(scale);
                trait.setMethod(method);
                traitResults.add(trait);
            }
        }

        return traitResults;
    }

    public List<Trait> getTraitsByAbbreviation(UUID programId, List<String> abbreviations) {

        Result<Record> records = dsl.select()
                .from(TRAIT)
                .join(PROGRAM_ONTOLOGY).on(TRAIT.PROGRAM_ONTOLOGY_ID.eq(PROGRAM_ONTOLOGY.ID))
                .join(PROGRAM).on(PROGRAM_ONTOLOGY.PROGRAM_ID.eq(PROGRAM.ID))
                .where(TRAIT.ABBREVIATIONS.cast(String[].class).contains(abbreviations.toArray(String[]::new)))
                .and(PROGRAM.ID.eq(programId))
                .fetch();

        List<Trait> traitResults = new ArrayList<>();
        for (Record record: records) {
            Trait trait = Trait.parseSqlRecord(record);
            traitResults.add(trait);
        }

        return traitResults;
    }

}
