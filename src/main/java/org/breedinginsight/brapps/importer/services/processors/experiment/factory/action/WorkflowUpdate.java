package org.breedinginsight.brapps.importer.services.processors.experiment.factory.action;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.entity.ExperimentImportEntity;

import java.util.List;
import java.util.Optional;

@Slf4j
@Prototype
public class WorkflowUpdate<T> implements BrAPIAction<T> {
    private final ExperimentImportEntity<T> entity;

    protected WorkflowUpdate(ExperimentImportEntity entity) {

        this.entity = entity;
    }

    public Optional<BrAPIState> execute() throws ApiException {
        return saveAndUpdateCache(entity.copyWorkflowMembers(ImportObjectState.MUTATED));
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

    public Optional<BrAPIUpdateState<T>> getBrAPIState() {
        try {
            return Optional.of(new BrAPIUpdateState<T>(entity.getBrAPIState(ImportObjectState.MUTATED)));
        } catch (ApiException e) {
            // TODO: add specific error messages to entity service
            log.error("Error getting...");
            throw new InternalServerException("Error getting...", e);
        }

    }

    protected <V> Optional<BrAPIState> saveAndUpdateCache(List<V> members) {
        return Optional.ofNullable(members).map(changes -> {
            try {
                List<V> savedMembers = entity.brapiPut(changes);
                entity.updateWorkflow(savedMembers);
                return new BrAPIUpdateState<V>(savedMembers);
            } catch (ApiException e) {
                // TODO: add specific error messages to entity service
                log.error("Error updating...");
                throw new InternalServerException("Error updating...", e);
            }
        });
    }

    @Getter
    public class BrAPIUpdateState<U> implements BrAPIState {
        private final List<U> members;

        public BrAPIUpdateState(List<U> existingMembers) { this.members = existingMembers; }

        public boolean restore() {
            return saveAndUpdateCache(this.getMembers()).isPresent();
        }
    }

}
