package org.breedinginsight.api.bi.model.v1.response;

import lombok.*;
import lombok.experimental.Accessors;
import org.breedinginsight.api.bi.model.v1.response.metadata.Metadata;

@Getter
@Setter
@Accessors(fluent=true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> {
    public Metadata metadata;
    public T result;
}
