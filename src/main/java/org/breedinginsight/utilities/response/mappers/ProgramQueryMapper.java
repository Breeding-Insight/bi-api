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
import org.breedinginsight.model.Program;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;


@Getter
public class ProgramQueryMapper extends AbstractQueryMapper {

    private Map<String, MapperEntry<Program>> fields;

    public ProgramQueryMapper() {
        //TODO: Think about doing reflection here to get field class. This is prone to mistakes in field class
        fields = Map.ofEntries(
                Map.entry("name", new MapperEntry<>(Program::getName, String.class)),
                Map.entry("abbreviation", new MapperEntry<>(Program::getAbbreviation, String.class)),
                Map.entry("objective", new MapperEntry<>(Program::getObjective, String.class)),
                Map.entry("documentationUrl", new MapperEntry<>(Program::getDocumentationUrl, String.class)),
                Map.entry("active", new MapperEntry<>(Program::getActive, Boolean.class)),
                Map.entry("createdAt", new MapperEntry<>(Program::getCreatedAt, OffsetDateTime.class)),
                Map.entry("updatedAt", new MapperEntry<>(Program::getUpdatedAt, OffsetDateTime.class)),
                Map.entry("speciesId", new MapperEntry<>(Program::getSpeciesId, UUID.class)),
                Map.entry("speciesName",
                        new MapperEntry<>((program) -> program.getSpecies() != null ? program.getSpecies().getCommonName() : null,
                        String.class)),
                Map.entry("createdByUserId",
                        new MapperEntry<>((program) -> program.getCreatedByUser() != null ? program.getCreatedByUser().getId() : null,
                        UUID.class)),
                Map.entry("createdByUserName",
                        new MapperEntry<>((program) -> program.getCreatedByUser() != null ? program.getCreatedByUser().getName() : null,
                        String.class)),
                Map.entry("updatedByUserId",
                        new MapperEntry<>((program) -> program.getUpdatedByUser() != null ? program.getUpdatedByUser().getId() : null,
                        UUID.class)),
                Map.entry("updatedByUserName",
                        new MapperEntry<>((program) -> program.getUpdatedByUser() != null ? program.getUpdatedByUser().getName() : null,
                        String.class))
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
