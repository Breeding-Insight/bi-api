/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.breedinginsight.api.serializer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.Gson;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.ObjectMapperFactory;
import org.brapi.client.v2.JSON;
import org.brapi.v2.model.BrApiGeoJSON;

import javax.inject.Singleton;

/**
 * Add custom serializers to Micronaut's Jackson ObjectMapper
 */
@Factory
public class CustomObjectMapperFactory extends ObjectMapperFactory {

    @Singleton @Replaces(ObjectMapper.class)
    @Override
    public ObjectMapper objectMapper(@Nullable JacksonConfiguration jacksonConfiguration, @Nullable JsonFactory jsonFactory) {
        ObjectMapper mapper = super.objectMapper(jacksonConfiguration, jsonFactory);

        // Jackson was not properly serializing geojson objects from com.github.filosganga.geogson
        // which is made to work with gson and part of the brapi client object models so just use gson to
        // do the serialization instead of jackson for this case
        SimpleModule module = new SimpleModule();
        Gson gson = new JSON().getGson();
        module.addSerializer(new GsonBasedSerializer<>(BrApiGeoJSON.class, gson));
        mapper.registerModule(module);

        return mapper;
    }
}
