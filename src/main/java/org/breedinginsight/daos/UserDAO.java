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
import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;
import org.breedinginsight.model.Role;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.dao.db.Tables.*;

// need annotation to find bean in micronaut test context
@Singleton
public class UserDAO extends BiUserDao {

    private DSLContext dsl;

    private String programUserTableAlias = "program_user_roles_";
    private String programTableAlias = "program_";
    private String roleTableAlias = "role_";
    @Inject
    public UserDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<User> getUsers() {
        List<Record> records = getUsersQuery(programUserTableAlias, programTableAlias, roleTableAlias)
                .where(BI_USER.ACTIVE.eq(true)).fetch();
        return parseRecords(records, programUserTableAlias, programTableAlias, roleTableAlias);
    }

    public Optional<User> getUser(UUID id) {
        List<Record> records = getUsersQuery(programUserTableAlias, programTableAlias, roleTableAlias)
                .where(BI_USER.ID.eq(id))
                .fetch();
        List<User> users = parseRecords(records, programUserTableAlias, programTableAlias, roleTableAlias);

        if (users.size() > 0){
            return Optional.of(users.get(0));
        } else {
            return Optional.empty();
        }
    }

    public Optional<User> getUserByOrcId(String orcid) {
        List<Record> records = getUsersQuery(programUserTableAlias, programTableAlias, roleTableAlias)
                .where(BI_USER.ORCID.eq(orcid))
                .fetch();
        List<User> users = parseRecords(records, programUserTableAlias, programTableAlias, roleTableAlias);

        if (users.size() > 0){
            return Optional.of(users.get(0));
        } else {
            return Optional.empty();
        }
    }

    private SelectOnConditionStep<Record> getUsersQuery(String programUserRoleTableAlias, String programTableAlias, String roleTableAlias){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // Rename the columns for the inner join
        List<String> tableColumns = PROGRAM_USER_ROLE.fieldStream().map(field -> programUserRoleTableAlias + field.getName()).collect(Collectors.toList());
        tableColumns.addAll(PROGRAM.fieldStream().map(field -> programTableAlias + field.getName()).collect(Collectors.toList()));
        tableColumns.addAll(ROLE.fieldStream().map(field -> roleTableAlias + field.getName()).collect(Collectors.toList()));

        // Inner select statement
        Table programRoles = dsl.select()
                .from(PROGRAM_USER_ROLE)
                .leftJoin(PROGRAM).on(PROGRAM_USER_ROLE.PROGRAM_ID.eq(PROGRAM.ID))
                .leftJoin(ROLE).on(PROGRAM_USER_ROLE.ROLE_ID.eq(ROLE.ID))
                .where(PROGRAM.ACTIVE.eq(true))
                .asTable("programRoles", tableColumns.toArray(String[]::new));

        return dsl.select()
                .from(BI_USER)
                .leftJoin(SYSTEM_USER_ROLE).on(BI_USER.ID.eq(SYSTEM_USER_ROLE.BI_USER_ID))
                .leftJoin(SYSTEM_ROLE).on(SYSTEM_USER_ROLE.SYSTEM_ROLE_ID.eq(SYSTEM_ROLE.ID))
                .leftJoin(programRoles).on(programRoles.field(programUserRoleTableAlias + "user_id").eq(BI_USER.ID));

    }

    private List<User> parseRecords(List<Record> records, String programUserRoleTableAlias, String programTableAlias, String roleTableAlias) {

        Map<String, User> userMap = new HashMap<>();

        // Parse the result
        for (Record record : records) {
            // program user exists
            User userRecord = User.parseSQLRecord(record);
            SystemRole systemRole = SystemRole.parseSQLRecord(record);
            ProgramUser programUser = ProgramUser.parseSQLRecord(record, programUserRoleTableAlias);
            Role programRole = Role.parseSQLRecord(record, roleTableAlias);
            Program program = Program.parseSQLRecord(record, programTableAlias);

            User existingUser;
            if (userMap.containsKey(userRecord.getId().toString())) {
                existingUser = userMap.get(userRecord.getId().toString());
            } else {
                userMap.put(userRecord.getId().toString(), userRecord);
                existingUser = userRecord;
            }

            // Add our system role
            if (systemRole.getDomain() != null) {
                Optional<SystemRole> systemRoleExists = Utilities.findInList(existingUser.getSystemRoles(),
                        systemRole, SystemRole::getId);
                if (!systemRoleExists.isPresent()){
                    existingUser.addRole(systemRole);
                }
            }

            // Add our program
            if (programUser.getProgramId() != null){
                Optional<ProgramUser> programUserExists = Utilities.findInList(existingUser.getProgramRoles(),
                        programUser, ProgramUser::getProgramId);
                if (programUserExists.isPresent()) {

                    ProgramUser existingProgramUser = programUserExists.get();

                    // Set role
                    Optional<Role> roleExists = Utilities.findInList(existingProgramUser.getRoles(),
                            programRole, Role::getId);
                    if (!roleExists.isPresent()){
                        existingProgramUser.addRole(programRole);
                    }
                } else {

                    // Set program
                    if (program.getId() != null){
                        programUser.setProgram(program);
                    }

                    // Set role
                    if (programRole.getId() != null){
                        programUser.addRole(programRole);
                    }

                    existingUser.addProgramUser(programUser);
                }
            }

        }

        return new ArrayList<>(userMap.values());
    }

}
