package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class BrAPIDatasetCreation extends ExpUnitMiddleware {

    ExperimentUtilities experimentUtilities;
    BrAPIListDAO brapiListDAO;
    private List<BrAPIListNewRequest> newDatasetRequests;
    @Inject
    public BrAPIDatasetCreation(ExperimentUtilities experimentUtilities, BrAPIListDAO brapiListDAO) {
        this.experimentUtilities = experimentUtilities;
        this.brapiListDAO = brapiListDAO;
    }
    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        // Construct request
        newDatasetRequests = experimentUtilities.getNewObjects(context.getPendingData().getObsVarDatasetByName(), BrAPIListDetails.class).stream().map(details -> {
            BrAPIListNewRequest request = new BrAPIListNewRequest();
            request.setListName(details.getListName());
            request.setListType(details.getListType());
            request.setExternalReferences(details.getExternalReferences());
            request.setAdditionalInfo(details.getAdditionalInfo());
            request.data(details.getData());
            return request;
        }).collect(Collectors.toList());

        List<BrAPIListSummary> createdDatasets = null;
        try {
            // Create entities in brapi service
            createdDatasets = new ArrayList<>(brapiListDAO.createBrAPILists(newDatasetRequests, context.getImportContext().getProgram().getId(), context.getImportContext().getUpload()));

            // Update the context cache by setting the system-generated dbId for each newly created dataset
            createdDatasets.forEach(summary -> context.getPendingData().getObsVarDatasetByName().get(summary.getListName()).getBrAPIObject().setListDbId(summary.getListDbId()));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        return processNext(context);
    }
}