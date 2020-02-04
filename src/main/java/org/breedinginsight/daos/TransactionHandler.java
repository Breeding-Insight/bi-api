package org.breedinginsight.daos;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import javax.inject.Inject;

public class TransactionHandler {

    @Inject
    DSLContext dsl;

    public void transaction(TransactionalRunnable jooqFunction) {
        // Use the jooq transaction to run our transaction

        dsl.transaction(configuration -> jooqFunction.run(configuration));
    }

    public <T> T transactionResult(TransactionalCallable<T> jooqFunction) {

        T result = dsl.transactionResult(configuration -> {
            return jooqFunction.run(configuration);
        });
        return result;
    }


}
