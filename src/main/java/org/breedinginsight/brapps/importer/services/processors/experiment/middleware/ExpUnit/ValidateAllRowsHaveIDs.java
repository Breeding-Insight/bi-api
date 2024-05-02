package org.breedinginsight.brapps.importer.services.processors.experiment.middleware.ExpUnit;

import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import java.util.HashSet;
import java.util.Set;

public class ValidateAllRowsHaveIDs extends ExpUnitMiddleware {
    private boolean hasAllReferenceUnitIds = true;
    private boolean hasNoReferenceUnitIds = true;

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        context.setReferenceOUIds(collateReferenceOUIds(context));
        return processNext(context);
    }

    @Override
    public boolean compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context, error);
    }

    private Set<String> collateReferenceOUIds(ExpUnitMiddlewareContext context) {
        Set<String> referenceOUIds = new HashSet<>();
        for (int rowNum = 0; rowNum < context.getImportContext().getImportRows().size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) context.getImportContext().getImportRows().get(rowNum);

            // Check if ObsUnitID is blank
            if (importRow.getObsUnitID() == null || importRow.getObsUnitID().isBlank()) {
                hasAllReferenceUnitIds = false;
            } else if (referenceOUIds.contains(importRow.getObsUnitID())) {

                // Throw exception if ObsUnitID is repeated
                this.compensate(context, new MiddlewareError(()->{
                    throw new IllegalStateException("ObsUnitId is repeated: " + importRow.getObsUnitID());
                }));

            } else {
                // Add ObsUnitID to referenceOUIds
                referenceOUIds.add(importRow.getObsUnitID());
                hasNoReferenceUnitIds = false;
            }
        }
        return referenceOUIds;
    }
}
