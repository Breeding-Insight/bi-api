package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPIStudyCreation extends BrAPICreation<BrAPIStudy> {

    public BrAPIStudyCreation(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @param context the ExpUnitMiddlewareContext object
     * @return the ExperimentImportEntity object
     */
    @Override
    public ExperimentImportEntity<BrAPIStudy> getEntity(ExpUnitMiddlewareContext context) {
        return new PendingStudy(context);
    }
}
