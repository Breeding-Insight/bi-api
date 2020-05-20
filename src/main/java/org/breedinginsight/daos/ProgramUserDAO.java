package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.ProgramUserRoleDao;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.User;
import org.breedinginsight.model.Role;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramUserDAO extends ProgramUserRoleDao {

    private DSLContext dsl;
    @Inject
    public ProgramUserDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public void deleteProgramUserRoles(UUID programId, UUID userId) {
        dsl.delete(PROGRAM_USER_ROLE)
                .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId).and(PROGRAM_USER_ROLE.USER_ID.eq(userId))).execute();
    }

    public void archiveProgramUserRoles(UUID programId, UUID userId) {
        dsl.update(PROGRAM_USER_ROLE)
                .set(PROGRAM_USER_ROLE.ACTIVE, false)
                .where(PROGRAM_USER_ROLE.USER_ID.eq(userId)).and(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId))
                .execute();
    }

    public ProgramUser getProgramUser(UUID programId, UUID userId) {
        ProgramUser user = null;

        if (programId != null && userId != null) {
            BiUserTable createdByUser = BI_USER.as("createdByUser");
            BiUserTable updatedByUser = BI_USER.as("updatedByUser");
            Result<Record> records = dsl.select()
                    .from(PROGRAM_USER_ROLE)
                    .join(BI_USER).on(PROGRAM_USER_ROLE.USER_ID.eq(BI_USER.ID))
                    .join(ROLE).on(PROGRAM_USER_ROLE.ROLE_ID.eq(ROLE.ID))
                    .join(createdByUser).on(PROGRAM_USER_ROLE.CREATED_BY.eq(createdByUser.ID))
                    .join(updatedByUser).on(PROGRAM_USER_ROLE.UPDATED_BY.eq(updatedByUser.ID))
                    .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId).and(PROGRAM_USER_ROLE.USER_ID.eq(userId)))
                    .fetch();

            // populate program, user info
            if (records.isNotEmpty()) {
                user = ProgramUser.parseSQLRecord(records.get(0));

                // assumes created/edited is the same for all role records
                user.setCreatedByUser(User.parseSQLRecord(records.get(0), createdByUser));
                user.setUpdatedByUser(User.parseSQLRecord(records.get(0), updatedByUser));
                user.setUser(User.parseSQLRecord(records.get(0)));

                // populate roles
                for (Record record : records) {
                    Role role = Role.parseSQLRecord(record);
                    user.addRole(role);
                }
            }
        }

        return user;
    }

    public List<ProgramUser> getProgramUsers(UUID programId){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");
        List<ProgramUser> resultProgramsUsers = new ArrayList<>();

        // TODO: When we allow for pulling archived users, active condition won't be hardcoded.
        Result<Record> records = dsl.select()
                .from(PROGRAM_USER_ROLE)
                .join(BI_USER).on(PROGRAM_USER_ROLE.USER_ID.eq(BI_USER.ID))
                .join(ROLE).on(PROGRAM_USER_ROLE.ROLE_ID.eq(ROLE.ID))
                .join(createdByUser).on(PROGRAM_USER_ROLE.CREATED_BY.eq(createdByUser.ID))
                .join(updatedByUser).on(PROGRAM_USER_ROLE.UPDATED_BY.eq(updatedByUser.ID))
                .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId))
                .and(PROGRAM_USER_ROLE.ACTIVE.eq(true))
                .fetch();

        // Parse the result
        for (Record record: records){
            // program user exists
            ProgramUser programUser = ProgramUser.parseSQLRecord(record);
            Role role = Role.parseSQLRecord(record);
            Optional<ProgramUser> existingUser = resultProgramsUsers.stream()
                    .filter(p -> p.getProgramId().equals(programUser.getProgramId()) &&
                                 p.getUserId().equals(programUser.getUserId()))
                    .findFirst();

            if (existingUser.isPresent()) {
                // add another role
                ProgramUser user = existingUser.get();
                user.addRole(role);
            }
            else {
                // add new program user with role
                programUser.setUser(User.parseSQLRecord(record));
                programUser.addRole(role);
                programUser.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
                programUser.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
                resultProgramsUsers.add(programUser);
            }
        }

        return resultProgramsUsers;
    }

}
