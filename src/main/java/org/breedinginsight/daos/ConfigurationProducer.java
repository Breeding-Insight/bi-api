package org.breedinginsight.daos;

import io.micronaut.http.annotation.Produces;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;

public class ConfigurationProducer {

    @Inject
    DSLContext dsl;

    @Produces
    public Configuration config() {
        return dsl.configuration();
    }
}
