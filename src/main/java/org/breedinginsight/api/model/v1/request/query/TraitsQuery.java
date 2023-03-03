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

package org.breedinginsight.api.model.v1.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class TraitsQuery extends QueryParams {
    private String name;
    private String traitDescription;
    private String entityAttribute;
    private String mainAbbreviation;
    private String synonyms;
    private String level;
    private String status;
    private String methodDescription;
    private String methodClass;
    private String methodFormula;
    private String scaleName;
    private String scaleClass;
    private String scaleDecimalPlaces;
    private String scaleLowerLimit;
    private String scaleUpperLimit;
    private String scaleCategories;
    private String createdAt;
    private String updatedAt;
    private String createdByUserId;
    private String createdByUserName;
    private String updatedByUserId;
    private String updatedByUserName;
    private String termType;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getName())) {
            filters.add(constructFilterRequest("name", getName()));
        }
        if (!StringUtils.isBlank(getTraitDescription())) {
            filters.add(constructFilterRequest("traitDescription", getTraitDescription()));
        }
        if (!StringUtils.isBlank(getEntityAttribute())) {
            filters.add(constructFilterRequest("entityAttribute", getEntityAttribute()));
        }
        if (!StringUtils.isBlank(getMainAbbreviation())) {
            filters.add(constructFilterRequest("mainAbbreviation", getMainAbbreviation()));
        }
        if (!StringUtils.isBlank(getSynonyms())) {
            filters.add(constructFilterRequest("synonyms", getSynonyms()));
        }
        if (!StringUtils.isBlank(getLevel())) {
            filters.add(constructFilterRequest("level", getLevel()));
        }
        if (!StringUtils.isBlank(getStatus())) {
            filters.add(constructFilterRequest("status", getStatus()));
        }
        if (!StringUtils.isBlank(getMethodDescription())) {
            filters.add(constructFilterRequest("methodDescription", getMethodDescription()));
        }
        if (!StringUtils.isBlank(getMethodClass())) {
            filters.add(constructFilterRequest("methodClass", getMethodClass()));
        }
        if (!StringUtils.isBlank(getMethodFormula())) {
            filters.add(constructFilterRequest("methodFormula", getMethodFormula()));
        }
        if (!StringUtils.isBlank(getScaleName())) {
            filters.add(constructFilterRequest("scaleName", getScaleName()));
        }
        if (!StringUtils.isBlank(getScaleClass())) {
            filters.add(constructFilterRequest("scaleClass", getScaleClass()));
        }
        if (!StringUtils.isBlank(getScaleDecimalPlaces())) {
            filters.add(constructFilterRequest("scaleDecimalPlaces", getScaleDecimalPlaces()));
        }
        if (!StringUtils.isBlank(getScaleLowerLimit())) {
            filters.add(constructFilterRequest("scaleLowerLimit", getScaleLowerLimit()));
        }
        if (!StringUtils.isBlank(getScaleUpperLimit())) {
            filters.add(constructFilterRequest("scaleUpperLimit", getScaleUpperLimit()));
        }
        if (!StringUtils.isBlank(getScaleCategories())) {
            filters.add(constructFilterRequest("scaleCategories", getScaleCategories()));
        }
        if (!StringUtils.isBlank(getCreatedAt())) {
            filters.add(constructFilterRequest("createdAt", getCreatedAt()));
        }
        if (!StringUtils.isBlank(getUpdatedAt())) {
            filters.add(constructFilterRequest("updatedAt", getUpdatedAt()));
        }
        if (!StringUtils.isBlank(getCreatedByUserId())) {
            filters.add(constructFilterRequest("createdByUserId", getCreatedByUserId()));
        }
        if (!StringUtils.isBlank(getCreatedByUserName())) {
            filters.add(constructFilterRequest("createdByUserName", getCreatedByUserName()));
        }
        if (!StringUtils.isBlank(getUpdatedByUserId())) {
            filters.add(constructFilterRequest("updatedByUserId", getUpdatedByUserId()));
        }
        if (!StringUtils.isBlank(getUpdatedByUserName())) {
            filters.add(constructFilterRequest("updatedByUserName", getUpdatedByUserName()));
        }
        if (!StringUtils.isBlank(getTermType())) {
            filters.add(constructFilterRequest("termType", getTermType()));
        }
        return new SearchRequest(filters);
    }
    private FilterRequest constructFilterRequest(String field, String value) {
        return FilterRequest.builder()
                .field(field)
                .value(value)
                .build();
    }


    Boolean full = false;
}
