package org.breedinginsight.api.model.v1.response;

import lombok.*;
import lombok.experimental.Accessors;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
public class Response<T> {
    public Metadata metadata;
    @NonNull
    public T result;
}
