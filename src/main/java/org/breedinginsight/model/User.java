package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.jooq.Record;

import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
public class User extends BiUserEntity{

    @JsonInclude(value= JsonInclude.Include.ALWAYS)
    @NotNull
    private List<SystemRole> systemRoles;

    public User(BiUserEntity biUser) {
        this.setId(biUser.getId());
        this.setOrcid(biUser.getOrcid());
        this.setName(biUser.getName());
        this.setEmail(biUser.getEmail());
        this.setSystemRoles(new ArrayList<>());
    }

    public User() {
        this.setSystemRoles(new ArrayList<>());
    }

    public static User parseSQLRecord(Record record, @NotNull BiUserTable tableName){
        return User.builder()
                .id(record.getValue(tableName.ID))
                .orcid(record.getValue(tableName.ORCID))
                .name(record.getValue(tableName.NAME))
                .email(record.getValue(tableName.EMAIL))
                .systemRoles(new ArrayList<>())
                .build();
    }

    public static User parseSQLRecord(Record record) {
        return parseSQLRecord(record, BI_USER);
    }

    public void addRole(SystemRole role) {
        systemRoles.add(role);
    }
}
