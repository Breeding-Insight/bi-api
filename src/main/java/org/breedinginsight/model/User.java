package org.breedinginsight.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.jooq.Record;

import javax.validation.constraints.NotNull;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class User extends BiUserEntity{

    public User(BiUserEntity biUser) {
        this.setId(biUser.getId());
        this.setOrcid(biUser.getOrcid());
        this.setName(biUser.getName());
        this.setEmail(biUser.getEmail());
    }

    public static User parseSQLRecord(Record record, @NotNull BiUserTable tableName){
        return User.builder()
                .id(record.getValue(tableName.ID))
                .name(record.getValue(tableName.NAME))
                .email(record.getValue(tableName.EMAIL))
                .build();
    }

    public static User parseSQLRecord(Record record) {
        return parseSQLRecord(record, BI_USER);
    }
}
