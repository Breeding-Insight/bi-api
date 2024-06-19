package org.breedinginsight.brapps.importer.services.processors.experiment.workflow;

import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;

public interface Workflow {

    ProcessedData process(ImportContext context);
    String getName();
}
