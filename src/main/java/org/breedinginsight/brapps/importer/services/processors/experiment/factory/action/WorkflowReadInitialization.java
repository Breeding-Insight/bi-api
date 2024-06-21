package org.breedinginsight.brapps.importer.services.processors.experiment.factory.action;

import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.entity.ExperimentImportEntity;
import org.breedinginsight.utilities.Utilities;

import java.util.List;
import java.util.Optional;

@Slf4j
@Prototype
public class WorkflowReadInitialization<T> implements BrAPIAction<T> {
    private final ExperimentImportEntity<T> entity; // The entity used for read operations initialization

    protected WorkflowReadInitialization(ExperimentImportEntity entity) {
        this.entity = entity;
    }


    /**
     * Executes the read workflow by fetching members from the entity and initializing the workflow.
     *
     * @return an Optional containing the BrAPIState representing the completed read workflow
     * @throws ApiException if an error occurs during execution
     */
    public Optional<BrAPIState> execute() throws ApiException {
        try {
            List<T> fetchedMembers = entity.brapiRead();
            entity.initializeWorkflow(fetchedMembers);
            return Optional.of(new WorkflowReadInitialization.BrAPIReadState<T>(fetchedMembers));
        } catch(ApiException e) {
            log.error(String.format("Error fetching %s: %s", entity.getClass().getName(), Utilities.generateApiExceptionLogMessage(e)), e);
            throw new ApiException(e);
        }
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<T> getEntity() {
        return null;
    }

    /**
     * The state class representing the result of a read operation.
     *
     * @param <T> the type of entity members contained in the state
     */
    @Getter
    public static class BrAPIReadState<T> implements BrAPIState {

        private final List<T> members; // The list of members fetched during the read operation

        /**
         * Constructs a new BrAPIReadState object with the provided list of members.
         *
         * @param fetchedMembers the list of members fetched during the read operation
         */
        public BrAPIReadState(List<T> fetchedMembers) { this.members = fetchedMembers; }
    }
}
