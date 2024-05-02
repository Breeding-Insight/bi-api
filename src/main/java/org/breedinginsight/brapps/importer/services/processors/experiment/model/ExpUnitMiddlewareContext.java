package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.*;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;

@Getter
@Setter
public class ExpUnitMiddlewareContext {

    private ImportContext importContext;
    private ExpUnitContext expUnitContext;
}
