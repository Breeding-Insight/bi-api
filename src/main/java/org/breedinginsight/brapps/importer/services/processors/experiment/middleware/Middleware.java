package org.breedinginsight.brapps.importer.services.processors.experiment.middleware;

public abstract class Middleware<T> {

    Middleware next;
    Middleware prior;

    /**
     * Builds chains of middleware objects.
     */
    public static Middleware link(Middleware first, Middleware... chain) {
        Middleware head = first;
        for (Middleware nextInChain: chain) {
            nextInChain.prior = head;
            head.next = nextInChain;
            head = nextInChain;
        }
        return first;
    }

    /**
     * Subclasses will implement this local transaction.
     */
    public abstract boolean process(T context);
    /**
     * Subclasses will implement this method to handle errors and possibly undo the local transaction.
     */
    public abstract boolean compensate(T context);
    /**
     * Processes the next local transaction or ends traversing if we're at the
     * last local transaction of the transaction.
     */
    protected boolean processNext(T context) {
        if (next == null) {
            return true;
        }
        return next.process(context);
    }

    /**
     * Runs the compensating local transaction for the prior local transaction or ends traversing if
     * we're at the first local transaction of the transaction.
     */
    protected boolean compensatePrior(T context) {
        if (prior == null) {
            return true;
        }
        return prior.compensate(context);
    }
}
