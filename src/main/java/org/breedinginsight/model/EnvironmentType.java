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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.EnvironmentTypeEntity;
import org.jooq.Record;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnvironmentType that = (EnvironmentType) o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName());
    }
}
