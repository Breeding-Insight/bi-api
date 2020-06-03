package org.breedinginsight.api.model.v1.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;

@Getter
@Introspected
public class TraitsQuery {
    Boolean full = false;
}
