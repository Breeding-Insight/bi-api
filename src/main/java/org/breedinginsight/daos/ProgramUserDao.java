package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;
import static org.breedinginsight.dao.db.Tables.PROGRAM;

@Singleton
public class ProgramUserDao extends org.breedinginsight.dao.db.tables.daos.ProgramUserRoleDao {

    @Inject
    DSLContext dsl;
    @Inject
    public ProgramUserDao(Configuration config) {
        super(config);
    }

    public void deleteProgramUserRoles(UUID programId, UUID userId) {
        dsl.delete(PROGRAM_USER_ROLE)
                .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId).and(PROGRAM_USER_ROLE.USER_ID.eq(userId))).execute();
    }



}
