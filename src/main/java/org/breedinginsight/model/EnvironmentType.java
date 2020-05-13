package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.EnvironmentTypeEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.ENVIRONMENT_TYPE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class EnvironmentType extends EnvironmentTypeEntity {

    public EnvironmentType(EnvironmentTypeEntity environmentTypeEntity) {
        this.setId(environmentTypeEntity.getId());
        this.setName(environmentTypeEntity.getName());
    }

    public static EnvironmentType parseSQLRecord(Record record) {
        return EnvironmentType.builder()
                .id(record.getValue(ENVIRONMENT_TYPE.ID))
                .name(record.getValue(ENVIRONMENT_TYPE.NAME))
                .build();
    }
}
