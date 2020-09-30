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


@Getter
@Singleton
@Context
public class ProgramLocationQueryMapper extends AbstractQueryMapper {

    private Map<String, MapperEntry<ProgramLocation>> fields;

    public ProgramLocationQueryMapper() throws NoSuchMethodException {
        fields = Map.ofEntries(
                Map.entry("name", new MapperEntry<>(ProgramLocation::getName,
                        ProgramLocation.class.getMethod("getName").getReturnType())),
                Map.entry("abbreviation", new MapperEntry<>(ProgramLocation::getAbbreviation,
                        ProgramLocation.class.getMethod("getAbbreviation").getReturnType())),
                Map.entry("slope", new MapperEntry<>(ProgramLocation::getSlope,
                        ProgramLocation.class.getMethod("getSlope").getReturnType())),
                Map.entry("createdAt", new MapperEntry<>(ProgramLocation::getCreatedAt,
                        ProgramLocation.class.getMethod("getCreatedAt").getReturnType()))

        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public MapperEntry<ProgramLocation> getMapperEntry(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
