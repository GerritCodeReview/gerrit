// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.query;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.SchemaDefinitions;
import com.google.gerrit.server.index.SchemaUtil;
import java.util.SortedMap;
import org.junit.Ignore;

@Ignore
public class LastIndexVersion {
  public static <V> Integer get(SchemaDefinitions<V> schemaDef, Class<V> valueClass) {
    SortedMap<Integer, Schema<V>> schemas =
        SchemaUtil.schemasFromClass(schemaDef.getClass(), valueClass);

    String envVar = "LAST_" + schemaDef.getName().toUpperCase() + "_INDEX_VERSION";
    String value = System.getenv(envVar);

    if (!Strings.isNullOrEmpty(value)) {
      Integer lastVersion = Ints.tryParse(value);
      if (lastVersion == null) {
        throw new IllegalArgumentException(
            String.format("Invalid value for env variable %s: %s", envVar, value));
      }
      if (!schemas.containsKey(lastVersion)) {
        throw new IllegalArgumentException(
            String.format(
                "Last index version %s that was specified by env variable %s not found."
                    + " Possible versions are: %s",
                lastVersion, envVar, schemas.keySet()));
      }
      return lastVersion;
    }

    if (schemas.size() > 1) {
      return Iterables.get(schemas.keySet(), schemas.size() - 2);
    }

    return null;
  }
}
