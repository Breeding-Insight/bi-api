package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIEntryTypeEnum;

@Builder
@Getter
@Setter
public class ObservationUnitData {
    private BrAPIEntryTypeEnum test_or_check = BrAPIEntryTypeEnum.TEST;

    private String germplasm_name = null;
    private String gid = null;
    private String exp_unit = null;
    private String exp_unit_id = null;
    private String exp_type = null;
    private String exp_replicate_no = null;
    private String exp_block_no = null;
    private String row = null;
    private String column = null;
    private String treatment_factors = null;
    private String id = null;
}
