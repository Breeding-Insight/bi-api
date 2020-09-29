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

import org.breedinginsight.api.v1.controller.metadata.SortOrder;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractQueryMapper<T> {
    public abstract boolean exists(String fieldName);
    public abstract MapperEntry<T> getMapperEntry(String fieldName) throws NullPointerException;

    public List<T> filter(List<T> target, List<FilterField> filterFields) {
        // Filter on a record matching all of the search filters
        //TODO: Check why it is hitting each record twice
        return target.stream()
                .filter(record ->
                        filterFields.stream().allMatch(filterField ->
                                filterField.getField().apply(record).toString().toLowerCase().contains(filterField.getValue().toLowerCase())))
                .collect(Collectors.toList());
    }

    /*
    Sorts fields from a sort request. Anything that is numerical is sorted numerically, everything else
    is sorted as a string. Might need to add more sortable types later.
     */
    public List<T> sort(List<T> target, MapperEntry mapperEntry, SortOrder sortOrder) throws IllegalArgumentException {
        // TODO: Add date sorting?

        if (target.size() == 0) { return target; }
        Boolean isNumeric = Float.class.isAssignableFrom(mapperEntry.getFieldType());

        target.sort((a,b)-> {
            // Cast to float for number and sort
            if (isNumeric) {
                if (sortOrder == SortOrder.ASC) {
                    if (mapperEntry.getGetter().apply(a) == null) return -1;
                    if (mapperEntry.getGetter().apply(b) == null) return 1;
                    return ((Float) mapperEntry.getGetter().apply(a)).compareTo((Float) mapperEntry.getGetter().apply(b));
                } else {
                    if (mapperEntry.getGetter().apply(a) == null) return 1;
                    if (mapperEntry.getGetter().apply(b) == null) return -1;
                    return ((Float) mapperEntry.getGetter().apply(b)).compareTo((Float) mapperEntry.getGetter().apply(a));
                }
            } else {
                if (sortOrder == SortOrder.ASC) {
                    if (mapperEntry.getGetter().apply(a) == null) return -1;
                    if (mapperEntry.getGetter().apply(b) == null) return 1;
                    return mapperEntry.getGetter().apply(a).toString().compareTo(mapperEntry.getGetter().apply(b).toString());
                } else {
                    if (mapperEntry.getGetter().apply(a) == null) return 1;
                    if (mapperEntry.getGetter().apply(b) == null) return -1;
                    return mapperEntry.getGetter().apply(b).toString().compareTo(mapperEntry.getGetter().apply(a).toString());
                }
            }
        });

        return target;
    }
}
