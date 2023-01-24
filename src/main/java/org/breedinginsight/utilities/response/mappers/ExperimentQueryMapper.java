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

import com.google.gson.JsonObject;
import lombok.Getter;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Getter
@Singleton
public class ExperimentQueryMapper extends AbstractQueryMapper {

    private final String defaultSortField = "name";
    private final SortOrder defaultSortOrder = SortOrder.ASC;

    private Map<String, Function<BrAPITrial,?>> fields;

    public ExperimentQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name", BrAPITrial::getTrialName),
                Map.entry("active", BrAPITrial::isActive),
                Map.entry("createdDate",
                        brAPITrial -> brAPITrial.getAdditionalInfo().get("createdDate").getAsString()),
                Map.entry("createdBy",
                        brAPITrial -> brAPITrial.getAdditionalInfo()
                        .get("createdBy").getAsJsonObject()
                        .get("userName").getAsString())
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPITrial, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }


}
