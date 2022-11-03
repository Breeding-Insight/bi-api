package org.breedinginsight.services.brapi;

import org.brapi.client.v2.BrAPIClient;

import javax.inject.Singleton;
import java.lang.reflect.InvocationTargetException;

@Singleton
public class BrAPIEndpointProvider {
    public <T> T get(BrAPIClient brAPIClient, Class<T> type) {
        try {
            return type.getDeclaredConstructor(BrAPIClient.class).newInstance(brAPIClient);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("Unable to create object of type: "+type.getName(), e);
        }
    }
}
