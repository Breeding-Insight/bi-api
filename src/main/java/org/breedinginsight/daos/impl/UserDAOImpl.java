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

package org.breedinginsight.daos.impl;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.breedinginsight.daos.ProgramUserDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static org.breedinginsight.dao.db.Tables.*;

// need annotation to find bean in micronaut test context
@Singleton
public class UserDAOImpl extends BiUserDao implements UserDAO {

    private DSLContext dsl;
    @Inject
    ProgramUserDAO programUserDAO;

    @Inject
    public UserDAOImpl(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<User> getUsers() {
        List<Record> records = getUsersQuery()
                .where(BI_USER.ACTIVE.eq(true)).fetch();
        List<ProgramUser> programUsers = programUserDAO.getAllProgramUsers();
        return parseRecords(records, programUsers);
    }

    public Optional<User> getUser(UUID id) {
        List<Record> records = getUsersQuery()
                .where(BI_USER.ID.eq(id))
                .fetch();
        List<ProgramUser> programUsers = programUserDAO.getProgramUsersByUserId(id);
        List<User> users = parseRecords(records, programUsers);

        return Utilities.getSingleOptional(users);
    }

    public Optional<User> getUserByOrcId(String orcid) {
        List<Record> records = getUsersQuery()
                .where(BI_USER.ORCID.eq(orcid))
                .fetch();
        List<ProgramUser> programUsers = programUserDAO.getProgramUsersByOrcid(orcid);
        List<User> users = parseRecords(records, programUsers);

        return Utilities.getSingleOptional(users);
    }

    private SelectOnConditionStep<Record> getUsersQuery(){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        return dsl.select()
                .from(BI_USER)
                .leftJoin(SYSTEM_USER_ROLE).on(BI_USER.ID.eq(SYSTEM_USER_ROLE.BI_USER_ID))
                .leftJoin(SYSTEM_ROLE).on(SYSTEM_USER_ROLE.SYSTEM_ROLE_ID.eq(SYSTEM_ROLE.ID))
                .join(createdByUser).on(BI_USER.CREATED_BY.eq(createdByUser.ID))
                .join(updatedByUser).on(BI_USER.UPDATED_BY.eq(updatedByUser.ID));

    }



    private List<User> parseRecords(List<Record> userRecords, List<ProgramUser> programUsers) {

        Map<String, User> userMap = new HashMap<>();

        // Parse the result
        for (Record record : userRecords) {
            // program user exists
            User userRecord = User.parseSQLRecord(record);
            SystemRole systemRole = SystemRole.parseSQLRecord(record);

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
        }

        for (ProgramUser programUser: programUsers){
            if (userMap.containsKey(programUser.getUserId().toString())){
                User user = userMap.get(programUser.getUserId().toString());
                user.addProgramUser(programUser);
            }
        }

        return new ArrayList<>(userMap.values());
    }

}
