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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;

import javax.annotation.Nullable;


@Getter
@Setter
@NoArgsConstructor
@Nullable
@Introspected
public class QueryParams {
    private Integer pageSize = 50;
    private Integer page = 1;
    private boolean showAll = false;
    private String sortField;
    private SortOrder sortOrder = SortOrder.ASC;

    public QueryParams(Integer pageSize, Integer page, Boolean showAll, String sortField, SortOrder sortOrder) {
        if (pageSize != null) this.pageSize = pageSize;
        if (page != null) this.page = page;
        if (showAll != null) this.showAll = showAll;
        if (sortField != null) this.sortField = sortField;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }
}
