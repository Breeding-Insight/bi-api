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

package org.breedinginsight.utilities.response;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class GenericComparator<T> implements Comparator<Object> {

    private final Comparator real;

    public GenericComparator(@NonNull Comparator<T> real) {
        this.real = real;
    }

    @Override
    public int compare(Object o1, Object o2) {

        if (o1 instanceof List && o2 instanceof List){
            List list1 = (List) o1;
            List list2 = (List) o2;

            if (list1.size() == list2.size()){
                // Sort individual lists
                Collections.sort(list1);
                Collections.sort(list2);

                return list1.toString().compareToIgnoreCase(list2.toString());
            }
            if (list1.size() > list2.size()) {
                return 1;
            } else {
                return -1;
            }
        } else {
            return real.compare(o1, o2);
        }

    }
}
