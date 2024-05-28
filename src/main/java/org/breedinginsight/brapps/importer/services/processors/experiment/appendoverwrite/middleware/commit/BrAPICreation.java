package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.List;
import java.util.Optional;
@Slf4j
public abstract class BrAPICreation<T> implements BrAPIAction<T> {
    ExperimentImportEntity<T> entity;

    BrAPICreation(ExpUnitMiddlewareContext context) {
        this.entity = getEntity(context);
    }

    public Optional<BrAPIState> execute() {
        List<T> newMembers = entity.getNewBrAPIMembers();
        try {
            List<T> createdMembers = entity.brapiPost(newMembers);
            return Optional.of(new BrAPICreationState<T>(createdMembers));
        } catch (ApiException e) {
            // TODO: add specific error messages to entity service
            log.error("Error creating...");
            throw new InternalServerException("Error creating...", e);
        }
    }

    @Getter
    public class BrAPICreationState<U> implements BrAPIState {

        private final List<U> members;

        public BrAPICreationState(List<U> createdMembers) {
            this.members = createdMembers;
        }

        public boolean undo() {
            List<U> createdMembers = this.getMembers();
            try {
                return entity.brapiDelete(createdMembers);
            } catch (ApiException e) {
                // TODO: add specific error messages to entity service
                log.error("Error deleting...");
                throw new InternalServerException("Error deleting...", e);
            }
        }
    }
}
