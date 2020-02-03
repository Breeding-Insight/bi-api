package org.breedinginsight.model;

import lombok.*;
import lombok.experimental.Accessors;
import org.breedinginsight.dao.db.tables.pojos.BiUser;
import org.breedinginsight.dao.db.tables.records.BiUserRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
public class User {

    private UUID id;
    private String orcid;
    private String name;
    private String email;
    private List<String> roles;

    public User(BiUser biUser) {
        this.setId(biUser.getId())
                .setOrcid(biUser.getOrcid())
                .setName(biUser.getName())
                .setEmail(biUser.getEmail())
                .setRoles(new ArrayList<>());
    }
}
