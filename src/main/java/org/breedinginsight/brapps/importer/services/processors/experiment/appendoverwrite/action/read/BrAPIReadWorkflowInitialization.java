package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.BrAPIAction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.List;
import java.util.Optional;

/**
 * This abstract class, BrAPIReadWorkflowInitialization, is responsible for initializing and executing a read workflow for a given BrAPI entity.
 * It implements the BrAPIAction interface and uses the ExperimentImportEntity to perform read operations.
 *
 * @param <T> the type of entity for which the workflow is initialized
 */
@Slf4j
public abstract class BrAPIReadWorkflowInitialization<T> implements BrAPIAction<T> {

    ExperimentImportEntity<T> entity; // The entity used for read operations initialization

    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    protected BrAPIReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        this.entity = getEntity(context);
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
            return Optional.of(new BrAPIReadState<T>(fetchedMembers));
        } catch(ApiException e) {
            // TODO: add specific error messages to entity service
            log.error("Error reading...");
            throw new InternalServerException("Error reading...", e);
        }
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
