package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Prototype
public class BrAPIObservationUpdateMiddleware extends ExpUnitMiddleware {

    ExperimentUtilities experimentUtilities;
    BrAPIObservationDAO brapiObservationDAO;
    private Map<String, BrAPIObservation> mutatedObservationByDbId;

    @Inject
    public BrAPIObservationUpdateMiddleware(ExperimentUtilities experimentUtilities, BrAPIObservationDAO brapiObservationDAO) {
        this.experimentUtilities = experimentUtilities;
        this.brapiObservationDAO = brapiObservationDAO;
    }
    @Override
    public boolean process(ExpUnitMiddlewareContext context) {

        mutatedObservationByDbId = experimentUtilities.getMutationsByObjectId(context.getPendingData().getPendingObservationByHash(), BrAPIObservation::getObservationDbId, BrAPIObservation.class);

        mutatedObservationByDbId.forEach((id, observation) ->  {
            try {
                if (observation == null) {
                    throw new Exception("Null observation");
                }
                BrAPIObservation updatedObs = brapiObservationDAO.updateBrAPIObservation(id, observation, context.getImportContext().getProgram().getId());

                if (updatedObs == null) {
                    throw new Exception("Null updated observation");
                }

                if (!Objects.equals(observation.getValue(), updatedObs.getValue())
                        || !Objects.equals(observation.getObservationTimeStamp(), updatedObs.getObservationTimeStamp())) {
                    String message;
                    if(!Objects.equals(observation.getValue(), updatedObs.getValue())) {
                        message = String.format("Updated observation, %s, from BrAPI service does not match requested update %s.", updatedObs.getValue(), observation.getValue());
                    } else {
                        message = String.format("Updated observation timestamp, %s, from BrAPI service does not match requested update timestamp %s.", updatedObs.getObservationTimeStamp(), observation.getObservationTimeStamp());
                    }
                    throw new Exception(message);
                }
            } catch (ApiException e) {
                log.error("Error updating observation: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating observation: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        });

        return processNext(context);
    }
}
