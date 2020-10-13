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
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;


@Getter
@Singleton
@Context
public class ProgramLocationQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<ProgramLocation, ?>> fields;

    public ProgramLocationQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name", ProgramLocation::getName),
                Map.entry("abbreviation", ProgramLocation::getAbbreviation),
                Map.entry("slope", ProgramLocation::getSlope),
                Map.entry("createdAt", ProgramLocation::getCreatedAt),
                Map.entry("updatedAt", ProgramLocation::getUpdatedAt),
                Map.entry("createdByUserId",
                        (location) -> location.getCreatedByUser() != null ? location.getCreatedByUser().getId() : null),
                Map.entry("createdByUserName",
                        (location) -> location.getCreatedByUser() != null ? location.getCreatedByUser().getName() : null),
                Map.entry("updatedByUserId",
                        (location) -> location.getUpdatedByUser() != null ? location.getUpdatedByUser().getId() : null),
                Map.entry("updatedByUserName",
                        (location) -> location.getUpdatedByUser() != null ? location.getUpdatedByUser().getName() : null)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<ProgramLocation,?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
