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
package org.breedinginsight.brapi.v1.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.api.model.v1.request.query.PaginationParams;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;

import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

@Getter
@Setter
@Introspected
@NoArgsConstructor
public class BrapiQuery implements PaginationParams {

    private static Integer DEFAULT_PAGE = 0;
    private static Integer DEFAULT_PAGE_SIZE = 50;

    @Positive
    private Integer pageSize;
    @PositiveOrZero
    private Integer page;

    private String sortField;
    private SortOrder sortOrder;

    public Integer getDefaultPage() {
        return DEFAULT_PAGE;
    }

    public Integer getDefaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }
}
