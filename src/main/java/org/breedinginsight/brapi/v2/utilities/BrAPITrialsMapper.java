package org.breedinginsight.brapi.v2.utilities;

import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class BrAPITrialsMapper extends BrAPISortFilterMapper {
    public BrAPITrialsMapper() {
        super(Map.of(
                "name", "trialName",
                "active", "active",
                "createdBy", "createdBy",
                "createdDate", "createdDate"
        ));
    }
}
