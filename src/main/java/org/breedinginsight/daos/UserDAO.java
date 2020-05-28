package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                .leftJoin(SYSTEM_ROLE).on(SYSTEM_USER_ROLE.SYSTEM_ROLE_ID.eq(SYSTEM_ROLE.ID));
    }

    private List<User> parseRecords(List<Record> records) {

        List<User> resultUsers = new ArrayList<>();

        // Parse the result
        for (Record record : records) {
            // program user exists
            User userRecord = User.parseSQLRecord(record);
            SystemRole systemRole = SystemRole.parseSQLRecord(record);
            Optional<User> existingUser = resultUsers.stream()
                    .filter(p -> p.getId().equals(userRecord.getId()))
                    .findFirst();

            User user;
            if (!existingUser.isPresent()) {
                resultUsers.add(userRecord);
                user = userRecord;
            } else {
                user = existingUser.get();
            }

            if (systemRole.getDomain() != null) {
                user.addRole(systemRole);
            }
        }

        return resultUsers;
    }

}
