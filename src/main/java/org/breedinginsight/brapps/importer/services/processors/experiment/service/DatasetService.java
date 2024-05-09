package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class DatasetService {
    private final BrAPIListDAO brAPIListDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public DatasetService(BrAPIListDAO brapiListDAO) {
        this.brAPIListDAO = brapiListDAO;
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
    public BrAPIListDetails fetchDatasetById(String id, Program program) throws ApiException {
        BrAPIListDetails dataSetDetails = null;

        // Retrieve existing dataset summaries based on program ID and external reference
        List<BrAPIListSummary> existingDatasets = brAPIListDAO
                .getListByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                        program.getId(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                        UUID.fromString(id));

        // Check if the existing dataset summaries are returned, throw exception if not
        if (existingDatasets == null || existingDatasets.isEmpty()) {
            throw new InternalServerException("Existing dataset summary not returned from BrAPI server");
        }

        // Retrieve dataset details using the list DB ID from the existing dataset summary
        dataSetDetails = brAPIListDAO
                .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                .getResult();

        return dataSetDetails;
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
                .orElseThrow(() -> new IllegalStateException("External references weren't found for list (dbid): " + dataset.getListDbId());

        // Create a PendingImportObject for the dataset with the existing list and reference ID
        return new PendingImportObject<BrAPIListDetails>(ImportObjectState.EXISTING, dataset, UUID.fromString(xref.getReferenceId()));
    }

    // TODO: used by expunit worflow
    public Map<String, PendingImportObject<BrAPIListDetails>> initializeObsVarDatasetForExistingObservationUnits(
            Map<String, PendingImportObject<BrAPITrial>> trialByName,
            Program program) {
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = new HashMap<>();

        if (trialByName.size() > 0 &&
                trialByName.values().iterator().next().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            String datasetId = trialByName.values().iterator().next().getBrAPIObject()
                    .getAdditionalInfo()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)
                    .getAsString();

            try {
                List<BrAPIListSummary> existingDatasets = brAPIListDAO
                        .getListByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                                program.getId(),
                                String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                                UUID.fromString(datasetId));
                if (existingDatasets == null || existingDatasets.isEmpty()) {
                    throw new InternalServerException("existing dataset summary not returned from brapi server");
                }
                BrAPIListDetails dataSetDetails = brAPIListDAO
                        .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                        .getResult();
                processAndCacheObsVarDataset(dataSetDetails, obsVarDatasetByName);
            } catch (ApiException e) {
                log.error(Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
        return obsVarDatasetByName;
    }

    // TODO: used by create workflow
    public Map<String, PendingImportObject<BrAPIListDetails>> initializeObsVarDatasetByName(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = new HashMap<>();

        Optional<PendingImportObject<BrAPITrial>> trialPIO = getTrialPIO(experimentImportRows);

        if (trialPIO.isPresent() && trialPIO.get().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            String datasetId = trialPIO.get().getBrAPIObject()
                    .getAdditionalInfo()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)
                    .getAsString();
            try {
                List<BrAPIListSummary> existingDatasets = brAPIListDAO
                        .getListByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                                program.getId(),
                                String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                                UUID.fromString(datasetId));
                if (existingDatasets == null || existingDatasets.isEmpty()) {
                    throw new InternalServerException("existing dataset summary not returned from brapi server");
                }
                BrAPIListDetails dataSetDetails = brAPIListDAO
                        .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                        .getResult();
                processAndCacheObsVarDataset(dataSetDetails, obsVarDatasetByName);
            } catch (ApiException e) {
                log.error(Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
        return obsVarDatasetByName;
    }

    // TODO: used by expunit workflow
    public Map<String, PendingImportObject<BrAPIListDetails>> mapPendingObsDatasetByOUId(
            String unitId,
            Map<String, PendingImportObject<BrAPITrial>> trialByOUId,
            Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName,
            Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByOUId) {
        if (!trialByOUId.isEmpty() && !obsVarDatasetByName.isEmpty() &&
                trialByOUId.values().iterator().next().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            obsVarDatasetByOUId.put(unitId, obsVarDatasetByName.values().iterator().next());
        }

        return obsVarDatasetByOUId;
    }

    // TODO: used by both workflows
    public void addObsVarsToDatasetDetails(PendingImportObject<BrAPIListDetails> pio, List<Trait> referencedTraits, Program program) {
        BrAPIListDetails details = pio.getBrAPIObject();
        referencedTraits.forEach(trait -> {
            String id = Utilities.appendProgramKey(trait.getObservationVariableName(), program.getKey());

            // TODO - Don't append the key if connected to a brapi service operating with legacy data(no appended program key)

            if (!details.getData().contains(id) && ImportObjectState.EXISTING != pio.getState()) {
                details.getData().add(id);
            }
            if (!details.getData().contains(id) && ImportObjectState.EXISTING == pio.getState()) {
                details.getData().add(id);
                pio.setState(ImportObjectState.MUTATED);
            }
        });
    }

    // TODO: used by expunit workflow
    public void fetchOrCreateDatasetPIO(ImportContext importContext,
                                        PendingData pendingData,
                                        ExpUnitContext expUnitContext,
                                        List<Trait> referencedTraits) throws UnprocessableEntityException {
        PendingImportObject<BrAPIListDetails> pio;
        PendingImportObject<BrAPITrial> trialPIO = getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES);
        String name = String.format("Observation Dataset [%s-%s]",
                program.getKey(),
                trialPIO.getBrAPIObject()
                        .getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                        .getAsString());
        if (obsVarDatasetByName.containsKey(name)) {
            pio = obsVarDatasetByName.get(name);
        } else {
            UUID id = UUID.randomUUID();
            BrAPIListDetails newDataset = importRow.constructDatasetDetails(
                    name,
                    id,
                    BRAPI_REFERENCE_SOURCE,
                    program,
                    trialPIO.getId().toString());
            pio = new PendingImportObject<BrAPIListDetails>(ImportObjectState.NEW, newDataset, id);
            trialPIO.getBrAPIObject().putAdditionalInfoItem("observationDatasetId", id.toString());
            if (ImportObjectState.EXISTING == trialPIO.getState()) {
                trialPIO.setState(ImportObjectState.MUTATED);
            }
            obsVarDatasetByName.put(name, pio);
        }
        addObsVarsToDatasetDetails(pio, referencedTraits, program);
    }

    // TODO: used by create workflow
    public void fetchOrCreateDatasetPIO(ImportContext importContext,
                                        PendingData pendingData,
                                        List<Trait> referencedTraits) throws UnprocessableEntityException {
        PendingImportObject<BrAPIListDetails> pio;
        PendingImportObject<BrAPITrial> trialPIO =  trialByNameNoScope.get(importRow.getExpTitle());

        String name = String.format("Observation Dataset [%s-%s]",
                program.getKey(),
                trialPIO.getBrAPIObject()
                        .getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                        .getAsString());
        if (obsVarDatasetByName.containsKey(name)) {
            pio = obsVarDatasetByName.get(name);
        } else {
            UUID id = UUID.randomUUID();
            BrAPIListDetails newDataset = importRow.constructDatasetDetails(
                    name,
                    id,
                    BRAPI_REFERENCE_SOURCE,
                    program,
                    trialPIO.getId().toString());
            pio = new PendingImportObject<BrAPIListDetails>(ImportObjectState.NEW, newDataset, id);
            trialPIO.getBrAPIObject().putAdditionalInfoItem("observationDatasetId", id.toString());
            if (ImportObjectState.EXISTING == trialPIO.getState()) {
                trialPIO.setState(ImportObjectState.MUTATED);
            }
            obsVarDatasetByName.put(name, pio);
        }
        addObsVarsToDatasetDetails(pio, referencedTraits, program);
    }

    // TODO: used by both workflows
    public List<BrAPIListSummary> commitNewPendingDatasetsToBrAPIStore(ImportContext importContext, PendingData pendingData) {
        List<BrAPIListNewRequest> newDatasetRequests = ProcessorData.getNewObjects(obsVarDatasetByName).stream().map(details -> {
            BrAPIListNewRequest request = new BrAPIListNewRequest();
            request.setListName(details.getListName());
            request.setListType(details.getListType());
            request.setExternalReferences(details.getExternalReferences());
            request.setAdditionalInfo(details.getAdditionalInfo());
            request.data(details.getData());
            return request;
        }).collect(Collectors.toList());
        List<BrAPIListSummary> createdDatasets = new ArrayList<>(brAPIListDAO.createBrAPILists(newDatasetRequests, program.getId(), upload));
        for (BrAPIListSummary summary : createdDatasets) {
            obsVarDatasetByName.get(summary.getListName()).getBrAPIObject().setListDbId(summary.getListDbId());
        }
        return createdDatasets;
    }

    // TODO: used by both workflows
    public List<BrAPIListSummary> commitUpdatedPendingDatasetsToBrAPIStore(ImportContext importContext, PendingData pendingData) {
        List<BrAPIListSummary> updatedDatasets = new ArrayList<>();
        Map<String, BrAPIListDetails> datasetNewDataById = ProcessorData
                .getMutationsByObjectId(obsVarDatasetByName, BrAPIListSummary::getListDbId);
        for (Map.Entry<String, BrAPIListDetails> entry : datasetNewDataById.entrySet()) {
            String id = entry.getKey();
            BrAPIListDetails dataset = entry.getValue();
            try {
                List<String> existingObsVarIds = brAPIListDAO.getListById(id, program.getId()).getResult().getData();
                List<String> newObsVarIds = dataset
                        .getData()
                        .stream()
                        .filter(obsVarId -> !existingObsVarIds.contains(obsVarId)).collect(Collectors.toList());
                List<String> obsVarIds = new ArrayList<>(existingObsVarIds);
                obsVarIds.addAll(newObsVarIds);
                dataset.setData(obsVarIds);
                updatedDatasets.add(brAPIListDAO.updateBrAPIList(id, dataset, program.getId()));
            } catch (ApiException e) {
                log.error("Error updating dataset observation variables: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating dataset observation variables: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        }
        return updatedDatasets;
    }
}
