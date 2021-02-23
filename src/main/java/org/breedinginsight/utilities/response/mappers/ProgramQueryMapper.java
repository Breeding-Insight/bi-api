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

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;


@Getter
@Singleton
public class ProgramQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<Program, ?>> fields;

    public ProgramQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name", Program::getName),
                Map.entry("abbreviation", Program::getAbbreviation),
                Map.entry("objective", Program::getObjective),
                Map.entry("documentationUrl", Program::getDocumentationUrl),
                Map.entry("active", Program::getActive),
                Map.entry("brapiUrl", Program::getBrapiUrl),
                Map.entry("numUsers", Program::getNumUsers),
                Map.entry("createdAt", Program::getCreatedAt),
                Map.entry("updatedAt", Program::getUpdatedAt),
                Map.entry("speciesId", Program::getSpeciesId),
                Map.entry("speciesName",
                        (program) -> program.getSpecies() != null ? program.getSpecies().getCommonName() : null),
                Map.entry("createdByUserId",
                        (program) -> program.getCreatedByUser() != null ? program.getCreatedByUser().getId() : null),
                Map.entry("createdByUserName",
                        (program) -> program.getCreatedByUser() != null ? program.getCreatedByUser().getName() : null),
                Map.entry("updatedByUserId",
                        (program) -> program.getUpdatedByUser() != null ? program.getUpdatedByUser().getId() : null),
                Map.entry("updatedByUserName",
                        (program) -> program.getUpdatedByUser() != null ? program.getUpdatedByUser().getName() : null)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<Program,?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
