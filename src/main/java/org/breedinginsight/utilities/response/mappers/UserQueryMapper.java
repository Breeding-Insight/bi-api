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

package org.breedinginsight.utilities.response.mappers;

import lombok.Getter;
import org.breedinginsight.model.User;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Singleton
public class UserQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<User,?>> fields;

    public UserQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name", User::getName),
                Map.entry("email", User::getEmail),
                Map.entry("orcid", User::getOrcid),
                Map.entry("systemRoles",
                        user -> user.getSystemRoles() != null ? user.getSystemRoles().stream()
                                .map(role -> role.getDomain()).collect(Collectors.toList()) : null),
                Map.entry("programs",
                        user -> user.getProgramRoles() != null ?
                                user.getProgramRoles().stream()
                                        .map(programRole -> programRole.getProgram() != null ? programRole.getProgram().getName() : null)
                                        .collect(Collectors.toList()) : null),
                Map.entry("active", User::getActive),
                Map.entry("createdAt", User::getCreatedAt),
                Map.entry("updatedAt", User::getUpdatedAt),
                Map.entry("createdByUserId", User::getCreatedBy),
                Map.entry("updatedByUserId", User::getUpdatedBy)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<User, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
