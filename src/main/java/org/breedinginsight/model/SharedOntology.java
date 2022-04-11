package org.breedinginsight.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SharedProgram {
    private UUID programId;
    private String programName;
    private Boolean shared;
    private Boolean accepted;
    private Boolean editable;
}
