package org.breedinginsight.api.bi.model.v1.request;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {
    private String name;
    private String email;
}
