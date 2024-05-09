package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.DatasetService;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RequiredDatasets extends ExpUnitMiddleware {
    private final DatasetService datasetService;

    @Inject
    public RequiredDatasets(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        Program program;
        String datasetId;
        BrAPIListDetails dataset = null;
        PendingImportObject<BrAPIListDetails> pendingDataset;
        Map<String, PendingImportObject<BrAPITrial>> pendingTrialByNameNoScope;
        Map<String, PendingImportObject<BrAPIListDetails>> pendingDatasetByName;

        program = context.getImportContext().getProgram();
        pendingTrialByNameNoScope = context.getPendingData().getTrialByNameNoScope();

        // nothing to do if there are no trials with dataset ids
        if (pendingTrialByNameNoScope.size() == 0 ||
                !pendingTrialByNameNoScope.values().iterator().next().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            return processNext(context);
        }
        log.debug("fetching from BrAPI service, datasets belonging to required units");

        // Get the id of the dataset belonging to the required exp units
        datasetId = pendingTrialByNameNoScope.values().iterator().next().getBrAPIObject()
                .getAdditionalInfo()
                .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)
                .getAsString();


        // Get the dataset belonging to required exp units
        try {
            dataset = datasetService.fetchDatasetById(datasetId, program);
        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        // Construct the pending dataset from the BrAPI observation variable list
        pendingDataset = datasetService.constructPIOFromDataset(dataset, program);

        // Construct a hashmap to look up the pending dataset by dataset name
        pendingDatasetByName = new HashMap<>();
        pendingDatasetByName.put(dataset.getListName(), pendingDataset);

        // Add the map to the context for use in processing import
        context.getPendingData().setObsVarDatasetByName(pendingDatasetByName);

        return processNext(context);
    }
}
