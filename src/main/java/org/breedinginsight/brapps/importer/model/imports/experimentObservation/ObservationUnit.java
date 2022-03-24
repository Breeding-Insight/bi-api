package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIEntryTypeEnum;

@Builder
@Getter
@Setter
public class ObservationUnit {

    @Setter(AccessLevel.NONE)
    @Builder(AccessLevel.NONE)
    private BrAPIEntryTypeEnum test_or_check = BrAPIEntryTypeEnum.TEST;

    private String exp_unit_id = null;
    private String exp_replicate_no = null;
    private String exp_block_no = null;
    private String row = null;
    private String column = null;

    public ObservationUnit test_or_check_str(String type){
        if("C".equals(type)){
            this.test_or_check = BrAPIEntryTypeEnum.CHECK;
        }
        return this;
    }
}
