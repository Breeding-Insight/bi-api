package org.breedinginsight.daos;

import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SpeciesDao extends org.breedinginsight.dao.db.tables.daos.SpeciesDao {

    @Inject
    DSLContext dsl;
    @Inject
    public SpeciesDao(Configuration config) {
        super(config);
    }
}
