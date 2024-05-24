package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
@Getter
@Setter
@Builder
public class ExpUnitMiddlewareContext {

    private ImportContext importContext;
    private ExpUnitContext expUnitContext;
    private PendingData pendingData;

}
