package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;

@Getter
@Setter
@Builder
public class ExpUnitMiddlewareContext {

    private ImportContext importContext;
    private AppendWorkflowContext expUnitContext;
}
