package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.*;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;

@Data
@Builder
public class ExpUnitMiddlewareContext {
    private ImportContext importContext;
    private ExpUnitContext expUnitContext;
    private PendingData pendingData;
}
