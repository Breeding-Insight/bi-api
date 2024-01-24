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

package org.breedinginsight.daos.impl;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.*;
import org.brapi.v2.model.pheno.request.BrAPIObservationVariableSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableSingleResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.TraitDao;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.daos.ObservationDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.*;
import org.breedinginsight.model.User;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jooq.*;
import org.jooq.tools.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.breedinginsight.dao.db.Tables.*;
import static org.jooq.impl.DSL.lower;

@Singleton
@Slf4j
public class TraitDAOImpl extends TraitDao implements TraitDAO {

    private final DSLContext dsl;
    private final BrAPIProvider brAPIProvider;
    private final String referenceSource;
    private final Boolean runScheduledTasks;
    private final ObservationDAO observationDao;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final ProgramCache<Trait> cache;
    private final ProgramDAO programDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final Gson gson;

    private final static String TAGS_KEY = "tags";
    private final static String FULLNAME_KEY = "fullname";

    @Inject
    public TraitDAOImpl(Configuration config,
                        DSLContext dsl,
                        BrAPIProvider brAPIProvider,
                        ObservationDAO observationDao,
                        BrAPIDAOUtil brAPIDAOUtil,
                        ProgramDAO programDAO,
                        ProgramCacheProvider programCacheProvider,
                        BrAPIEndpointProvider brAPIEndpointProvider,
                        @Property(name = "brapi.server.reference-source") String referenceSource,
                        @Property(name = "micronaut.bi.api.run-scheduled-tasks") Boolean runScheduledTasks) {
        super(config);
        this.dsl = dsl;
        this.brAPIProvider = brAPIProvider;
        this.observationDao = observationDao;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.cache = programCacheProvider.getProgramCache(this::populateCache, Trait.class);
        this.programDAO = programDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                                                                 (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                                                         .registerTypeAdapterFactory(new GeometryAdapterFactory())
                                                         .create();

        this.referenceSource = referenceSource;
        this.runScheduledTasks = runScheduledTasks;
    }

    @Scheduled(initialDelay = "2s")
    public void setup() {
        if(!runScheduledTasks) {
            return;
        }
        // Populate trait cache for all programs on startup
        log.debug("Populate traits cache");
        List<Program> programs = programDAO.getActive();
        if(programs != null) {
            cache.populate(programs.stream().map(Program::getId).collect(Collectors.toList()));
        }
    }

    private Map<String, Trait> populateCache(UUID programId) {
        List<Trait> programTraits = fetchTraitsFullByProgramId(programId);
        Map<String, Trait> traits = new HashMap<>();
        if (!programTraits.isEmpty()) {
            programTraits.forEach(trait -> traits.put(trait.getId().toString(), trait));
        }

        return traits;
    }

    @Override
    public List<Trait> getTraitsFullByProgramId(UUID programId) {
        List<UUID> programIds = new ArrayList<>();
        programIds.add(programId);
        return getTraitsFullByProgramIds(programIds);
    }

    @Override
    public List<Trait> getTraitsFullByProgramIds(List<UUID> programIds) {
        List<Trait> saturatedTraits = new ArrayList<>();
        for(UUID id : programIds) {
            Map<String, Trait> programTraits = null;
            try {
                programTraits = cache.get(id);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
            if(programTraits != null) {
                saturatedTraits.addAll(programTraits.values());
            }
        }

        return saturatedTraits;
    }

    private List<Trait> fetchTraitsFullByProgramId(UUID programId) {
        List<Trait> saturatedTraits = new ArrayList<>();
        // Get our db traits (equivalent to brapi variables)
        List<Trait> programTraits = getTraitsByProgramIds(programId);
        if (programTraits.size() == 0) {
            return new ArrayList<>();
        }

        // Get brapi variables
        VariableQueryParams variablesRequest = new VariableQueryParams();
        variablesRequest.externalReferenceSource(referenceSource);
        variablesRequest.pageSize(10000);

        ApiResponse<BrAPIObservationVariableListResponse> brApiVariables;
        try {
            brApiVariables = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationVariablesApi.class)
                                          .variablesGet(variablesRequest);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        Map<String, BrAPIObservationVariable> brApiVariableMap = new HashMap<>();
        for (BrAPIObservationVariable brApiVariable : brApiVariables.getBody()
                                                                    .getResult()
                                                                    .getData()) {
            List<BrAPIExternalReference> brApiExternalReferences = brApiVariable.getExternalReferences();
            for (BrAPIExternalReference brApiExternalReference : brApiExternalReferences) {
                if (brApiExternalReference.getReferenceID() != null) {
                    brApiVariableMap.put(brApiExternalReference.getReferenceID(), brApiVariable);
                }
            }
        }

        for (Trait trait : programTraits) {
            // assumes external reference id is unique to each brapi variable
            if (brApiVariableMap.containsKey(trait.getId()
                                                  .toString())) {
                BrAPIObservationVariable brApiVariable = brApiVariableMap.get(trait.getId()
                                                                                   .toString());
                saturateTrait(trait, brApiVariable);
                saturatedTraits.add(trait);
            } else {
                throw new InternalServerException("Could not find trait in returned brapi server results");
            }
        }

        return saturatedTraits;
    }

    @Override
    public List<Trait> getTraitsByProgramId(UUID programId) {
        return getTraitsByProgramIds(programId);
    }

    @Override
    public List<Trait> getTraitsByProgramIds(UUID... programIds) {

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

    @Override
    public List<Trait> getTraitsById(UUID... traitIds){

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

    @Override
    public Optional<Trait> getTraitFull(UUID programId, UUID traitId){

        try {
            return Optional.ofNullable(cache.get(programId).get(traitId.toString()));
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }
    }

    // could be more efficient to do a single get instead of search in saved search case but less code this way
    // and search stuff is working in breedbase
    @Override
    public List<BrAPIObservation> getObservationsForTrait(UUID traitId, UUID programId) {
        return getObservationsForTraits(Stream.of(traitId).collect(Collectors.toList()), programId);
    }

    @Override
    public List<BrAPIObservation> getObservationsForTraits(List<UUID> traitIds, UUID programId) {

        List<String> ids = traitIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        List<BrAPIObservationVariable> variables = searchVariables(ids, programId);

        // TODO: make sure have all expected external references
        if (variables.size() != ids.size()) {
            throw new InternalServerException("Observation variables search results mismatch");
        }

        List<String> brapiVariableIds = variables.stream()
                                                 .map(BrAPIObservationVariable::getObservationVariableDbId).collect(Collectors.toList());

        return observationDao.getObservationsByVariableDbIds(brapiVariableIds, programId);
    }

    @Override
    public List<BrAPIObservation> getObservationsForTraitsByBrAPIProgram(String brapiProgramId, UUID programId, List<UUID> traitIds) {

        List<String> ids = traitIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        List<BrAPIObservationVariable> variables = searchVariables(ids, programId);

        // TODO: make sure have all expected external references
        if (variables.size() != ids.size()) {
            throw new InternalServerException("Observation variables search results mismatch");
        }

        List<String> brapiVariableIds = variables.stream()
                                                 .map(BrAPIObservationVariable::getObservationVariableDbId).collect(Collectors.toList());

        return observationDao.getObservationsByVariableAndBrAPIProgram(brapiProgramId, programId, brapiVariableIds);
    }

    @Override
    public List<BrAPIObservationVariable> searchVariables(List<String> variableIds, UUID programId) {

        if (variableIds == null || variableIds.size() == 0) {
            return Collections.emptyList();
        }
        try {
            BrAPIObservationVariableSearchRequest request = new BrAPIObservationVariableSearchRequest()
                    .externalReferenceIDs(variableIds);

            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationVariablesApi api = brAPIEndpointProvider.get(client, ObservationVariablesApi.class);

            return brAPIDAOUtil.search(
                    api::searchVariablesPost,
                    api::searchVariablesSearchResultsDbIdGet,
                    request
            );
        } catch (ApiException e) {
            throw new InternalServerException("Observation variables brapi search error", e);
        }
    }

    @Override
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

    @Override
    public List<Trait> createTraitsBrAPI(List<Trait> traits, User actingUser, Program program){

        //TODO: Pass ontology reference

        // Convert our traits into BrAPI traits
        List<BrAPIObservationVariable> brApiVariables = new ArrayList<>();
        for (Trait trait: traits) {

            // Construct method
            BrAPIExternalReference methodReference = new BrAPIExternalReference()
                    .referenceID(trait.getMethod().getId().toString())
                    .referenceSource(referenceSource);
            BrAPIMethod brApiMethod = new BrAPIMethod()
                                                 .methodName(constructMethodName(trait, program))
                                                 .externalReferences(List.of(methodReference))
                                                 .methodClass(trait.getMethod().getMethodClass())
                                                 .description(trait.getMethod().getDescription())
                                                 .formula(trait.getMethod().getFormula());

            // Construct scale
            BrAPIExternalReference scaleReference = new BrAPIExternalReference()
                    .referenceID(trait.getScale().getId().toString())
                    .referenceSource(referenceSource);
            BrAPITraitDataType brApiTraitDataType = BrAPITraitDataType.valueOf(trait.getScale().getDataType().toString());
            BrAPIScaleValidValues brApiScaleValidValues = new BrAPIScaleValidValues()
                                                                               .categories(trait.getScale().getCategories())
                                                                               .max(trait.getScale().getValidValueMax())
                                                                               .min(trait.getScale().getValidValueMin());
            BrAPIScale brApiScale = new BrAPIScale()
                    .scaleName(String.format("%s [%s]", trait.getScale().getScaleName(), program.getKey()))
                    .externalReferences(List.of(scaleReference))
                    .dataType(brApiTraitDataType)
                    .decimalPlaces(trait.getScale().getDecimalPlaces())
                    .validValues(brApiScaleValidValues);

            // Construct trait
            BrAPIExternalReference traitReference = new BrAPIExternalReference()
                    .referenceID(trait.getId().toString())
                    .referenceSource(referenceSource);
            BrAPITrait brApiTrait = new BrAPITrait()
                    .traitName(String.format("%s %s [%s]", trait.getEntity(), trait.getAttribute(), program.getKey()))
                    .traitDescription(trait.getTraitDescription())
                    .synonyms(trait.getSynonyms())
                    .status("active")
                    .entity(trait.getEntity())
                    .mainAbbreviation(trait.getMainAbbreviation())
                    .traitClass(trait.getTraitClass())
                    .externalReferences(List.of(traitReference))
                    .attribute(trait.getAttribute());

            BrAPIExternalReference variableReference = new BrAPIExternalReference()
                    .referenceID(trait.getId().toString())
                    .referenceSource(referenceSource);
            BrAPIExternalReference programReference = new BrAPIExternalReference()
                    .referenceID(program.getId().toString())
                    .referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS));
            BrAPIObservationVariable brApiVariable = new BrAPIObservationVariable()
                    .method(brApiMethod)
                    .scale(brApiScale)
                    .trait(brApiTrait)
                    .externalReferences(List.of(variableReference, programReference))
                    .observationVariableName(String.format("%s [%s]", trait.getObservationVariableName(), program.getKey()))
                    .status("active")
                    .language("english")
                    .scientist(actingUser.getName())
                    .defaultValue(trait.getDefaultValue())
                    .synonyms(trait.getSynonyms())
                    .institution(program.getName())
                    .commonCropName(program.getSpecies().getCommonName());
            if (trait.getTags() != null) brApiVariable.putAdditionalInfoItem(TAGS_KEY, trait.getTags());
            if (trait.getFullName() != null) brApiVariable.putAdditionalInfoItem(FULLNAME_KEY, trait.getFullName());

            if (trait.getActive() == null || trait.getActive()){
                brApiVariable.setStatus("active");
            } else {
                brApiVariable.setStatus("archived");
            }


                    // Unused
                    //.contextOfUse()
                    //.documentationURL()
                    //.growthStage()

            brApiVariables.add(brApiVariable);
        }


        // POST variables to each brapi service
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        ApiResponse<BrAPIObservationVariableListResponse> createdVariables = null;
        try {
            ObservationVariablesApi variablesAPI = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationVariablesApi.class);
            createdVariables = variablesAPI.variablesPost(brApiVariables);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        if(createdVariables == null) {
            throw new InternalServerException("Creating new variable did not return any data");
        }

        // Pull our traits from the db
        List<UUID> traitIds = traits.stream().map(TraitEntity::getId).collect(Collectors.toList());
        List<Trait> createdTraits = getTraitsById(traitIds.toArray(UUID[]::new));

        // Saturate our traits from the brapi return information
        for (Trait trait: createdTraits){
            for (BrAPIObservationVariable variable: createdVariables.getBody().getResult().getData()){
                if (variable.getExternalReferences() != null) {
                    for (BrAPIExternalReference brApiExternalReference: variable.getExternalReferences()){
                        if (brApiExternalReference.getReferenceSource().equals(referenceSource) &&
                                brApiExternalReference.getReferenceID().equals(trait.getId().toString())){
                            saturateTrait(trait, variable);

                            cache.set(program.getId(), trait.getId().toString(), trait);
                        }
                    }
                }
            }
        }

        return createdTraits;
    }

    @Override
    public Trait updateTraitBrAPI(Trait trait, Program program) {
        //TODO: Need to roll back somehow if there is an error
        Trait updatedTrait = null;
        List<ObservationVariablesApi> variablesAPIS = brAPIProvider.getAllUniqueVariablesAPI();
        for (ObservationVariablesApi variablesAPI: variablesAPIS){
            // GET brapi trait
            BrAPIObservationVariable existingVariable = getBrAPIVariable(variablesAPI, trait.getId());

            // Change method
            existingVariable.getMethod().setMethodName(constructMethodName(trait, program));
            existingVariable.getMethod().setMethodClass(trait.getMethod().getMethodClass());
            existingVariable.getMethod().setDescription(trait.getMethod().getDescription());
            existingVariable.getMethod().setFormula(trait.getMethod().getFormula());

            // Change scale
            BrAPITraitDataType brApiTraitDataType = BrAPITraitDataType.valueOf(trait.getScale().getDataType().toString());
            existingVariable.getScale().setScaleName(String.format("%s [%s]", trait.getScale().getScaleName(), program.getKey()));
            existingVariable.getScale().setDataType(brApiTraitDataType);
            existingVariable.getScale().setDecimalPlaces(trait.getScale().getDecimalPlaces());
            BrAPIScaleValidValues brApiScaleValidValues = new BrAPIScaleValidValues()
                    .categories(trait.getScale().getCategories())
                    .max(trait.getScale().getValidValueMax())
                    .min(trait.getScale().getValidValueMin());
            existingVariable.getScale().setValidValues(brApiScaleValidValues);

            // Change trait
            existingVariable.getTrait().setTraitName(String.format("%s %s [%s]", trait.getEntity(), trait.getAttribute(), program.getKey()));
            existingVariable.getTrait().setTraitDescription(trait.getTraitDescription());
            existingVariable.getTrait().setSynonyms(trait.getSynonyms());
            existingVariable.getTrait().setEntity(trait.getProgramObservationLevel().getName());
            existingVariable.getTrait().setMainAbbreviation(trait.getMainAbbreviation());
            existingVariable.getTrait().setTraitClass(trait.getTraitClass());
            existingVariable.getTrait().setAttribute(trait.getAttribute());

            // Change variable
            existingVariable.setObservationVariableName(String.format("%s [%s]", trait.getObservationVariableName(), program.getKey()));
            existingVariable.setDefaultValue(trait.getDefaultValue());
            existingVariable.setSynonyms(trait.getSynonyms());
            if (trait.getActive() == null || trait.getActive()){
                existingVariable.setStatus("active");
            } else {
                existingVariable.setStatus("archived");
            }
            existingVariable.putAdditionalInfoItem(TAGS_KEY, trait.getTags());
            if (trait.getFullName() != null) existingVariable.putAdditionalInfoItem(FULLNAME_KEY, trait.getFullName());

            // PUT brapi trait
            BrAPIObservationVariable updatedVariable = putBrAPIVariable(variablesAPI, existingVariable);

            // Retrieve our update trait from the db
            updatedTrait = getTrait(program.getId(), trait.getId()).get();
            saturateTrait(updatedTrait, updatedVariable);

            //update cache
            cache.set(program.getId(), updatedTrait.getId().toString(), updatedTrait);
        }

        return updatedTrait;
    }

    private String constructMethodName(Trait trait, Program program) {
        return !StringUtils.isBlank(trait.getMethod().getDescription()) ?
                String.format("%s %s [%s]", trait.getMethod().getDescription(), trait.getMethod().getMethodClass(), program.getKey()) :
                String.format("%s [%s]", trait.getMethod().getMethodClass(), program.getKey());
    }

    private BrAPIObservationVariable getBrAPIVariable(ObservationVariablesApi variablesAPI, UUID traitId) {

        BrAPIObservationVariable existingVariable;
        try {
            VariableQueryParams queryParams = new VariableQueryParams();
            queryParams.externalReferenceID(traitId.toString());
            queryParams.externalReferenceSource(referenceSource);
            ApiResponse<BrAPIObservationVariableListResponse> existingVariableResponse =
                    variablesAPI.variablesGet(queryParams);
            List<BrAPIObservationVariable> variableList = existingVariableResponse.getBody().getResult().getData();
            if (variableList.size() == 1) {
                existingVariable = variableList.get(0);
            } else {
                throw new InternalServerException(String.format("Unable to find variable with id %s in brapi server.", traitId.toString()));
            }

        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException(String.format("Unable to retrieve variable with id %s in brapi server.", traitId.toString()));
        }

        return existingVariable;
    }

    private BrAPIObservationVariable putBrAPIVariable(ObservationVariablesApi variablesAPI, BrAPIObservationVariable variable) {

        BrAPIObservationVariable updatedVariable;
        try {
            ApiResponse<BrAPIObservationVariableSingleResponse> updatedResponse =
                    variablesAPI.variablesObservationVariableDbIdPut(variable.getObservationVariableDbId(), variable);
            updatedVariable = updatedResponse.getBody().getResult();
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Unable to save variable in brapi server.");
        }
        return updatedVariable;
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

    @Override
    public List<Trait> getTraitsByTraitName(UUID programId, List<Trait> traits){

        List<String> names = traits.stream()
                .filter(trait -> trait.getObservationVariableName() != null)
                .map(trait -> trait.getObservationVariableName().toLowerCase())
                .collect(Collectors.toList());

        List<Trait> traitResults = new ArrayList<>();
        if (!names.isEmpty()) {
            traitResults = getTraitsFullByProgramId(programId).stream().filter(trait -> names.contains(trait.getObservationVariableName().toLowerCase())).collect(Collectors.toList());
        }

        return traitResults;
    }

    @Override
    public List<Trait> getTraitsByAbbreviation(UUID programId, List<String> abbreviations) {

        Result<Record> records = dsl.select()
                .from(TRAIT)
                .join(PROGRAM_ONTOLOGY).on(TRAIT.PROGRAM_ONTOLOGY_ID.eq(PROGRAM_ONTOLOGY.ID))
                .join(PROGRAM).on(PROGRAM_ONTOLOGY.PROGRAM_ID.eq(PROGRAM.ID))
                .and(PROGRAM.ID.eq(programId))
                .fetch();

        List<Trait> traitResults = new ArrayList<>();
        for (Record record: records) {
            Trait trait = Trait.parseSqlRecord(record);
            traitResults.add(trait);
        }

        return traitResults;
    }

    @Override
    public TraitEntity fetchOneById(UUID id) {
        return super.fetchOne(TRAIT.ID, id);
    }

    @Override
    public List<TraitEntity> fetchById(UUID traitId) {
        return super.fetch(TRAIT.ID, traitId);
    }

    private void saturateTrait(Trait trait, BrAPIObservationVariable brApiVariable) {

        if (brApiVariable.getAdditionalInfo() != null) {
            List<String> tags = null;
            String fullName = null;
            if (brApiVariable.getAdditionalInfo().has(TAGS_KEY) && !brApiVariable.getAdditionalInfo().get(TAGS_KEY).isJsonNull()) {
                tags = gson.fromJson(brApiVariable.getAdditionalInfo().getAsJsonArray(TAGS_KEY), List.class);
            }
            if (brApiVariable.getAdditionalInfo().has(FULLNAME_KEY) && !brApiVariable.getAdditionalInfo().get(FULLNAME_KEY).isJsonNull()) {
                fullName = brApiVariable.getAdditionalInfo().get(FULLNAME_KEY).getAsString();
            }
            trait.setBrAPIProperties(brApiVariable, tags, fullName);
        } else {
            trait.setBrAPIProperties(brApiVariable);
        }

        Method method = trait.getMethod();
        method.setBrAPIProperties(brApiVariable.getMethod());

        Scale scale = trait.getScale();
        scale.setBrAPIProperties(brApiVariable.getScale());
    }
}
