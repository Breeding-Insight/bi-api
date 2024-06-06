package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPICreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.BrAPIStudyCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.Optional;

@Slf4j
@Prototype
@NoArgsConstructor
public class BrAPIStudyCommit extends ExpUnitMiddleware {
    private BrAPIStudyCreation brAPIStudyCreation;
    private Optional<BrAPICreation.BrAPICreationState> createdBrAPIStudies;

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        try {
            brAPIStudyCreation = new BrAPIStudyCreation(context);
            createdBrAPIStudies = brAPIStudyCreation.execute().map(s -> (BrAPICreation.BrAPICreationState) s);
        } catch (ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // Tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // Delete any created studies from the BrAPI service
        createdBrAPIStudies.ifPresent(BrAPICreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context, error);
    }
}
