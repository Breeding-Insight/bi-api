package org.breedinginsight.api.bi.model.v1.response.metadata;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent=true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Status {
    private StatusCode messageType;
    private String message;
}
