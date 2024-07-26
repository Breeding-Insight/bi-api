package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;

@Prototype
public class Environment extends DeltaEntity<BrAPIStudy> {

    @NonNull
    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Environment(BrAPIStudy brAPIObject) {
        super(brAPIObject);
    }

}
