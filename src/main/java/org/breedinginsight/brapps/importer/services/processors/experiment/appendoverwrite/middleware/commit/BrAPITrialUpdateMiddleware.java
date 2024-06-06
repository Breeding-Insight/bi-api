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
import java.util.Map;

@Slf4j
@Prototype
public class BrAPITrialUpdateMiddleware extends ExpUnitMiddleware {

    ExperimentUtilities experimentUtilities;
    BrAPITrialDAO brapiTrialDAO;
    private Map<String, BrAPITrial> mutatedTrialsById;

    @Inject
    public BrAPITrialUpdateMiddleware(ExperimentUtilities experimentUtilities, BrAPITrialDAO brapiTrialDAO) {
        this.experimentUtilities = experimentUtilities;
        this.brapiTrialDAO = brapiTrialDAO;
    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        // Construct request
        mutatedTrialsById = experimentUtilities.getMutationsByObjectId(context.getPendingData().getTrialByNameNoScope(), BrAPITrial::getTrialDbId, BrAPITrial.class);

        mutatedTrialsById.forEach((id, trial) -> {
            try {
                // Update entities in the brapi service
                brapiTrialDAO.updateBrAPITrial(id, trial, context.getImportContext().getProgram().getId());
            } catch (ApiException e) {
                log.error("Error updating dataset observation variables: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating dataset observation variables: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        });

        return processNext(context);
    }
}
