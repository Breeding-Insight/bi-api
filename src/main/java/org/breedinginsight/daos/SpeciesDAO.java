package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.daos.SpeciesDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SpeciesDAO extends SpeciesDao {

    private DSLContext dsl;
    @Inject
    public SpeciesDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

}
