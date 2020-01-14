package org.breedinginsight.api.bi.model.v1.request;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(fluent=true)
@ToString
public class UserRequest {
    private String name;
    private String email;
}
