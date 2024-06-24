package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

import lombok.Getter;
import lombok.Setter;

public class MiddlewareException {
    @Getter
    @Setter
    String localTransactionName;

    @Getter
    @Setter
    Exception exception;

    public MiddlewareException(Exception exception) {
        this.exception = exception;
    }

    public void tag(String name) {
        if (this.getLocalTransactionName() == null) {
            this.setLocalTransactionName(name);
        }
    }
}
