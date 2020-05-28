package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.List;

import static org.breedinginsight.dao.db.Tables.PROGRAM_USER_ROLE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "createdBy", "updatedBy", "programId", "userId", "id" })
public class ProgramUser extends ProgramUserRoleEntity {

    private User createdByUser;
    private User updatedByUser;

    private User user;
    private List<Role> roles;

    public static ProgramUser parseSQLRecord(Record record){
        // Generate our program record
        ProgramUser programUser = ProgramUser.builder()
                .id(record.getValue(PROGRAM_USER_ROLE.ID))
                .roles(new ArrayList<>())
                .programId(record.getValue(PROGRAM_USER_ROLE.PROGRAM_ID))
                .userId(record.getValue(PROGRAM_USER_ROLE.USER_ID))
                .createdAt(record.getValue(PROGRAM_USER_ROLE.CREATED_AT))
                .updatedAt(record.getValue(PROGRAM_USER_ROLE.UPDATED_AT))
                .createdBy(record.getValue(PROGRAM_USER_ROLE.CREATED_BY))
                .updatedBy(record.getValue(PROGRAM_USER_ROLE.UPDATED_BY))
                .active(record.getValue(PROGRAM_USER_ROLE.ACTIVE))
                .build();

        return programUser;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

}
