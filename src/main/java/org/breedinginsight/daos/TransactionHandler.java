package org.breedinginsight.daos;

import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import javax.inject.Inject;
import javax.swing.text.html.Option;
import java.util.Optional;
import java.util.concurrent.Callable;

public class TransactionHandler {

    @Inject
    DSLContext dsl;

    public <T> Optional<T> transaction(Callable<T> jooqFunction) {

        T result = dsl.transactionResult(configuration -> {
            // Run our sql
            return jooqFunction.call();
        });

        return Optional.ofNullable(result);
    }


}
