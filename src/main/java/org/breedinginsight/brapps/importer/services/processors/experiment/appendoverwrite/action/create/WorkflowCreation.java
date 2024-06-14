package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.BrAPIAction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Prototype
public class WorkflowCreation<T> implements BrAPIAction<T> {

    private final ExperimentImportEntity<T> entity;


    protected WorkflowCreation(ExperimentImportEntity entity) {
        this.entity = entity;
    }



    /**
     * Executes the creation process for entities.
     * @return an Optional containing the BrAPI state after execution
     * @throws ApiException if an error occurs during execution
     */
    public Optional<BrAPIState> execute() throws ApiException {
        List<T> newMembers = entity.copyWorkflowMembers(ImportObjectState.NEW);
        try {
            List<T> createdMembers = entity.brapiPost(newMembers);
            entity.updateWorkflow(createdMembers);
            return Optional.of(new BrAPICreationState<>(createdMembers));
        } catch (ApiException | MissingRequiredInfoException | UnprocessableEntityException | DoesNotExistException e) {
            log.error("Error creating...");
            throw new InternalServerException("Error creating...", e);
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
     * Inner class representing the state of creation for BrAPI entities.
     * @param <U> the type of entity
     */
    @Getter
    public class BrAPICreationState<U> implements BrAPIState {

        private final List<U> members;

        /**
         * Constructor for BrAPICreationState class.
         * @param createdMembers the list of created members
         */
        public BrAPICreationState(List<U> createdMembers) {
            this.members = createdMembers;
        }

        /**
         * Undo the creation operation by deleting the created members.
         * @return true if undo operation is successful, false otherwise
         */
        public boolean undo() {
            List<U> createdMembers = this.getMembers();
            try {
                return entity.brapiDelete(createdMembers);
            } catch (ApiException e) {
                log.error("Error deleting...");
                throw new InternalServerException("Error deleting...", e);
            }
        }
    }
}
