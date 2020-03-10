package org.breedinginsight;

import com.zaxxer.hikari.HikariConfig;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import javax.inject.Singleton;

@Singleton
public class HikariConfiguration implements BeanInitializedEventListener<HikariConfig> {

    @Override
    public HikariConfig onInitialized(BeanInitializingEvent<HikariConfig> configurationEvent)
    {

        // Initialize container using TestContainers
        GenericContainer dbContainer = new GenericContainer<>("postgis/postgis:12-3.0")
                .withImagePullPolicy(PullPolicy.alwaysPull())
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", "bitest")
                .withEnv("POSTGRES_PASSWORD", "postgres")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections*\\n", 1));

        dbContainer.start();
        Integer containerPort = dbContainer.getMappedPort(5432);

        HikariConfig configuration = configurationEvent.getBean();

        configuration.setJdbcUrl(String.format("jdbc:postgresql://localhost:%s/bitest", containerPort));

        return configuration;
    }
}
