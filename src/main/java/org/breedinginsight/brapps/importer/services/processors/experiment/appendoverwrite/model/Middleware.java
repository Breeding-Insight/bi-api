package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

public abstract class Middleware<T> {

    Middleware next;
    Middleware prior;

    /**
     * Builds chains of middleware objects.
     */
    public static Middleware link(Middleware first, Middleware... chain) {
        Middleware head = first;
        for (Middleware nextInChain: chain) {
            nextInChain.prior = head.getLastLink();
            head.getLastLink().next = nextInChain;
            head = nextInChain;
        }
        return first;
    }

    /**
     * Subclasses will implement this local transaction.
     */
    public abstract T process(T context);
    /**
     * Subclasses will implement this method to handle errors and possibly undo the local transaction.
     */
    public abstract T compensate(T context);
    /**
     * Processes the next local transaction or ends traversing if we're at the
     * last local transaction of the transaction.
     */
    protected T processNext(T context) {
        if (next == null) {
            return context;
        }
        return (T) next.process(context);
    }

    /**
     * Runs the compensating local transaction for the prior local transaction or ends traversing if
     * we're at the first local transaction of the transaction.
     */
    protected T compensatePrior(T context) {
        if (prior == null) {
            return context;
        }
        return (T) prior.compensate(context);
    }

    private Middleware getLastLink() {
        return this.next == null ? this : this.next.getLastLink();
    }

    private Middleware getFirstLink() {
        return this.prior == null ? this : this.prior.getFirstLink();
    }
}
