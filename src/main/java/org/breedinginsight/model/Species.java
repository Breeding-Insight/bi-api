package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.SPECIES;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class Species extends SpeciesEntity {

    public Species(SpeciesEntity speciesEntity){
        this.setId(speciesEntity.getId());
        this.setCommonName(speciesEntity.getCommonName());
    }

    public static Species parseSQLRecord(Record record) {
        return Species.builder()
                .id(record.getValue(SPECIES.ID))
                .commonName(record.getValue(SPECIES.COMMON_NAME))
                .build();
    }
}
