package org.breedinginsight.brapps.importer.model.workflow;

import io.micronaut.core.order.Ordered;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;

import java.util.Optional;

@FunctionalInterface
public interface ImportWorkflow extends Ordered {
    Optional<ImportWorkflowResult> process(ImportServiceContext context);
}
