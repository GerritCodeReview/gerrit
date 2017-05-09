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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.SchemaDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

public class IndexVersions {
  private static final String ALL = "all";
  private static final String CURRENT = "current";
  private static final String PREVIOUS = "previous";

  /**
   * Returns the index versions from {@link IndexVersions#get(SchemaDefinitions)} without the latest
   * schema version.
   *
   * @param schemaDef the schema definition
   * @return the index versions from {@link IndexVersions#get(SchemaDefinitions)} without the latest
   *     schema version
   */
  public static <V> ImmutableList<Integer> getWithoutLatest(SchemaDefinitions<V> schemaDef) {
    List<Integer> schemaVersions = new ArrayList<>(get(schemaDef));
    schemaVersions.remove(Integer.valueOf(schemaDef.getLatest().getVersion()));
    return ImmutableList.copyOf(schemaVersions);
  }

  /**
   * Returns the schema versions against which the query tests should be executed.
   *
   * <p>The schema versions are read from the '<schema-name>_INDEX_VERSIONS' env var if it is set,
   * e.g. 'ACCOUNTS_INDEX_VERSIONS', 'CHANGES_INDEX_VERSIONS', 'GROUPS_INDEX_VERSIONS'. As value a
   * comma-separated list of schema version is expected. {@code current} can be used for the latest
   * schema version and {@code previous} is resolved to the second last schema version.
   * Alternatively the value can also be {@code all} for all schema versions.
   *
   * <p>If a schema version was not specified by an env var, the current and the second last schema
   * versions are returned. If there is no other schema version than the current schema version,
   * only the current schema version is returned.
   *
   * @param schemaDef the schema definition
   * @return the last schema version that was used before the current schema, {@code null} if no
   *     schema version was specified by the env var and if no other schema version than the current
   *     schema version exists
   * @throws IllegalArgumentException if the value of the env var is invalid or if the specified
   *     schema version doesn't exist
   */
  public static <V> ImmutableList<Integer> get(SchemaDefinitions<V> schemaDef) {
    SortedMap<Integer, Schema<V>> schemas = schemaDef.getSchemas();

    String envVar = schemaDef.getName().toUpperCase() + "_INDEX_VERSIONS";
    String value = System.getenv(envVar);
    if (value != null) {
      value = value.trim();
    }

    if (!Strings.isNullOrEmpty(value)) {
      if (ALL.equals(value)) {
        return ImmutableList.copyOf(schemas.keySet());
      }

      List<Integer> versions = new ArrayList<>();
      for (String s : Splitter.on(',').split(value)) {
        if (CURRENT.equals(s)) {
          versions.add(schemaDef.getLatest().getVersion());
        } else if (PREVIOUS.equals(s)) {
          if (schemaDef.getPrevious() == null) {
            throw new IllegalArgumentException("previous version does not exist");
          }
          versions.add(schemaDef.getPrevious().getVersion());
        } else {
          Integer version = Ints.tryParse(s);
          if (version == null) {
            throw new IllegalArgumentException(
                String.format("Invalid value for env variable %s: %s", envVar, s));
          }

          if (!schemas.containsKey(version)) {
            throw new IllegalArgumentException(
                String.format(
                    "Last index version %s that was specified by env variable %s not found."
                        + " Possible versions are: %s",
                    version, envVar, schemas.keySet()));
          }
          versions.add(version);
        }
      }
      return ImmutableList.copyOf(versions);
    }

    List<Integer> schemaVersions = new ArrayList<>(2);
    if (schemaDef.getPrevious() != null) {
      schemaVersions.add(schemaDef.getPrevious().getVersion());
    }
    schemaVersions.add(schemaDef.getLatest().getVersion());
    return ImmutableList.copyOf(schemaVersions);
  }
}
