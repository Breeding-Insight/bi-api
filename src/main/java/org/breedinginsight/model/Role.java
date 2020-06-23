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
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.jooq.Record;

import java.util.UUID;

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

    public static Role parseSQLRecord(Record record, String alias){
        return Role.builder()
                .id(record.getValue(alias + ROLE.ID.getName(), UUID.class))
                .domain(record.getValue(alias + ROLE.DOMAIN.getName(), String.class))
                .build();
    }

}
