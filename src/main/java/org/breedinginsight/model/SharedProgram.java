package org.breedinginsight.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SharedProgram {
    private UUID program_id;
    private String program_name;
    private Boolean shared;
    private Boolean editable;
}
