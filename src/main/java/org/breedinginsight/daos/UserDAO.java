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
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static org.breedinginsight.dao.db.Tables.*;

// need annotation to find bean in micronaut test context
@Singleton
public class UserDAO extends BiUserDao {

    private DSLContext dsl;
    @Inject
    public UserDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<User> getUsers() {
        List<Record> records = getUsersQuery()
                .where(BI_USER.ACTIVE.eq(true)).fetch();
        return parseRecords(records);
    }

    public Optional<User> getUser(UUID id) {
        List<Record> records = getUsersQuery()
                .where(BI_USER.ID.eq(id))
                .fetch();
        List<User> users = parseRecords(records);

        if (users.size() > 0){
            return Optional.of(users.get(0));
        } else {
            return Optional.empty();
        }
    }

    public Optional<User> getUserByOrcId(String orcid) {
        List<Record> records = getUsersQuery()
                .where(BI_USER.ORCID.eq(orcid))
                .fetch();
        List<User> users = parseRecords(records);

        if (users.size() > 0){
            return Optional.of(users.get(0));
        } else {
            return Optional.empty();
        }
    }

    private SelectOnConditionStep<Record> getUsersQuery(){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        return dsl.select()
                .from(BI_USER)
                .leftJoin(SYSTEM_USER_ROLE).on(BI_USER.ID.eq(SYSTEM_USER_ROLE.BI_USER_ID))
                .leftJoin(SYSTEM_ROLE).on(SYSTEM_USER_ROLE.SYSTEM_ROLE_ID.eq(SYSTEM_ROLE.ID))
                .leftJoin(PROGRAM_USER_ROLE).on(PROGRAM_USER_ROLE.USER_ID.eq(BI_USER.ID))
                .leftJoin(PROGRAM).on(PROGRAM_USER_ROLE.PROGRAM_ID.eq(PROGRAM.ID))
                    .and(PROGRAM.ACTIVE.eq(true));
    }

    private List<User> parseRecords(List<Record> records) {

        Map<String, User> userMap = new HashMap<>();

        // Parse the result
        for (Record record : records) {
            // program user exists
            User userRecord = User.parseSQLRecord(record);
            SystemRole systemRole = SystemRole.parseSQLRecord(record);
            Program program = Program.parseSQLRecord(record);

            User existingUser;
            if (userMap.containsKey(userRecord.getId().toString())) {
                existingUser = userMap.get(userRecord.getId().toString());
            } else {
                userMap.put(userRecord.getId().toString(), userRecord);
                existingUser = userRecord;
            }

            // Add our system role
            if (systemRole.getDomain() != null) {
                Boolean systemRoleExists = Utilities.existsInList(existingUser.getSystemRoles(),
                        systemRole, SystemRole::getId);
                if (!systemRoleExists){
                    existingUser.addRole(systemRole);
                }
            }

            // Add our program
            if (program.getId() != null){
                Boolean programExists = Utilities.existsInList(existingUser.getActivePrograms(),
                        program, Program::getId);
                if (!programExists) {
                    existingUser.addActiveProgram(program);
                }
            }

        }

        return new ArrayList<>(userMap.values());
    }

}
