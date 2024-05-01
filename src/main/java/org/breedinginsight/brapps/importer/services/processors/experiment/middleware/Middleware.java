package org.breedinginsight.brapps.importer.services.processors.experiment.middleware;

public abstract class Middleware<T> {

    Middleware next;

    /**
     * Builds chains of middleware objects.
     */
    public static Middleware link(Middleware first, Middleware... chain) {
        Middleware head = first;
        for (Middleware nextInChain: chain) {
            head.next = nextInChain;
            head = nextInChain;
        }
        return first;
    }

    /**
     * Subclasses will implement this method with processing steps.
     */
    public abstract boolean process(T context);

    /**
     * Runs check on the next object in chain or ends traversing if we're in
     * last object in chain.
     */
    protected boolean processNext(T context) {
        if (next == null) {
            return true;
        }
        return next.process(context);
    }
}
