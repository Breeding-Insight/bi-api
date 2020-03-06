package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class Role extends RoleEntity {

    public Role(RoleEntity roleEntity){
        this.setId(roleEntity.getId());
        this.setDomain(roleEntity.getDomain());
    }

}
