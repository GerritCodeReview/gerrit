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

package com.google.gerrit.testutil;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.SchemaDefinitions;
import com.google.gerrit.server.index.SchemaUtil;
import java.util.SortedMap;

public class LastIndexVersion {
  /**
   * Returns the last schema version that was used before the current schema version.
   *
   * <p>The last schema version is read from the 'LAST_<schema-name>_INDEX_VERSION' env var if it is
   * set, e.g. 'LAST_ACCOUNTS_INDEX_VERSION', 'LAST_CHANGES_INDEX_VERSION',
   * 'LAST_GROUPS_INDEX_VERSION'.
   *
   * <p>If a last schema version was not specified by an env var, the second last schema version is
   * returned. If there is no other schema version than the current schema version, {@code null} is
   * returned.
   *
   * @param schemaDef the schema definition
   * @param valueClass the value class of the schema
   * @return the last schema version that was used before the current schema, {@code null} if no
   *     schema version was specified by the env var and if no other schema version than the current
   *     schema version exists
   * @throws IllegalArgumentException if the value of the env var is invalid or if the specified
   *     schema version doesn't exist
   */
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
