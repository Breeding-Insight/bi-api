package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.ROLE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class Role extends RoleEntity {

    public Role(RoleEntity roleEntity){
        this.setId(roleEntity.getId());
        this.setDomain(roleEntity.getDomain());
    }

    public static Role parseSQLRecord(Record record) {
        return Role.builder()
                .id(record.getValue(ROLE.ID))
                .domain(record.getValue(ROLE.DOMAIN))
                .build();
    }

}
