package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.SystemRoleEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.SYSTEM_ROLE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class SystemRole extends SystemRoleEntity {

    public SystemRole(SystemRoleEntity roleEntity){
        this.setId(roleEntity.getId());
        this.setDomain(roleEntity.getDomain());
    }

    public static SystemRole parseSQLRecord(Record record) {
        return SystemRole.builder()
                .id(record.getValue(SYSTEM_ROLE.ID))
                .domain(record.getValue(SYSTEM_ROLE.DOMAIN))
                .build();
    }

}
