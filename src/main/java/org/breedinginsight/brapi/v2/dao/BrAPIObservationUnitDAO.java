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

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
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

    private final int brapiMaxPageSize;

    @Inject
    public BrAPIObservationUnitDAO(ProgramDAO programDAO,
                                   ImportDAO importDAO,
                                   BrAPIDAOUtil brAPIDAOUtil,
                                   BrAPIEndpointProvider brAPIEndpointProvider,
                                   BrAPIGermplasmService germplasmService,
                                   ProgramService programService,
                                   @Property(name = "brapi.server.reference-source") String referenceSource,
                                   @Property(name = "brapi.cache.fetch-page-size") int brapiFetchPageSize) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
        this.programService = programService;
        this.germplasmService = germplasmService;
        this.brapiMaxPageSize = brapiFetchPageSize;
    }

    /**
     * Get all observation units for a program from the cache.
     */
    public List<BrAPIObservationUnit> getProgramObservationUnits(UUID programId) throws ApiException {
        Program program = programDAO.get(programId)
                .stream()
                .findFirst()
                .orElseThrow();

        if (program.getId() == null) {
            throw new InternalServerException("BI-API Program or Program ID is null");
        }

        String brapiProgramDbId = Optional.of(program)
                .map(Program::getBrapiProgram)
                .map(BrAPIProgram::getProgramDbId)
                .orElse(null);

        if (brapiProgramDbId == null) {
            brapiProgramDbId = programDAO.getProgramBrAPI(program).getProgramDbId();
        }

        ObservationUnitQueryParams observationUnitQueryParams = ObservationUnitQueryParams.builder()
                .programDbId(brapiProgramDbId)
                .build();

        return getBrAPIObservationUnitsUsingQueryParams(observationUnitQueryParams, program);
    }

    private List<BrAPIObservationUnit> getBrAPIObservationUnitsUsingQueryParams(ObservationUnitQueryParams observationUnitQueryParams,
                                                                                Program program) throws ApiException {

        if (observationUnitQueryParams.page() == null) {
            observationUnitQueryParams.setPage(0);
        }

        if (observationUnitQueryParams.pageSize() == null) {
            observationUnitQueryParams.setPageSize(brapiMaxPageSize);
        }

        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);

        List<BrAPIObservationUnit> result = brAPIDAOUtil.get(api::observationunitsGet, observationUnitQueryParams);

        processObservationUnits(program, result, true);

        return result;
    }

    private List<BrAPIObservationUnit> searchBrapiObservationUnits(BrAPIObservationUnitSearchRequest observationUnitSearchRequest,
                                                                   Program program) throws ApiException {
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationUnitsApi.class);

        BrAPIObservationUnitListResponse brAPIResponse =
                brAPIDAOUtil.simpleSearch(
                        api::searchObservationunitsPost,
                        observationUnitSearchRequest
                );

        List<BrAPIObservationUnit> observationUnits = brAPIDAOUtil.getListResult(brAPIResponse);
                processObservationUnits(program, observationUnits, false);

        return observationUnits;
    }

    /**
     * Create observation units with import progress.
     * Mutates brAPIObservationUnitList.
     */
    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList, UUID programId, ImportUpload upload) throws ApiException, DoesNotExistException {
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        try {
            if (!brAPIObservationUnitList.isEmpty()) {
                List<BrAPIObservationUnit> ous = brAPIDAOUtil.post(brAPIObservationUnitList, upload, api::observationunitsPost, importDAO::update);
                processObservationUnits(program, ous, false);
                return ous;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    /**
     * Create observation units without import progress.
     * Mutates brAPIObservationUnitList.
     */
    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList, UUID programId) throws ApiException, DoesNotExistException {
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));
        ObservationUnitsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationUnitsApi.class);
        try {
            if (!brAPIObservationUnitList.isEmpty()) {
                List<BrAPIObservationUnit> ous = brAPIDAOUtil.post(brAPIObservationUnitList, api::observationunitsPost);
                processObservationUnits(program, ous, false);
                return ous;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    public List<BrAPIObservationUnit> getObservationUnitsById(Collection<String> observationUnitDbIds, Program program) throws ApiException {
        if (observationUnitDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        // TODO: Optimize and change to search/get on observationUnitDbId. This will cause sample submission tests to fail until we have a better solution for exists checks for tabular errors. [BI-2987]
        return getProgramObservationUnits(program.getId()).stream()
                .filter(ou -> observationUnitDbIds.contains(ou.getObservationUnitDbId()))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservationUnit> getObservationUnitsForStudyDbId(@NotNull String studyDbId, Program program) throws ApiException {
        // TODO: Optimize and change this to search/get on studyDbId once cache is removed for studies [BI-2979]
        return getProgramObservationUnits(program.getId()).stream()
                .filter(ou -> ou.getStudyDbId().equals(studyDbId))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservationUnit> getObservationUnitsForTrialDbIds(@NotNull UUID programId, List<String> trialDbIds) throws ApiException {
        if (trialDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        // TODO: Optimize and change this to search on trialDbIds [BI-2979]
        return getProgramObservationUnits(programId).stream()
                .filter(ou -> trialDbIds.contains(ou.getTrialDbId()))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservationUnit> getObservationUnitsForTrialDbId(@NotNull UUID programId, @NotNull String trialDbId) throws ApiException {
        return getProgramObservationUnits(programId).stream()
                // TODO: Optimize and change this to search/get on trialDbId [BI-2979]
                .filter(ou -> ou.getTrialDbId().equals(trialDbId))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservationUnit> getObservationUnitsForDataset(@NotNull String datasetId, @NotNull Program program) throws ApiException {
        String datasetReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.DATASET);
        // TODO: Optimize and change this to search/get on dataset id once a solution is in place to relate ous to datasets [BI-2961]
        return getProgramObservationUnits(program.getId()).stream()
                .filter(ou -> {
                    Optional<BrAPIExternalReference> exRef = Utilities.getExternalReference(ou.getExternalReferences(), datasetReferenceSource);
                    return exRef.map(brAPIExternalReference -> brAPIExternalReference.getReferenceId().equals(datasetId)).orElse(false);
                })
                .collect(Collectors.toList());
    }

    public List<BrAPIObservationUnit> getObservationUnitsForDatasetAndEnvs(@NotNull String datasetId, Collection<String> envIds, @NotNull Program program) throws ApiException {
        String datasetReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.DATASET);
        String studyReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES);
        // TODO: Optimize and change this to search/get on both dataset id and studyDbId once a solution is in place to relate ous to datasets and study cache is removed [BI-2961]
        return getProgramObservationUnits(program.getId()).stream()
                .filter(ou -> {
                    Optional<BrAPIExternalReference> datasetExRef = Utilities.getExternalReference(ou.getExternalReferences(), datasetReferenceSource);
                    Optional<BrAPIExternalReference> studyExRef = Utilities.getExternalReference(ou.getExternalReferences(), studyReferenceSource);
                    return Boolean.logicalAnd(
                            datasetExRef.map(x -> x.getReferenceId().equals(datasetId)).orElse(false),
                            studyExRef.map(x -> envIds.contains(x.getReferenceId())).orElse(false)
                    );
                })
                .collect(Collectors.toList());
    }

    // Note: does not use cache, impractical to implement all search parameters client-side.
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
        //TODO add pagination support: This should be easy to implement with BrAPIDAOUtil.simpleSearch()
//                                    .page(page)
//                                    .pageSize(pageSize);

        List<String> xrefIds = new ArrayList<>();
        List<String> xrefSources = new ArrayList<>();
        BrAPIObservationUnitLevelRelationship level = new BrAPIObservationUnitLevelRelationship();
        AtomicBoolean levelFilter = new AtomicBoolean(false);
        BrAPIObservationUnitLevelRelationship relationship = new BrAPIObservationUnitLevelRelationship();
        AtomicBoolean relationshipFilter = new AtomicBoolean(false);

        observationUnitId.ifPresent(dbid -> observationUnitSearchRequest.setObservationUnitDbIds(List.of(dbid)));
        observationUnitName.ifPresent(name -> observationUnitSearchRequest.setObservationUnitNames(List.of(Utilities.appendProgramKey(name, program.getKey()))));
        locationDbId.ifPresent(dbid -> observationUnitSearchRequest.setLocationDbIds(List.of(dbid)));
        seasonDbId.ifPresent(dbid -> observationUnitSearchRequest.setSeasonDbIds(List.of(dbid)));
        experimentId.ifPresent(dbId -> observationUnitSearchRequest.setTrialDbIds(List.of(dbId)));
        includeObservations.ifPresent(observationUnitSearchRequest::includeObservations);
        // TODO: Are level filters being used here at all?  Should they be?
        addLevelFilter(observationUnitLevelName, observationUnitLevelOrder, observationUnitLevelCode, level, levelFilter);
        addLevelFilter(observationUnitLevelRelationshipName, observationUnitLevelRelationshipOrder, observationUnitLevelRelationshipCode, relationship, relationshipFilter);
        // TODO: Use observationUnitSearchRequest.setStudyDbIds() instead of xrefs [BI-2919]
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
            //xref search does an OR, so we need to convert the searching for expId/envId to be an AND
            boolean matches = environmentId.map(id -> id.equals(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES))
                                                                             .get()
                                                                             .getReferenceId()))
                                               .orElse(true);

            //adding filter for germplasmDbId because we can't easily search that in the stored data object
            // TODO: Add search on accessionNumber once it's been added to prod server and brapi client [BI-2978]
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

    private BrAPIObservationUnitSearchRequest buildSearchRequest(Program program, List<String> brapiOUDbIds) {
        BrAPIProgram brAPIProgram = programDAO.getProgramBrAPI(program);

        if (brAPIProgram == null || brAPIProgram.getProgramDbId() == null) {
            throw new InternalServerException(String.format("BI program with id [%s] not found in BrAPI db", program.getId()));
        }

        BrAPIObservationUnitSearchRequest searchRequest = new BrAPIObservationUnitSearchRequest();

        searchRequest.programDbIds(List.of(brAPIProgram.getProgramDbId()));

        if (brapiOUDbIds != null && !brapiOUDbIds.isEmpty()) {
            searchRequest.setObservationUnitDbIds(brapiOUDbIds);
        }

        // TODO: Utilize a search query for filtering/pagination on ous/datasets [BI-2961]
        brAPIDAOUtil.setGenericSearchParameters(searchRequest, null);

        return searchRequest;
    }

    private void processObservationUnits(Program program, List<BrAPIObservationUnit> brapiObservationUnits, boolean withGID) throws ApiException {

    	HashMap<String, BrAPIGermplasm> germplasmByDbId = new HashMap<>();
    	if( withGID ){
            // TODO: Optimize this to use germplasm information directly in BrAPIObservationUnit by adding accession num/GID there via the prodserver/client [BI-2978]
            this.germplasmService.getGermplasm(program.getId()).forEach((germplasm -> germplasmByDbId.put(germplasm.getGermplasmDbId(), germplasm)));
        }

        for (BrAPIObservationUnit ou : brapiObservationUnits) {
            JsonObject additionalInfo = ou.getAdditionalInfo();
            if (additionalInfo != null) {
                if( withGID ){
					BrAPIGermplasm germplasm = germplasmByDbId.get(ou.getGermplasmDbId());
                	ou.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GID, germplasm.getAccessionNumber());
                    ou.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_UUID,
                                             Utilities.getExternalReference(germplasm.getExternalReferences(), referenceSource)
                                                      .orElseThrow(() -> new IllegalStateException("Germplasm UUID not found"))
                                                      .getReferenceId());
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
}
