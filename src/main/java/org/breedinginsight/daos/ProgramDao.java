package org.breedinginsight.daos;

import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProgramDao extends org.breedinginsight.dao.db.tables.daos.ProgramDao {
    @Inject
    public ProgramDao(Configuration config) {
        super(config);
    }
}

