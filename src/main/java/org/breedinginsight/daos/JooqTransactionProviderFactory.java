package org.breedinginsight.daos;

import io.micronaut.configuration.jooq.JooqConfigurationFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.*;
import io.micronaut.context.annotation.Parameter;
import org.jooq.*;
import org.jooq.Configuration;
import org.jooq.conf.Settings;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.ThreadLocalTransactionProvider;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Factory
public class JooqTransactionProviderFactory {


    @EachBean(DataSource.class)
    @Parameter
    public TransactionProvider getTransactionProvider(DataSource dataSource) {
        DataSourceConnectionProvider cp = new DataSourceConnectionProvider(dataSource);
        return new ThreadLocalTransactionProvider(cp);
    }

}
