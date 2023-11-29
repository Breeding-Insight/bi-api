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
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.jooq.Record;

import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@Introspected
@Jacksonized
@JsonIgnoreProperties(value = {"accountToken"})
public class User extends BiUserEntity {

    @JsonInclude()
    @NotNull
    private List<SystemRole> systemRoles;
    @JsonInclude()
    @NotNull
    @JsonIgnoreProperties(value = {"createdAt", "updatedAt", "createdByUser", "updatedByUser", "user"})
    private List<ProgramUser> programRoles;

    public User(BiUserEntity biUser) {
        this.setId(biUser.getId());
        this.setOrcid(biUser.getOrcid());
        this.setName(biUser.getName());
        this.setEmail(biUser.getEmail());
        this.setSystemRoles(new ArrayList<>());
        this.setProgramRoles(new ArrayList<>());
        this.setActive(biUser.getActive());
        this.setAccountToken(biUser.getAccountToken());
    }

    public User() {
        this.setSystemRoles(new ArrayList<>());
    }

    public static User parseSQLRecord(Record record, @NotNull BiUserTable tableName){
        return User.builder()
                .id(record.getValue(tableName.ID))
                .orcid(record.getValue(tableName.ORCID))
                .name(record.getValue(tableName.NAME))
                .email(record.getValue(tableName.EMAIL))
                .systemRoles(new ArrayList<>())
                .programRoles(new ArrayList<>())
                .active(record.getValue(tableName.ACTIVE))
                .accountToken(record.getValue(tableName.ACCOUNT_TOKEN))
                .build();
    }

    public static User parseSQLRecord(Record record) {
        return parseSQLRecord(record, BI_USER);
    }

    public void addRole(SystemRole role) {
        systemRoles.add(role);
    }

    public void addProgramUser(ProgramUser programUser) {programRoles.add(programUser); }
}
