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
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;


@Getter
@Singleton
public class StudyQueryMapper extends AbstractQueryMapper {

    private final String defaultSortField = "studyName";
    private final SortOrder defaultSortOrder = SortOrder.ASC;
    private Map<String, Function<BrAPIStudy, ?>> fields;

    public StudyQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("studyName", BrAPIStudy::getStudyName),
                Map.entry("trialDbId", BrAPIStudy::getTrialDbId)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPIStudy,?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
