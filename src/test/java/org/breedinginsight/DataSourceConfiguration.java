package org.breedinginsight;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class DataSourceConfiguration implements BeanCreatedEventListener<DataSource> {

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> configurationEvent){

        // Get connection
        DataSource configuration = configurationEvent.getBean();

        // Init flyway on the database


        return configuration;
    }
}
