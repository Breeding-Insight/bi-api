package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import java.util.*;

public class DatasetService {
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
}
