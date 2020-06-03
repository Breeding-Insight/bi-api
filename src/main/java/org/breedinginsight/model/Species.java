/*
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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
