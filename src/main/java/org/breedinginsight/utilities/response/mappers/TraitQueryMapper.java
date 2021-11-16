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
import org.breedinginsight.model.Trait;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Singleton
public class TraitQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<Trait,?>> fields;

    public TraitQueryMapper() {

        fields = Map.ofEntries(
                Map.entry("name", Trait::getObservationVariableName),
                Map.entry("mainAbbreviation", Trait::getMainAbbreviation),
                Map.entry("synonyms", Trait::getSynonyms),
                Map.entry("level",
                        trait -> trait.getProgramObservationLevel() != null ? trait.getProgramObservationLevel().getName() : null),
                Map.entry("status", Trait::getActive),
                Map.entry("methodDescription",
                        trait -> trait.getMethod() != null ? trait.getMethod().getDescription() : null),
                Map.entry("methodClass",
                        trait -> trait.getMethod() != null ? trait.getMethod().getMethodClass() : null),
                Map.entry("methodFormula",
                        trait -> trait.getMethod() != null ? trait.getMethod().getFormula() : null),
                Map.entry("scaleName",
                        trait -> trait.getScale() != null ? trait.getScale().getScaleName() : null),
                Map.entry("scaleClass",
                        trait -> trait.getScale() != null ? trait.getScale().getDataType() : null),
                Map.entry("scaleDecimalPlaces",
                        trait -> trait.getScale() != null ? trait.getScale().getDecimalPlaces() : null),
                Map.entry("scaleLowerLimit",
                        trait -> trait.getScale() != null ? trait.getScale().getValidValueMin() : null),
                Map.entry("scaleUpperLimit",
                        trait -> trait.getScale() != null ? trait.getScale().getValidValueMax() : null),
                Map.entry("scaleCategories",
                        trait -> trait.getScale() != null ?
                                (trait.getScale().getCategories() != null ?
                                        trait.getScale().getCategories().stream()
                                                .map(category -> category.getLabel() + "=" + category.getValue()).collect(Collectors.toList()) : null)
                                : null),
                Map.entry("createdAt", Trait::getCreatedAt),
                Map.entry("updatedAt", Trait::getUpdatedAt),
                Map.entry("createdByUserId",
                        trait -> trait.getCreatedByUser() != null ? trait.getCreatedByUser().getId() : null),
                Map.entry("createdByUserName",
                        trait -> trait.getCreatedByUser() != null ? trait.getCreatedByUser().getName() : null),
                Map.entry("updatedByUserId",
                        trait -> trait.getUpdatedByUser() != null ? trait.getUpdatedByUser().getId() : null),
                Map.entry("updatedByUserName",
                        trait -> trait.getUpdatedByUser() != null ? trait.getUpdatedByUser().getName() : null)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<Trait, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
