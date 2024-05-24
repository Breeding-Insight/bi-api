package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Prototype
public class BrAPITrialCreation extends ExpUnitMiddleware {

    ExperimentUtilities experimentUtilities;
    BrAPITrialDAO brapiTrialDAO;
    private List<BrAPITrial> newBrAPITrials;
    @Inject
    public BrAPITrialCreation(ExperimentUtilities experimentUtilities, BrAPITrialDAO brapiTrialDAO) {
        this.experimentUtilities = experimentUtilities;
        this.brapiTrialDAO = brapiTrialDAO;
    }
    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        newBrAPITrials = experimentUtilities.getNewObjects(context.getPendingData().getTrialByNameNoScope(), BrAPITrial.class);

        try {
            List<BrAPITrial> createdTrials = new ArrayList<>(brapiTrialDAO.createBrAPITrials(newBrAPITrials, context.getImportContext().getProgram().getId(), context.getImportContext().getUpload()));

            // Update the context cache by setting the system-generated dbId for each newly created trial
            for (BrAPITrial createdTrial : createdTrials) {
                String createdTrialNameNoScope = Utilities.removeProgramKey(createdTrial.getTrialName(), context.getImportContext().getProgram().getKey());
                context.getPendingData().getTrialByNameNoScope().get(createdTrialNameNoScope).getBrAPIObject().setTrialDbId(createdTrial.getTrialDbId());
            }

        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        return processNext(context);
    }
}
