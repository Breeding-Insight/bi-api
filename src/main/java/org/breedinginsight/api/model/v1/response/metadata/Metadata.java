package org.breedinginsight.api.model.v1.response.metadata;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
public class Metadata {
    private Pagination pagination;
    private List<Status> status;
    private List<String> dataFiles;

    public Metadata(Pagination pagination, List<Status> status) {
        this.setPagination(pagination)
            .setStatus(status);
    }
}
