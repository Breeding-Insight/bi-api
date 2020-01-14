package org.breedinginsight.api.bi.model.v1.response;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter @Setter
@Accessors(fluent=true)
@ToString
public class UserInfoResponse {
    private String orcid;
    private String name;
    private List<String> roles;
}
