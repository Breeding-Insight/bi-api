package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.AccessibilityOptionEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.ACCESSIBILITY_OPTION;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class Accessibility extends AccessibilityOptionEntity {
    public Accessibility(AccessibilityOptionEntity accessibilityEntity) {
        this.setId(accessibilityEntity.getId());
        this.setName(accessibilityEntity.getName());
    }

    public static Accessibility parseSQLRecord(Record record) {
        return Accessibility.builder()
                .id(record.getValue(ACCESSIBILITY_OPTION.ID))
                .name(record.getValue(ACCESSIBILITY_OPTION.NAME))
                .build();
    }
}
