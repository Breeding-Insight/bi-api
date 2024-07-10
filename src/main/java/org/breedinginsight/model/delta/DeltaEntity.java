package org.breedinginsight.model.delta;

import org.apache.commons.lang3.NotImplementedException;

public interface DeltaEntity<T> {
    // TODO: I don't think interfaces can specify private methods
//    T getBrAPIObject();

    T cloneBrAPIObject();
}
