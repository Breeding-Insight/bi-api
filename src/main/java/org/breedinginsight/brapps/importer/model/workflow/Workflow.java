package org.breedinginsight.brapps.importer.model.workflow;

import io.micronaut.core.order.Ordered;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;

import java.util.Optional;

@FunctionalInterface
public interface Workflow extends Ordered {
    Optional<ImportWorkflowResult> process(ImportServiceContext context);
}
