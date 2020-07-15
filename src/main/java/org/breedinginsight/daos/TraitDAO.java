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
import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.phenotyping.model.BrApiVariable;
import org.brapi.v2.phenotyping.model.request.VariablesRequest;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.TraitDao;
import org.breedinginsight.model.*;
import org.breedinginsight.model.User;
import org.jooq.*;
import org.jooq.impl.SQLDataType;

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
    @Inject
    private BrAPIProvider brAPIProvider;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Inject
    public TraitDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<Trait> getTraitsFullByProgramId(UUID programId) {

        // Get our db traits (equivalent to brapi variables)
        List<Trait> dbVariables = getTraitsByProgramId(programId);
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

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        Result<Record> recordResult = getTraitSql(createdByUser, updatedByUser)
                .where(PROGRAM_ONTOLOGY.PROGRAM_ID.eq(programId))
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
        //TODO: We might want to add a source too
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

    public List<Trait> createTraitsBrAPI(UUID ontologyId, List<Trait> traits){

        //TODO: Insert traits into brapi
        //TODO: Convert brapi trait into our traits
        return new ArrayList<>();
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

    public List<Trait> getTraitsByTraitMethodScaleName(List<Trait> traits){

        //TODO: Get these from the query as well
        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        RowN[] valueRows = traits.stream()
                .filter(trait -> trait.getMethod() != null && trait.getScale() != null)
                .map(trait -> (RowN) row(trait.getTraitName(), trait.getMethod().getMethodName(), trait.getScale().getScaleName()))
                .collect(Collectors.toList()).toArray(RowN[]::new);

        List<Trait> traitResults = new ArrayList<>();
        if (valueRows.length > 0){
            Table newTraits = dsl.select()
                    .from(values(valueRows).as("newTraits", "new_trait_name", "new_method_name", "new_scale_name")).asTable("newTraits");

            Result<Record> records = dsl.select()
                    .from(newTraits)
                    .join(TRAIT).on(TRAIT.TRAIT_NAME.like(newTraits.field("new_trait_name")))
                    .join(METHOD).on(TRAIT.METHOD_ID.eq(METHOD.ID)).and(METHOD.METHOD_NAME.like(newTraits.field("new_method_name")))
                    .join(SCALE).on(TRAIT.SCALE_ID.eq(SCALE.ID)).and(SCALE.SCALE_NAME.like(newTraits.field("new_scale_name")))
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

    public List<Trait> getTraitsByAbbreviation(List<String> abbreviations) {

        Result<Record> records = dsl.select()
                .from(TRAIT).where(TRAIT.ABBREVIATIONS.cast(String[].class).contains(abbreviations.toArray(String[]::new)))
                .fetch();

        List<Trait> traitResults = new ArrayList<>();
        for (Record record: records) {
            Trait trait = Trait.parseSqlRecord(record);
            traitResults.add(trait);
        }

        return traitResults;
    }

}
