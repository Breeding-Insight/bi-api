package org.breedinginsight.daos;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.ExperimentProgramUserRoleDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class ExperimentalCollaboratorDAO extends ExperimentProgramUserRoleDao {

    private DSLContext dsl;

    @Inject
    public ExperimentalCollaboratorDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }
}
