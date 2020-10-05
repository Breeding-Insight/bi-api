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

import io.micronaut.context.annotation.Context;
import lombok.Getter;
import org.breedinginsight.model.*;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Singleton
@Context
public class ProgramUserQueryMapper  extends AbstractQueryMapper {

    private Map<String, Function<ProgramUser,?>> fields;

    public ProgramUserQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name",
                        (programUser) -> programUser.getUser() != null ? programUser.getUser().getName() : null),
                Map.entry("email",
                        programUser -> programUser.getUser() != null ? programUser.getUser().getEmail() : null),
                Map.entry("roles",
                        programUser ->
                                programUser.getRoles() != null ? programUser.getRoles().stream().map(Role::getDomain).collect(Collectors.toList()) : null),
                Map.entry("active", ProgramUser::getActive),
                Map.entry("createdAt", ProgramUser::getCreatedAt),
                Map.entry("updatedAt", ProgramUser::getUpdatedAt),
                Map.entry("createdByUserId",
                        programUser -> programUser.getCreatedByUser() != null ? programUser.getCreatedByUser().getId() : null),
                Map.entry("createdByUserName",
                        programUser -> programUser.getCreatedByUser() != null ? programUser.getCreatedByUser().getName() : null),
                Map.entry("updatedByUserId",
                        programUser -> programUser.getUpdatedByUser() != null ? programUser.getUpdatedByUser().getId() : null),
                Map.entry("updatedByUserName",
                        programUser -> programUser.getUpdatedByUser() != null ? programUser.getUpdatedByUser().getName() : null)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<ProgramUser, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
