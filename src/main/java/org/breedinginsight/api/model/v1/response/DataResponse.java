package org.breedinginsight.api.model.v1.response;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DataResponse<T> {
    private List<T> data;
}
