package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.daos.PlaceDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProgramLocationDAO extends PlaceDao {
    private DSLContext dsl;
    @Inject
    public ProgramLocationDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }
}
