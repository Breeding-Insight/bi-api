package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.CountryEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.COUNTRY;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class Country extends CountryEntity {

    public Country(CountryEntity countryEntity){
        this.setId(countryEntity.getId());
        this.setName(countryEntity.getName());
        this.setAlpha_2Code(countryEntity.getAlpha_2Code());
        this.setAlpha_3Code(countryEntity.getAlpha_3Code());
    }

    public static Country parseSQLRecord(Record record) {
        return Country.builder()
                .id(record.getValue(COUNTRY.ID))
                .name(record.getValue(COUNTRY.NAME))
                .alpha_2Code(record.getValue(COUNTRY.ALPHA_2_CODE))
                .alpha_3Code(record.getValue(COUNTRY.ALPHA_3_CODE))
                .build();
    }
}
