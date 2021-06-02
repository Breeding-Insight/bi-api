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

package org.breedinginsight.api.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jooq.tools.StringUtils.isBlank;

public class ListOfStringDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<String> filtered = ListOfStringDeserializer.process(p);
        return filtered.size() > 0 ? filtered : null;
    }

    public static List<String> process(JsonParser p) throws IOException {
        List<String> stringList = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            String value = p.getValueAsString();
            if (value != null && !isBlank(value.trim())) {
                stringList.add(value.trim());
            }
        }
        return stringList;
    }

}
