package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.TopographyOptionEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.TOPOGRAPHY_OPTION;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class Topography extends TopographyOptionEntity {
    public Topography(TopographyOptionEntity topographyEntity) {
        this.setId(topographyEntity.getId());
        this.setName(topographyEntity.getName());
    }

    public static Topography parseSQLRecord(Record record) {
        return Topography.builder()
                .id(record.getValue(TOPOGRAPHY_OPTION.ID))
                .name(record.getValue(TOPOGRAPHY_OPTION.NAME))
                .build();
    }
}
