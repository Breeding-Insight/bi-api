/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.ProgramUserRoleDao;
import org.breedinginsight.model.Program;
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

    public boolean existsAndActive(UUID programId, UUID userId) {
        return dsl.selectCount().from(PROGRAM_USER_ROLE)
                .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId)
                        .and(PROGRAM_USER_ROLE.USER_ID.eq(userId))
                        .and(PROGRAM_USER_ROLE.ACTIVE.eq(true)))
                .fetchOne(0, Integer.class) == 1;
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

    public void archiveProgramUsersByUserId(UUID userId) {
        dsl.update(PROGRAM_USER_ROLE)
                .set(PROGRAM_USER_ROLE.ACTIVE, false)
                .where(PROGRAM_USER_ROLE.USER_ID.eq(userId))
                .execute();
    }

    public ProgramUser getProgramUser(UUID programId, UUID userId) {

        if (programId != null && userId != null) {
            BiUserTable createdByUser = BI_USER.as("createdByUser");
            BiUserTable updatedByUser = BI_USER.as("updatedByUser");
            Result<Record> records = getProgramUsersQuery(createdByUser, updatedByUser)
                    .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId)
                    .and(PROGRAM_USER_ROLE.USER_ID.eq(userId)))
                    .fetch();

            List<ProgramUser> programUsers = parseRecords(records, createdByUser, updatedByUser);
            if (programUsers.size() == 1){
                return programUsers.get(0);
            } else {
                return null;
            }
        }

        return null;
    }

    public List<ProgramUser> getProgramUsers(UUID programId){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // TODO: When we allow for pulling archived users, active condition won't be hardcoded.
        Result<Record> records = getProgramUsersQuery(createdByUser, updatedByUser)
                .where(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId))
                .and(PROGRAM_USER_ROLE.ACTIVE.eq(true))
                .fetch();

        return parseRecords(records, createdByUser, updatedByUser);
    }

    public List<ProgramUser> getProgramUsersByUserId(UUID userId) {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // TODO: When we allow for pulling archived users, active condition won't be hardcoded.
        Result<Record> records = getProgramUsersQuery(createdByUser, updatedByUser)
                .where(PROGRAM_USER_ROLE.USER_ID.eq(userId))
                .and(PROGRAM.ACTIVE.eq(true))
                .fetch();

        return parseRecords(records, createdByUser, updatedByUser);
    }

    public List<ProgramUser> getProgramUsersByOrcid(String orcid) {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // TODO: When we allow for pulling archived users, active condition won't be hardcoded.
        Result<Record> records = getProgramUsersQuery(createdByUser, updatedByUser)
                .where(BI_USER.ORCID.eq(orcid))
                .and(PROGRAM.ACTIVE.eq(true))
                .fetch();

        return parseRecords(records, createdByUser, updatedByUser);
    }

    private SelectOnConditionStep<Record> getProgramUsersQuery(BiUserTable createdByTableAlias, BiUserTable updatedByTableAlias) {

        return dsl.select()
                .from(PROGRAM_USER_ROLE)
                .join(BI_USER).on(PROGRAM_USER_ROLE.USER_ID.eq(BI_USER.ID))
                .join(ROLE).on(PROGRAM_USER_ROLE.ROLE_ID.eq(ROLE.ID))
                .join(PROGRAM).on(PROGRAM_USER_ROLE.PROGRAM_ID.eq(PROGRAM.ID))
                .join(createdByTableAlias).on(PROGRAM_USER_ROLE.CREATED_BY.eq(createdByTableAlias.ID))
                .join(updatedByTableAlias).on(PROGRAM_USER_ROLE.UPDATED_BY.eq(updatedByTableAlias.ID));

    }

    public List<ProgramUser> getAllProgramUsers() {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        Result<Record> records = getProgramUsersQuery(createdByUser, updatedByUser)
                .where(PROGRAM.ACTIVE.eq(true))
                .fetch();

        return parseRecords(records, createdByUser, updatedByUser);
    }

    public List<ProgramUser> parseRecords(Result<Record> records, BiUserTable createdByUser, BiUserTable updatedByUser){

        List<ProgramUser> resultProgramsUsers = new ArrayList<>();

        for (Record record: records){
            // program user exists
            ProgramUser programUser = ProgramUser.parseSQLRecord(record);
            Role role = Role.parseSQLRecord(record);
            Program program = Program.parseSQLRecord(record);
            Optional<ProgramUser> existingUser = resultProgramsUsers.stream()
                    .filter(p -> p.getProgramId().equals(programUser.getProgramId()) &&
                            p.getUserId().equals(programUser.getUserId()))
                    .findFirst();

            ProgramUser user;
            if (existingUser.isPresent()) {
                // add another role
                user = existingUser.get();
                user.addRole(role);
            } else {
                // add new program user with role
                programUser.setUser(User.parseSQLRecord(record));
                programUser.addRole(role);
                programUser.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
                programUser.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
                user = programUser;
                resultProgramsUsers.add(user);
            }

            if (user.getProgram() == null){
                user.setProgram(program);
            }
        }

        return resultProgramsUsers;
    }

    public List<ProgramUser> getProgramUsersByRole(UUID programId, UUID roleId) {
        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        Result<Record> records = getProgramUsersQuery(createdByUser, updatedByUser)
                .where(PROGRAM.ACTIVE.eq(true))
                .and(PROGRAM.ID.eq(programId))
                .and(ROLE.ID.eq(roleId))
                .fetch();

        return parseRecords(records, createdByUser, updatedByUser);
    }
}
