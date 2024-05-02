package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.Getter;
import lombok.Setter;

public class MiddlewareError {
    @Getter
    @Setter
    String localTransactionName;
    Runnable handler;

    public MiddlewareError(Runnable handler) {
        this.handler = handler;
    }

    public void tag(String name) {
        if (this.getLocalTransactionName() == null) {
            this.setLocalTransactionName(name);
        }
    }
}
