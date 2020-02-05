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
@Replaces(factory = JooqConfigurationFactory.class)
public class JooqTransactionProviderFactory extends JooqConfigurationFactory {


    @EachBean(DataSource.class)
    @Parameter
    public TransactionProvider getTransactionProvider(DataSource dataSource) {
        DataSourceConnectionProvider cp = new DataSourceConnectionProvider(dataSource);
        return new ThreadLocalTransactionProvider(cp);
    }

    @Override
    public Configuration jooqConfiguration(String name,
                                           DataSource dataSource,
                                           @Parameter @Nullable TransactionProvider transactionProvider,
                                           @Parameter @Nullable Settings settings,
                                           @Parameter @Nullable ExecutorProvider executorProvider,
                                           @Parameter @Nullable RecordMapperProvider recordMapperProvider,
                                           @Parameter @Nullable RecordUnmapperProvider recordUnmapperProvider,
                                           @Parameter @Nullable MetaProvider metaProvider,
                                           ApplicationContext ctx) {
        Configuration config = super.jooqConfiguration(name, dataSource, transactionProvider, settings, executorProvider, recordMapperProvider, recordUnmapperProvider, metaProvider, ctx);

        if(config instanceof DefaultConfiguration) {
            ((DefaultConfiguration) config).setTransactionProvider(new ThreadLocalTransactionProvider(config.connectionProvider()));
        }

        return config;
    }
}
