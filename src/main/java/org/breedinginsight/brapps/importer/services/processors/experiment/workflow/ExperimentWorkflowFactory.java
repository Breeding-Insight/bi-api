package org.breedinginsight.brapps.importer.services.processors.experiment.workflow;

import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.CreateNewExperimentWorkflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class ExperimentWorkflowFactory {

    private final Provider<CreateNewExperimentWorkflow> createNewExperimentWorkflowProvider;
    private final Provider<AppendOverwritePhenotypesWorkflow> appendOverwritePhenotypesWorkflowProvider;

    @Inject
    public ExperimentWorkflowFactory(Provider<CreateNewExperimentWorkflow> createNewExperimentWorkflowProvider,
                                     Provider<AppendOverwritePhenotypesWorkflow> appendOverwritePhenotypesWorkflowProvider) {
        this.createNewExperimentWorkflowProvider = createNewExperimentWorkflowProvider;
        this.appendOverwritePhenotypesWorkflowProvider = appendOverwritePhenotypesWorkflowProvider;
    }

    /**
     * Retrieves the appropriate workflow based on the provided import context. Validation will be done
     * in selected workflow, not here. For example will not check if file has ObsUnitIDs that all rows have one.
     * We are just checking the basic condition for what type of workflow to return.
     *
     * @param context import context containing import rows
     * @return the workflow to be used for processing the import rows
     */
    public Workflow getWorkflow(ImportContext context) {

        List<BrAPIImport> importRows = context.getImportRows();

        boolean hasExpUnitObsUnitIDs = importRows.stream()
                .anyMatch(row -> {
                    ExperimentObservation expRow = (ExperimentObservation) row;
                    return StringUtils.isNotBlank(expRow.getObsUnitID());
                });

        if (hasExpUnitObsUnitIDs) {
            long distinctCount = importRows.stream()
                    .map(row -> {
                        ExperimentObservation expRow = (ExperimentObservation) row;
                        return expRow.getObsUnitID();
                    })
                    .distinct()
                    .count();

            if (distinctCount != importRows.size()) {
                // If have ExpUnit ObsUnitIDs and there are duplicates -> Append / Update SubObsUnit Phenotypes
                // TODO: different workflow for subobs units?
                return appendOverwritePhenotypesWorkflowProvider.get();
            } else {
                // If have ExpUnit ObsUnitIDs and all are unique -> Append / Update ExpUnit Phenotypes
                return appendOverwritePhenotypesWorkflowProvider.get();
            }

        } else {
            // No ObsUnitIDs so creating experiment or appending env
            return (Workflow) createNewExperimentWorkflowProvider.get();
            // TODO: different workflow for appending envs? Would have a dependency on DAO to check for existing trial name
        }
    }

}
