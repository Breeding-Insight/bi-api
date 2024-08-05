/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.CountryEntity;
import org.jooq.Record;

import java.util.Objects;

import static org.breedinginsight.dao.db.Tables.COUNTRY;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "alpha_2Code", "alpha_3Code" })
public class Country extends CountryEntity {

    // these getters/setters are to get correct camel case to/from json
    // jooq generator puts weird underscores in naming we have to deal with
    public String getAlpha2Code() {
        return super.getAlpha_2Code();
    }

    public String getAlpha3Code() {
        return super.getAlpha_3Code();
    }

    public void setAlpha2Code(String alpha2Code) {
        super.setAlpha_2Code(alpha2Code);
    }

    public void setAlpha3Code(String alpha3Code) {
        super.setAlpha_3Code(alpha3Code);
    }

    public Country(CountryEntity countryEntity) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Country that = (Country) o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName());
    }
}
