package org.breedinginsight.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribedOntology {
    private UUID programId;
    private String programName;
    private Boolean subscribed;
    private Boolean editable;
}
