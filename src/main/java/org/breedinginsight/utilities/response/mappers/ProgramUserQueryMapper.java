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
import java.util.stream.Collectors;

@Getter
@Singleton
@Context
public class ProgramUserQueryMapper  extends AbstractQueryMapper {

    private Map<String, MapperEntry<ProgramUser>> fields;

    public ProgramUserQueryMapper() throws NoSuchMethodException {
        fields = Map.ofEntries(
                Map.entry("name", new MapperEntry<>(programUser -> programUser.getUser() != null ? programUser.getUser().getName() : null,
                        User.class.getMethod("getName").getReturnType())),
                Map.entry("email", new MapperEntry<>(programUser -> programUser.getUser() != null ? programUser.getUser().getEmail() : null,
                        User.class.getMethod("getEmail").getReturnType())),
                Map.entry("roles", new MapperEntry<>(
                        programUser ->
                                programUser.getRoles() != null ? programUser.getRoles().stream().map(Role::getDomain).collect(Collectors.toList()) : null,
                        Role.class.getMethod("getDomain").getReturnType())),
                Map.entry("active", new MapperEntry<>(ProgramUser::getActive,
                        ProgramUser.class.getMethod("getActive").getReturnType())),
                Map.entry("createdAt", new MapperEntry<>(ProgramUser::getCreatedAt,
                        ProgramUser.class.getMethod("getCreatedAt").getReturnType())),
                Map.entry("updatedAt", new MapperEntry<>(ProgramUser::getUpdatedAt,
                        ProgramUser.class.getMethod("getUpdatedAt").getReturnType())),
                Map.entry("createdByUserId",
                        new MapperEntry<>(programUser -> programUser.getCreatedByUser() != null ? programUser.getCreatedByUser().getId() : null,
                                User.class.getMethod("getId").getReturnType())),
                Map.entry("createdByUserName",
                        new MapperEntry<>(programUser -> programUser.getCreatedByUser() != null ? programUser.getCreatedByUser().getName() : null,
                                User.class.getMethod("getName").getReturnType())),
                Map.entry("updatedByUserId",
                        new MapperEntry<>(programUser -> programUser.getUpdatedByUser() != null ? programUser.getUpdatedByUser().getId() : null,
                                User.class.getMethod("getId").getReturnType())),
                Map.entry("updatedByUserName",
                        new MapperEntry<>(programUser -> programUser.getUpdatedByUser() != null ? programUser.getUpdatedByUser().getName() : null,
                                User.class.getMethod("getName").getReturnType()))
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public MapperEntry<ProgramUser> getMapperEntry(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
