package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.BrAPIAction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class BrAPIRead<T> implements BrAPIAction<T> {
    ExperimentImportEntity<T> entity;

    BrAPIRead(ExpUnitMiddlewareContext context) {
        this.entity = getEntity(context);
    }
    public Optional<BrAPIState> execute() {
        try {
            List<T> fetchedMembers = entity.brapiRead();
            entity.updateCache(fetchedMembers);
            return Optional.of(new BrAPIReadState<T>(fetchedMembers));
        } catch(ApiException e) {
            // TODO: add specific error messages to entity service
            log.error("Error reading...");
            throw new InternalServerException("Error reading...", e);
        }
    }
    @Getter
    public static class BrAPIReadState<T> implements BrAPIState {

        private final List<T> members;
        public BrAPIReadState(List<T> fetchedMembers) { this.members = fetchedMembers; }
    }
}
