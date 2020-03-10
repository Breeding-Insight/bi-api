package org.breedinginsight;

import com.zaxxer.hikari.HikariConfig;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;

import javax.inject.Singleton;

@Singleton
public class HikariConfiguration implements BeanInitializedEventListener<HikariConfig> {

    @Override
    public HikariConfig onInitialized(BeanInitializingEvent<HikariConfig> configurationEvent)
    {

        // Initialize container using TestContainers

        HikariConfig configuration = configurationEvent.getBean();
        // TODO: Get testcontainer port and put here
        configuration.setJdbcUrl("jdbc:postgresql://localhost:8765/bitest");

        return configuration;
    }
}
