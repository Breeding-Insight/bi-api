package org.breedinginsight.api.model.v1.response;

import lombok.*;
import lombok.experimental.Accessors;
import org.breedinginsight.dao.db.tables.records.BiUserRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@Accessors(fluent=true)
@ToString
@NoArgsConstructor
public class UserInfoResponse {
    private UUID id;
    private String orcid;
    private String name;
    private String email;
    private List<String> roles;

    public UserInfoResponse(BiUserRecord biUserRecord){
        this.id(biUserRecord.getId());
        this.orcid(biUserRecord.getOrcid());
        this.name(biUserRecord.getName());
        this.email(biUserRecord.getEmail());
        this.roles(new ArrayList<>());
    }

}
