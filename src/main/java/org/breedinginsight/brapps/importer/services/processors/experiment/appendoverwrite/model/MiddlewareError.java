package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

import lombok.Getter;
import lombok.Setter;

public class MiddlewareError {
    @Getter
    @Setter
    String localTransactionName;

    @Getter
    @Setter
    Exception error;

    public MiddlewareError(Exception error) {
        this.error = error;
    }

    public void tag(String name) {
        if (this.getLocalTransactionName() == null) {
            this.setLocalTransactionName(name);
        }
    }
}
