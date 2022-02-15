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

package org.breedinginsight.utilities;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Utilities {

    public static <T> Optional<T> findInList(List<T> checkList, T objectToCheck, Function<T, UUID> getterMethod){

        Optional<T> existingObject = checkList.stream()
                .filter(p -> getterMethod.apply(p).equals(getterMethod.apply(objectToCheck)))
                .findFirst();
        return existingObject;
    }

    /**
     * Case insensitive search for string in string list
     *
     * @param target - string to search for
     * @param list - list of strings to search in
     * @return true if case insensitive match, false otherwise
     */
    public static boolean containsCaseInsensitive(String target, List<String> list){
        return list.stream().anyMatch(x -> x.equalsIgnoreCase(target));
    }
}
