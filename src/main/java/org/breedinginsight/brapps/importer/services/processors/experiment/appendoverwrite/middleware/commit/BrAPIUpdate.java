package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class BrAPIUpdate<T> implements BrAPIAction<T> {
    ExperimentImportEntity<T> entity;

    BrAPIUpdate(ExpUnitMiddlewareContext context) {
        this.entity = getEntity(context);
    }

    public Optional<BrAPIState> execute() {
        return saveAndUpdateCache(entity.getMutatedBrAPIMembers());
    }

    public Optional<BrAPIUpdateState<T>> getBrAPIState() {
        try {
            return Optional.of(new BrAPIUpdateState<T>(entity.getBrAPIStateMutatedMembers()));
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
                entity.updateCache(savedMembers);
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
