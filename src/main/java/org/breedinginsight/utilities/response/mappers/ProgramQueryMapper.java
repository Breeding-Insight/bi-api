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
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;

import javax.inject.Singleton;
import java.util.Map;


@Getter
@Singleton
@Context
public class ProgramQueryMapper extends AbstractQueryMapper {

    private Map<String, MapperEntry<Program>> fields;

    public ProgramQueryMapper() throws NoSuchMethodException {
        fields = Map.ofEntries(
                Map.entry("name", new MapperEntry<>(Program::getName,
                        Program.class.getMethod("getName").getReturnType())),
                Map.entry("abbreviation", new MapperEntry<>(Program::getAbbreviation,
                        Program.class.getMethod("getAbbreviation").getReturnType())),
                Map.entry("objective", new MapperEntry<>(Program::getObjective,
                        Program.class.getMethod("getObjective").getReturnType())),
                Map.entry("documentationUrl", new MapperEntry<>(Program::getDocumentationUrl,
                        Program.class.getMethod("getDocumentationUrl").getReturnType())),
                Map.entry("active", new MapperEntry<>(Program::getActive,
                        Program.class.getMethod("getActive").getReturnType())),
                Map.entry("createdAt", new MapperEntry<>(Program::getCreatedAt,
                        Program.class.getMethod("getCreatedAt").getReturnType())),
                Map.entry("updatedAt", new MapperEntry<>(Program::getUpdatedAt,
                        Program.class.getMethod("getUpdatedAt").getReturnType())),
                Map.entry("speciesId", new MapperEntry<>(Program::getSpeciesId,
                        Program.class.getMethod("getSpeciesId").getReturnType())),
                Map.entry("speciesName",
                        new MapperEntry<>((program) -> program.getSpecies() != null ? program.getSpecies().getCommonName() : null,
                                Species.class.getMethod("getCommonName").getReturnType())),
                Map.entry("createdByUserId",
                        new MapperEntry<>((program) -> program.getCreatedByUser() != null ? program.getCreatedByUser().getId() : null,
                                User.class.getMethod("getId").getReturnType())),
                Map.entry("createdByUserName",
                        new MapperEntry<>((program) -> program.getCreatedByUser() != null ? program.getCreatedByUser().getName() : null,
                                User.class.getMethod("getName").getReturnType())),
                Map.entry("updatedByUserId",
                        new MapperEntry<>((program) -> program.getUpdatedByUser() != null ? program.getUpdatedByUser().getId() : null,
                                User.class.getMethod("getId").getReturnType())),
                Map.entry("updatedByUserName",
                        new MapperEntry<>((program) -> program.getUpdatedByUser() != null ? program.getUpdatedByUser().getName() : null,
                                User.class.getMethod("getName").getReturnType()))
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public MapperEntry<Program> getMapperEntry(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
