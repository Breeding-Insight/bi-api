package org.breedinginsight.api.model.v1.response.metadata;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent=true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Pagination {
    private Integer totalCount;
    private Integer pageSize;
    private Integer totalPages;
    private Integer currentPage;
}
