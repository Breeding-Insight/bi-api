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

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapi.v2.services.BrAPIObservationLevelService;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.DatasetLevel;
import org.breedinginsight.model.DatasetMetadata;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class DatasetService {
    private final BrAPIListDAO brAPIListDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    private final BrAPIObservationLevelService observationLevelService;

    @Inject
    public DatasetService(BrAPIListDAO brapiListDAO,
                          BrAPIObservationLevelService brAPIObservationLevelService) {
        this.brAPIListDAO = brapiListDAO;
        this.observationLevelService = brAPIObservationLevelService;
    }
    /**
     * Module: Dataset Utility
     *
     * This module provides utility functions for interacting with datasets using the BrAPI standards.
     * It includes methods for fetching dataset details, creating new datasets, updating existing datasets, etc.
     * Usage: This module can be used in various applications where handling BrAPI-compliant datasets is required.
     */

    /**
     * Fetches dataset details by dataset ID and program
     *
     * This function fetches details of a dataset by its ID and associated program from a data source using the BrAPI standards.
     *
     * @param id The unique identifier of the dataset to fetch
     * @param program The program object associated with the dataset
     * @return BrAPIListDetails object containing the details of the dataset
     * @throws ApiException if there is an issue with fetching the dataset details from the data source
     */
    public Optional<BrAPIListDetails> fetchDatasetById(String id, Program program) throws ApiException {
        Optional<BrAPIListDetails> dataSetDetails = Optional.empty();

        // Retrieve existing dataset summaries based on program ID and external reference
        List<BrAPIListSummary> existingDatasets = brAPIListDAO
                .getListsByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                        program.getId(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                        UUID.fromString(id));
        if (existingDatasets == null || existingDatasets.isEmpty()) {
            return Optional.empty();
        }

        // Retrieve dataset details using the list DB ID from the existing dataset summary
        dataSetDetails = Optional.ofNullable(brAPIListDAO
                .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                .getResult());

        return dataSetDetails;
    }

    public Optional<List<BrAPIListDetails>> fetchDatasetsByIds(Set<String> datasetIds, Program program) throws ApiException {
        List<BrAPIListDetails> datasets = new ArrayList<>();
        for (String datasetId : datasetIds) {
            Optional<BrAPIListDetails> dataSetDetailsOptional = fetchDatasetById(datasetId, program);
            dataSetDetailsOptional.ifPresent(datasets::add);
        }

        return datasets.isEmpty() ? Optional.empty() : Optional.of(datasets);
    }

    /**
     * Constructs a PendingImportObject for a BrAPIListDetails dataset.
     * This method retrieves the external reference for the dataset from the existing list
     * based on a specific reference source. It then creates a PendingImportObject for the dataset
     * with the existing list and reference ID.
     *
     * @param dataset The BrAPIListDetails dataset for which to construct the PendingImportObject
     * @param program
     * @return A PendingImportObject containing the dataset with the existing list and reference ID
     * @throws IllegalStateException if external references weren't found for the list
     */
    public PendingImportObject<BrAPIListDetails> constructPIOFromDataset(BrAPIListDetails dataset, Program program) {
        // Get the external reference for the dataset from the existing list
        BrAPIExternalReference xref = Utilities.getExternalReference(dataset.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()))
                .orElseThrow(() -> new IllegalStateException("External references weren't found for list (dbid): " + dataset.getListDbId()));

        // Create a PendingImportObject for the dataset with the existing list and reference ID
        return new PendingImportObject<BrAPIListDetails>(ImportObjectState.EXISTING, dataset, UUID.fromString(xref.getReferenceId()));
    }

    public void createBrAPIObsVarListForDataset(Program program,
                                                BrAPITrial trial,
                                                DatasetMetadata subEntityDatasetMetadata) throws ApiException {

        String name = String.format("Observation Dataset [%s-%s-%s]",
                program.getKey(),
                trial.getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                        .getAsString(),
                subEntityDatasetMetadata.getName());

        BrAPIListDetails subEntityObsVarsList = constructDatasetDetails(name,
                subEntityDatasetMetadata.getId(),
                BRAPI_REFERENCE_SOURCE,
                program,
                trial.getTrialDbId());

        BrAPIListNewRequest listRq = new BrAPIListNewRequest();
        listRq.setListName(subEntityObsVarsList.getListName());
        listRq.setListType(subEntityObsVarsList.getListType());
        listRq.setExternalReferences(subEntityObsVarsList.getExternalReferences());
        listRq.setAdditionalInfo(subEntityObsVarsList.getAdditionalInfo());
        listRq.data(subEntityObsVarsList.getData());

        brAPIListDAO.createBrAPILists(List.of(listRq), program.getId(), null);
    }

    public BrAPIListDetails constructDatasetDetails(
            String name,
            UUID datasetId,
            String referenceSourceBase,
            Program program, String trialId) {
        BrAPIListDetails dataSetDetails = new BrAPIListDetails();
        dataSetDetails.setListName(name);
        dataSetDetails.setListType(BrAPIListTypes.OBSERVATIONVARIABLES);
        dataSetDetails.setData(new ArrayList<>());
        dataSetDetails.putAdditionalInfoItem("datasetType", "observationDataset");
        List<BrAPIExternalReference> refs = new ArrayList<>();
        Utilities.addReference(refs, program.getId(), referenceSourceBase, ExternalReferenceSource.PROGRAMS);
        Utilities.addReference(refs, UUID.fromString(trialId), referenceSourceBase, ExternalReferenceSource.TRIALS);
        Utilities.addReference(refs, datasetId, referenceSourceBase, ExternalReferenceSource.DATASET);
        dataSetDetails.setExternalReferences(refs);
        return dataSetDetails;
    }

    /**
     * @return brapiLevelNameDbId of found or created record
     */
    public String getOrCreateLevelNameForDataset(Program program,
                                                 String brapiProgramDbId,
                                                 String levelName,
                                                 DatasetLevel levelOrder) throws ApiException {

        String existingLevelNameDbId = findLevelNameByNameAndOrder(program, brapiProgramDbId, levelName, levelOrder);

        if (StringUtils.isNotBlank(existingLevelNameDbId)) {
            return existingLevelNameDbId;
        }

        // Level name does not exist and needs to be created.
        BrAPIObservationUnitHierarchyLevel createdLevelName = observationLevelService.createObservationLevel(program, brapiProgramDbId, levelName, levelOrder);

        return createdLevelName.getLevelNameDbId();
    }

    /**
     * This method retrieves the programmatic level names and then matches the level names on the submitted
     * level name and order.
     *
     * @return levelNameDbId of the matched level name
     */
    private String findLevelNameByNameAndOrder(Program program,
                                               String brapiProgramDbId,
                                               String levelName,
                                               DatasetLevel levelOrder) {
        var programmaticLevelNames = observationLevelService.getProgrammaticLevelNames(program, brapiProgramDbId);

        List<BrAPIObservationUnitHierarchyLevel> levelNameStreamResult
                = programmaticLevelNames.stream()
                .filter(ouln -> ouln.getLevelName().equals(levelName.toLowerCase()) && ouln.getLevelOrder() == levelOrder.getValue())
                .limit(1)
                .collect(Collectors.toList());

        if (levelNameStreamResult.isEmpty()) {
            return null;
        }

        return levelNameStreamResult.get(0).getLevelNameDbId();
    }

    public void updateObservationUnitsWithLevelNameDbIds(List<BrAPIObservationUnit> observationUnits,
                                                         Program program,
                                                         String brapiProgramDbId,
                                                         String expUnitName,
                                                         DatasetLevel levelOrder) throws ApiException {
        Map<String, String> levelNameDbIdByName = new HashMap<>();

        String expLevelName = expUnitName.toLowerCase();

        String existingLevelNameDbId = getOrCreateLevelNameForDataset(program, brapiProgramDbId, expLevelName, levelOrder);
        levelNameDbIdByName.put(expLevelName, existingLevelNameDbId);

        List<BrAPIObservationUnitHierarchyLevel> globalLevelNames = observationLevelService.getGlobalLevelNames(program);

        globalLevelNames.forEach(ouln -> levelNameDbIdByName.put(ouln.getLevelName(), ouln.getLevelNameDbId()));

        for (BrAPIObservationUnit observationUnit : observationUnits) {

            String positionLevelName = observationUnit.getObservationUnitPosition().getObservationLevel().getLevelName().toLowerCase();

            observationUnit.getObservationUnitPosition().getObservationLevel().setLevelNameDbId(levelNameDbIdByName.get(positionLevelName));

            for (BrAPIObservationUnitLevelRelationship lvlRelationship : observationUnit.getObservationUnitPosition().getObservationLevelRelationships()) {
                if (lvlRelationship.getLevelName().equals(BrAPIConstants.BLOCK.getValue())) {
                    lvlRelationship.setLevelNameDbId(levelNameDbIdByName.get(lvlRelationship.getLevelName()));
                } else if (lvlRelationship.getLevelName().equals(BrAPIConstants.REPLICATE.getValue())) {
                    lvlRelationship.setLevelNameDbId(levelNameDbIdByName.get(lvlRelationship.getLevelName()));
                } else {
                    throw new InternalServerException(String.format("Level name [%s] detected in OU Level Relationship " +
                            "for experiment with Exp Unit name [%s].  This is unexpected and the new level " +
                            "name must be retrieved properly from BrAPI to insert its DbId into BrAPI request for proper creation and assignment.",
                            lvlRelationship.getLevelName(), expUnitName));
                }
            }
        }

    }
}
