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

package org.breedinginsight.brapi.v2.dao;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationTreatment;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIObservationUnitDAO {
    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramService programService;
    private final BrAPIGermplasmService germplasmService;

    private final String referenceSource;
    private boolean runScheduledTasks;

    private final Gson gson = new JSON().getGson();
    private final Type treatmentlistType = new TypeToken<ArrayList<BrAPIObservationTreatment>>(){}.getType();

    private final ProgramCache<BrAPIObservationUnit> programObservationUnitCache;

    @Inject
    public BrAPIObservationUnitDAO(ProgramDAO programDAO,
                                   ImportDAO importDAO,
                                   BrAPIDAOUtil brAPIDAOUtil,
                                   BrAPIEndpointProvider brAPIEndpointProvider,
                                   BrAPIGermplasmService germplasmService,
                                   ProgramService programService,
                                   @Property(name = "brapi.server.reference-source") String referenceSource,
                                   @Property(name = "micronaut.bi.api.run-scheduled-tasks") boolean runScheduledTasks,
                                   ProgramCacheProvider programCacheProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
        this.runScheduledTasks = runScheduledTasks;
        this.programService = programService;
        this.germplasmService = germplasmService;
        this.programObservationUnitCache = programCacheProvider.getProgramCache(this::fetchProgramObservationUnits, BrAPIObservationUnit.class);
    }

    @Scheduled(initialDelay = "2s")
    public void setup() {
        if(!runScheduledTasks) {
            return;
        }
        // Populate observation unit cache for all programs on startup.
        log.debug("populating observation unit cache");
        List<Program> programs = programDAO.getActive();
        if(programs != null) {
            programObservationUnitCache.populate(programs.stream().map(Program::getId).collect(Collectors.toList()));
        }
    }

    /**
     * Fetch formatted observation units for this program.
     * @param programId
     * @return Map<Key = string representing observation unit UUID, value = formatted BrAPIObservationUnit>
     * @throws ApiException
     */
    private Map<String, BrAPIObservationUnit> fetchProgramObservationUnits(UUID programId) throws ApiException {
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        // Get the program key.
        List<Program> programs = programDAO.get(programId);
        if (programs.size() != 1) {
            throw new InternalServerException("Program was not found for given key");
        }
        Program program = programs.get(0);

        // Set query params and make call.
        BrAPIObservationUnitSearchRequest observationUnitSearch = new BrAPIObservationUnitSearchRequest();
        observationUnitSearch.externalReferenceIds(List.of(programId.toString()));
        observationUnitSearch.externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS)));
        return processObservationUnitsForCache(brAPIDAOUtil.search(
                api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearch
        ), program.getKey());
    }

    // TODO: use program key, strip from observationUnitName, etc.
    /**
     * Process a list of observation units for insertion into the cache.
     * @param programObservationUnits
     * @param programKey
     * @return
     */
    private Map<String, BrAPIObservationUnit> processObservationUnitsForCache(List<BrAPIObservationUnit> programObservationUnits, String programKey) {
        // Build map.
        Map<String, BrAPIObservationUnit> programObservationUnitsMap = new HashMap<>();
        for (BrAPIObservationUnit observationUnit: programObservationUnits) {
            BrAPIExternalReference xref = observationUnit
                .getExternalReferences()
                .stream()
                .filter(reference -> String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS.getName()).equals(reference.getReferenceSource()))
                .findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            programObservationUnitsMap.put(xref.getReferenceID(), observationUnit);
        }
        return programObservationUnitsMap;
    }

    public List<BrAPIObservationUnit> getObservationUnitByName(List<String> observationUnitNames, Program program) throws ApiException {
        if(observationUnitNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationUnitSearchRequest.observationUnitNames(observationUnitNames);

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program, false);
    }

    /**
     * Create observation units, mutates brAPIObservationUnitList
     */
    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList, UUID programId, ImportUpload upload) throws ApiException, DoesNotExistException {
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        preprocessObservationUnits(brAPIObservationUnitList);
        List<BrAPIObservationUnit> ous = brAPIDAOUtil.post(brAPIObservationUnitList, upload, api::observationunitsPost, importDAO::update);
        processObservationUnits(program, ous, false);
        return ous;
    }

    public BrAPIObservationUnit updateBrAPIObservationUnit(String ouDbId, BrAPIObservationUnit ou, UUID programId) {
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        BrAPIObservationUnit updatedOu = null;
        try {
            if (ou != null && !ouDbId.isBlank()) {
                updatedOu = brAPIDAOUtil.put(ouDbId, ou, api::observationunitsObservationUnitDbIdPut);
            }
            return updatedOu;
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    public List<BrAPIObservationUnit> getObservationUnitsById(Collection<String> observationUnitExternalIds, Program program) throws ApiException {
        if(observationUnitExternalIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                                                                 .getProgramDbId()));
        observationUnitSearchRequest.externalReferenceIDs(new ArrayList<>(observationUnitExternalIds));
        observationUnitSearchRequest.externalReferenceSources(List.of(String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS.getName())));

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program, false);
    }

    public List<BrAPIObservationUnit> getObservationUnitsForStudyDbId(@NotNull String studyDbId, Program program) throws ApiException {
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                .getProgramDbId()));
        observationUnitSearchRequest.studyDbIds(List.of(studyDbId));

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program, false);
    }

    public List<BrAPIObservationUnit> getObservationUnitsForTrialDbId(@NotNull UUID programId, @NotNull String trialDbId) throws ApiException, DoesNotExistException {
        return getObservationUnitsForTrialDbId(programId, trialDbId, false);
    }

    public List<BrAPIObservationUnit> getObservationUnitsForTrialDbId(@NotNull UUID programId, @NotNull String trialDbId, boolean withGID) throws ApiException, DoesNotExistException {
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));

        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                .getProgramDbId()));
        observationUnitSearchRequest.trialDbIds(List.of(trialDbId));

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program, withGID);
    }

    public List<BrAPIObservationUnit> getObservationUnitsForDataset(@NotNull String datasetId, @NotNull Program program) throws ApiException {
        String datasetReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.DATASET);
        BrAPIObservationUnitSearchRequest ouSearchRequest = new BrAPIObservationUnitSearchRequest();
        ouSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        ouSearchRequest.externalReferenceSources(List.of(datasetReferenceSource));
        ouSearchRequest.externalReferenceIDs(List.of(datasetId));
        return searchObservationUnitsAndProcess(ouSearchRequest, program, true);
    }

    public List<BrAPIObservationUnit> getObservationUnits(Program program,
                                                          Optional<String> observationUnitId,
                                                          Optional<String> observationUnitName,
                                                          Optional<String> locationDbId,
                                                          Optional<String> seasonDbId,
                                                          Optional<Boolean> includeObservations,
                                                          Optional<String> observationUnitLevelName,
                                                          Optional<Integer> observationUnitLevelOrder,
                                                          Optional<String> observationUnitLevelCode,
                                                          Optional<String> observationUnitLevelRelationshipName,
                                                          Optional<Integer> observationUnitLevelRelationshipOrder,
                                                          Optional<String> observationUnitLevelRelationshipCode,
                                                          Optional<String> observationUnitLevelRelationshipDbId,
                                                          Optional<String> commonCropName,
                                                          Optional<String> experimentId,
                                                          Optional<String> environmentId,
                                                          Optional<String> germplasmId
//                                                          , Integer page,
//                                                          Integer pageSize
    ) throws ApiException {
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.programDbIds(List.of(program.getBrapiProgram()
                                                                 .getProgramDbId()));
        //TODO add pagination support
//                                    .page(page)
//                                    .pageSize(pageSize);

        List<String> xrefIds = new ArrayList<>();
        List<String> xrefSources = new ArrayList<>();
        BrAPIObservationUnitLevelRelationship level = new BrAPIObservationUnitLevelRelationship();
        AtomicBoolean levelFilter = new AtomicBoolean(false);
        BrAPIObservationUnitLevelRelationship relationship = new BrAPIObservationUnitLevelRelationship();
        AtomicBoolean relationshipFilter = new AtomicBoolean(false);

        observationUnitId.ifPresent(ouId -> addXRefFilter(ouId, ExternalReferenceSource.OBSERVATION_UNITS, xrefIds, xrefSources));
        observationUnitName.ifPresent(name -> observationUnitSearchRequest.setObservationUnitNames(List.of(Utilities.appendProgramKey(name, program.getKey()))));
        locationDbId.ifPresent(dbid -> observationUnitSearchRequest.setLocationDbIds(List.of(dbid)));
        seasonDbId.ifPresent(dbid -> observationUnitSearchRequest.setSeasonDbIds(List.of(dbid)));
        includeObservations.ifPresent(observationUnitSearchRequest::includeObservations);
        addLevelFilter(observationUnitLevelName, observationUnitLevelOrder, observationUnitLevelCode, level, levelFilter);
        addLevelFilter(observationUnitLevelRelationshipName, observationUnitLevelRelationshipOrder, observationUnitLevelRelationshipCode, relationship, relationshipFilter);
        experimentId.ifPresent(expId -> addXRefFilter(expId, ExternalReferenceSource.TRIALS, xrefIds, xrefSources));
        environmentId.ifPresent(envId -> addXRefFilter(envId, ExternalReferenceSource.STUDIES, xrefIds, xrefSources));
//        germplasmId.ifPresent(germId -> {
//            xrefIds.add(germId);
//            xrefSources.add(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.));
//        });

        if(!xrefIds.isEmpty()) {
            observationUnitSearchRequest.externalReferenceIDs(xrefIds);
        }
        if(!xrefSources.isEmpty()) {
            observationUnitSearchRequest.externalReferenceSources(xrefSources);
        }

        return searchObservationUnitsAndProcess(observationUnitSearchRequest, program, true).stream().filter(ou -> {
            //xref search does an OR, so we need to convert the searching for ouId/expId/envId to be an AND
            boolean matches = observationUnitId.map(id -> id.equals(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS))
                                                                                 .get()
                                                                                 .getReferenceID()))
                                                   .orElse(true);
            matches = matches && experimentId.map(id -> id.equals(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS))
                                                                                   .get()
                                                                                   .getReferenceID()))
                                                     .orElse(true);
            matches = matches && environmentId.map(id -> id.equals(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES))
                                                                             .get()
                                                                             .getReferenceID()))
                                               .orElse(true);

            //adding filter for germplasmDbId because we can't easily search that in the stored data object
            return matches && germplasmId.map(id -> id.equals(ou.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_UUID).getAsString())).orElse(true);
        }).collect(Collectors.toList());
    }

    private void addXRefFilter(String ouId, ExternalReferenceSource externalReferenceSource, List<String> xrefIds, List<String> xrefSources) {
        xrefIds.add(ouId);
        xrefSources.add(Utilities.generateReferenceSource(referenceSource, externalReferenceSource));
    }

    private void addLevelFilter(Optional<String> observationUnitLevelName, Optional<Integer> observationUnitLevelOrder, Optional<String> observationUnitLevelCode, BrAPIObservationUnitLevelRelationship level, AtomicBoolean levelFilter) {
        observationUnitLevelName.ifPresent(name -> {
            levelFilter.set(true);
            level.setLevelName(name);
        });
        observationUnitLevelOrder.ifPresent(order -> {
            levelFilter.set(true);
            level.setLevelOrder(order);
        });
        observationUnitLevelCode.ifPresent(code -> {
            levelFilter.set(true);
            level.setLevelCode(code);
        });
    }


    /**
     * Perform observation unit search and process returned observation units to handle any modifications to the data
     * to be returned by bi-api
     */
    private List<BrAPIObservationUnit> searchObservationUnitsAndProcess(BrAPIObservationUnitSearchRequest request, Program program, boolean withGID) throws ApiException {

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);
        List<BrAPIObservationUnit> brapiObservationUnits = brAPIDAOUtil.search(api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                request);

        processObservationUnits(program, brapiObservationUnits, withGID);
        return brapiObservationUnits;
    }

    private void processObservationUnits(Program program, List<BrAPIObservationUnit> brapiObservationUnits, boolean withGID) throws ApiException {

    	HashMap<String, BrAPIGermplasm> germplasmByDbId = new HashMap<>();
    	if( withGID ){
            // Load germplasm for program into map.
            // TODO: if we use redis search, that may be more efficient than loading all germplasm for the program.
            this.germplasmService.getGermplasm(program.getId()).forEach((germplasm -> germplasmByDbId.put(germplasm.getGermplasmDbId(), germplasm)));
        }

        // if has treatments in additionalInfo, copy to treatments property
        for (BrAPIObservationUnit ou : brapiObservationUnits) {
            JsonObject additionalInfo = ou.getAdditionalInfo();
            if (additionalInfo != null) {
                JsonElement treatmentsElement = additionalInfo.get(BrAPIAdditionalInfoFields.TREATMENTS);
                if (treatmentsElement != null) {
                    List<BrAPIObservationTreatment> treatments = gson.fromJson(treatmentsElement, treatmentlistType);
                    ou.setTreatments(treatments);
                }
                if( withGID ){
					BrAPIGermplasm germplasm = germplasmByDbId.get(ou.getGermplasmDbId());
                	ou.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GID, germplasm.getAccessionNumber());
                    ou.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_UUID,
                                             Utilities.getExternalReference(germplasm.getExternalReferences(), referenceSource)
                                                      .orElseThrow(() -> new IllegalStateException("Germplasm UUID not found"))
                                                      .getReferenceID());
                }
            }
            ou.setObservationUnitName(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));
            if(StringUtils.isNotBlank(ou.getGermplasmName())) {
                ou.setGermplasmName(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getGermplasmName(), program.getKey()));
            }
            if(StringUtils.isNotBlank(ou.getLocationName())) {
                ou.setLocationName(Utilities.removeProgramKey(ou.getLocationName(), program.getKey()));
            }
            if(StringUtils.isNotBlank(ou.getProgramName())) {
                ou.setProgramName(ou.getProgramName().replaceAll("\\(" + program.getKey() + "\\)", "").trim());
            }
            if(StringUtils.isNotBlank(ou.getTrialName())) {
                ou.setTrialName(Utilities.removeProgramKey(ou.getTrialName(), program.getKey()));
            }
            if(StringUtils.isNotBlank(ou.getStudyName())) {
                ou.setStudyName(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getStudyName(), program.getKey()));
            }
            if (ou.getObservationUnitPosition() != null
                    && ou.getObservationUnitPosition().getObservationLevel() != null
                    && StringUtils.isNotBlank(ou.getObservationUnitPosition().getObservationLevel().getLevelCode())) {
                ou.getObservationUnitPosition()
                        .getObservationLevel()
                        .setLevelCode(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitPosition()
                                .getObservationLevel()
                                .getLevelCode(), program.getKey()));
            }
        }
    }

    private void preprocessObservationUnits(List<BrAPIObservationUnit> brapiObservationUnits) {
        // add treatments to additional info
        for (BrAPIObservationUnit obsUnit : brapiObservationUnits) {
            List<BrAPIObservationTreatment> treatments = obsUnit.getTreatments();
            if (treatments != null) {
                obsUnit.putAdditionalInfoItem(BrAPIAdditionalInfoFields.TREATMENTS, treatments);
            }
        }
    }
}
