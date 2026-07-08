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

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationSingleResponse;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;

@Singleton
@Slf4j
public class BrAPIObservationDAO extends BrAPICachedDAO<BrAPIObservation> {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private BrAPIObservationUnitDAO observationUnitDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final String referenceSource;
    private final TraitService traitService;

    private final int brapiMaxPageSize;

    @Inject
    public BrAPIObservationDAO(ProgramDAO programDAO,
                               ImportDAO importDAO,
                               BrAPIObservationUnitDAO observationUnitDAO,
                               BrAPIDAOUtil brAPIDAOUtil,
                               BrAPIEndpointProvider brAPIEndpointProvider,
                               @Property(name = "brapi.server.reference-source") String referenceSource,
                               @Property(name = "micronaut.bi.api.run-scheduled-tasks") boolean runScheduledTasks,
                               @Property(name = "brapi.cache.fetch-page-size")  int brapiFetchPageSize,
                               TraitService traitService) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.observationUnitDAO = observationUnitDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
        this.traitService = traitService;
        this.brapiMaxPageSize = brapiFetchPageSize;
    }

    /**
     * Process BrAPIObservations for use in DeltaBreed (e.g. strip program key).
     */
    private void processObservations(String programKey, List<BrAPIObservation> observations) {
        for (BrAPIObservation obs: observations) {
            // Strip program key from observationVariableName.
            if (StringUtils.isNotBlank(obs.getObservationVariableName())) {
                obs.setObservationVariableName(Utilities.removeProgramKey(obs.getObservationVariableName(), programKey));
            }
            // Strip program key and unknown info from germplasmName and observationUnitName.
            if (StringUtils.isNotBlank(obs.getGermplasmName())) {
                obs.setGermplasmName(Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getGermplasmName(), programKey));
            }
            if (StringUtils.isNotBlank(obs.getObservationUnitName())) {
                obs.setObservationUnitName(Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getObservationUnitName(), programKey));
            }
        }
    }

    private List<BrAPIObservation> getProgramObservations(UUID programId) throws ApiException {
        Program program = programDAO.get(programId)
                .stream()
                .findFirst()
                .orElseThrow();

        return getBrAPIObservationsUsingBrAPIProgramId(program);
    }

    public List<BrAPIObservation> getObservationsByStudyName(List<String> studyNames, Program program) throws ApiException {
        if(studyNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIObservationSearchRequest observationSearchRequest = new BrAPIObservationSearchRequest();
        observationSearchRequest.setProgramDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationSearchRequest.setStudyNames(new ArrayList<>(studyNames));
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationsApi.class);
        return brAPIDAOUtil.search(
                api::searchObservationsPost,
                (brAPIWSMIMEDataTypes, searchResultsDbId, page, pageSize) -> searchObservationsSearchResultsDbIdGet(program.getId(), searchResultsDbId, page, pageSize),
                observationSearchRequest
        );
    }

    /**
     * Retrieves a list of observations based on their database IDs and a specific program.
     *
     * @param dbIds A list of database IDs representing the observations to retrieve.
     * @param program The Program object for which the observations belong.
     * @return A List of BrAPIObservation objects filtered by the provided database IDs.
     * @throws ApiException if an error occurs during the retrieval process.
     */
    public List<BrAPIObservation> getObservationsByDbIds(List<String> dbIds, Program program) throws ApiException {
        // Check if the dbIds list is empty and return an empty list if so
        if(dbIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter the observations based on the provided program ID and the provided list of dbIds
        // Collect the filtered observations into a List and return the result
        return getProgramObservations(program.getId()).stream()
                .filter(o -> dbIds.contains(o.getObservationDbId()))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservation> getObservationsByTrialDbId(List<String> trialDbIds, Program program) throws ApiException {
        if(trialDbIds.isEmpty()) {
            return Collections.emptyList();
        }
        // First, get all ObservationUnits for the given trialDbIds.
        // TODO: Once OUDAO removes cache, investigate utilizing observationUnit GET param to includeObservations instead of making an extra call for the observations. This should offer performance gains. [BI-2963]
        List<String> observationUnitDbIds = observationUnitDAO.getObservationUnitsForTrialDbIds(program.getId(), trialDbIds)
                .stream().map(BrAPIObservationUnit::getObservationUnitDbId).collect(Collectors.toList());
        // Finally, return all Observations for those ObservationUnits (Observations are linked to Trial through ObservationUnits).
        // TODO: This gets all observations for the program and filters, which is extremely inefficient.  If above TODO suggestion doesn't work, another improvement would be to search on OU ids directly in BrAPI instead. [BI-2963]
        return getProgramObservations(program.getId()).stream()
                .filter(o -> observationUnitDbIds.contains(o.getObservationUnitDbId()))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservation> getObservationsByObservationUnitsAndVariables(Collection<String> ouDbIds, Collection<String> variableDbIds, Program program) throws ApiException {
        if(ouDbIds.isEmpty() || variableDbIds.isEmpty()) {
            return Collections.emptyList();
        }
        // TODO: Once OUDAO removes cache, change to BrAPI Observation search request on program, observation var ids, and ouDbId [BI-2963]
        return getProgramObservations(program.getId()).stream()
                .filter(o -> ouDbIds.contains(o.getObservationUnitDbId()) && variableDbIds.contains(o.getObservationVariableDbId()))
                .collect(Collectors.toList());
    }

    public List<BrAPIObservation> getObservationsByObservationUnits(Collection<String> ouDbIds, Program program) throws ApiException {
        // TODO: Once OUDAO removes cache, change to BrAPI Observation search request on program and ouDbIds [BI-2963]
        if(ouDbIds.isEmpty()) {
            return Collections.emptyList();
        }
        return getProgramObservations(program.getId()).stream()
                .filter(o -> ouDbIds.contains(o.getObservationUnitDbId()))
                .collect(Collectors.toList());
    }

    // TODO: implement other filters in BI-2506.
    public List<BrAPIObservation> getObservationsByFilters(Program program, String studyDbId) throws ApiException, DoesNotExistException {

        String studySource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES);
        String observationUnitSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS);
        String observationSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATIONS);

        // Get all observations for the program.
        Collection<BrAPIObservation> observations = getProgramObservations(program.getId());
        // Build a hashmap of traits for fast lookup. The key is ObservationVariableDbId, the value is the Trait Id.
        HashMap<String, String> traitIdsByObservationVariableDbId = traitService.getIdsByObservationVariableDbIds(program.getId(), observations.stream().map(BrAPIObservation::getObservationVariableDbId).collect(Collectors.toList()));

        // Lookup studyDbId.
        return observations.stream()
                .filter(o -> {
                    // Short circuit if filter is null.
                    if (studyDbId == null) return true;
                    Optional<BrAPIExternalReference> xref = Utilities.getExternalReference(o.getExternalReferences(), studySource);
                    return xref.filter(brAPIExternalReference -> studyDbId.equals(brAPIExternalReference.getReferenceId())).isPresent();
                })
                // Try to figure out why/how this translation is used.
                .peek(o -> {
                    // Translate ObservationVariableDbId.
                    o.setObservationVariableDbId(traitIdsByObservationVariableDbId.get(o.getObservationVariableDbId()));
                    // Translate ObservationUnitDbId.
                    o.setObservationUnitDbId(Utilities.getExternalReference(o.getExternalReferences(), observationUnitSource)
                            .orElseThrow(() -> new RuntimeException("observationUnit xref not found on observation")).getReferenceId());
                    // Translate ObservationId.
                    o.setObservationDbId(Utilities.getExternalReference(o.getExternalReferences(), observationSource)
                            .orElseThrow(() -> new RuntimeException("observation xref not found on observation")).getReferenceId());
                    // Translate StudyDbId.
                    o.setStudyDbId(Utilities.getExternalReference(o.getExternalReferences(), studySource)
                            .orElseThrow(() -> new RuntimeException("study xref not found on observation")).getReferenceId());
                    // TODO: consider translating germplasmDbId in BI-2506.
                }).collect(Collectors.toList());
    }

    @NotNull
    private ApiResponse<Pair<Optional<BrAPIObservationListResponse>, Optional<BrAPIAcceptedSearchResponse>>> searchObservationsSearchResultsDbIdGet(UUID programId, String searchResultsDbId, Integer page, Integer pageSize) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        return api.searchObservationsSearchResultsDbIdGet(APPLICATION_JSON, searchResultsDbId, page, pageSize);
    }

    public List<BrAPIObservation> createBrAPIObservations(List<BrAPIObservation> brAPIObservationList, UUID programId, ImportUpload upload) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        var program = programDAO.fetchOneById(programId);
        try {
            if (!brAPIObservationList.isEmpty()) {
                    List<BrAPIObservation> postResponse = brAPIDAOUtil.post(brAPIObservationList, upload, api::observationsPost, importDAO::update);
                    processObservations(program.getKey(), postResponse);
                    return postResponse;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    private List<BrAPIObservation> getBrAPIObservationsUsingBrAPIProgramId(Program program) throws ApiException {

        if (program == null || program.getId() == null) {
            throw new InternalServerException("BI-API Program or Program ID is null");
        }

        String brapiProgramDbId = Optional.of(program)
                .map(Program::getBrapiProgram)
                .map(BrAPIProgram::getProgramDbId)
                .orElse(null);

        if (brapiProgramDbId == null) {
            brapiProgramDbId = programDAO.getProgramBrAPI(program).getProgramDbId();
        }

        ObservationQueryParams observationQueryParams =
                ObservationQueryParams.builder()
                        .programDbId(brapiProgramDbId)
                        .pageSize(brapiMaxPageSize)
                        .page(0)
                        .build();

        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationsApi.class);

        List<BrAPIObservation> result = brAPIDAOUtil.get(api::observationsGet, observationQueryParams);

        processObservations(program.getKey(), result);

        return result;
    }

    public List<BrAPIObservation> updateBrAPIObservation(Map<String, BrAPIObservation> mutatedObservationByDbId, UUID programId) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        var program = programDAO.fetchOneById(programId);

        List <BrAPIObservation> updatedObservations = new ArrayList<>();
        try {
            // TODO: Instead of a for loop, utilize BrAPI Observations put to do all updates in one request. [BI-2969]
            for (Map.Entry<String, BrAPIObservation> entry : mutatedObservationByDbId.entrySet()) {
                String dbId = entry.getKey();
                BrAPIObservation observation = entry.getValue();
                if (observation == null) {
                    throw new Exception("Null observation");
                }

                BrAPIObservation updatedObservation;

                try {
                     updatedObservation = brAPIDAOUtil.put(dbId, observation, api::observationsObservationDbIdPut);
                } catch (ApiException e) {
                    throw new RuntimeException(e);
                }
                updatedObservations.add(updatedObservation);

                if (!Objects.equals(observation.getValue(), updatedObservation.getValue())
                        || !Objects.equals(observation.getObservationTimeStamp(), updatedObservation.getObservationTimeStamp())) {
                    String message;
                    if (!Objects.equals(observation.getValue(), updatedObservation.getValue())) {
                        message = String.format("Updated observation, %s, from BrAPI service does not match requested update %s.", updatedObservation.getValue(), observation.getValue());
                    } else {
                        message = String.format("Updated observation timestamp, %s, from BrAPI service does not match requested update timestamp %s.", updatedObservation.getObservationTimeStamp(), observation.getObservationTimeStamp());
                    }
                    throw new Exception(message);
                }
            }
            processObservations(program.getKey(), updatedObservations);
            return updatedObservations;
        } catch (ApiException e) {
            log.error("Error updating observation: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw e;
        } catch (Exception e) {
            log.error("Error updating observation: ", e);
            throw new InternalServerException(e.getMessage(), e);
        }
    }
}
