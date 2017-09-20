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

package com.google.gerrit.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.eclipse.jgit.lib.Config;

public class IndexVersions {
  static final String ALL = "all";
  static final String CURRENT = "current";
  static final String PREVIOUS = "previous";

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
   * e.g. 'ACCOUNTS_INDEX_VERSIONS', 'CHANGES_INDEX_VERSIONS', 'GROUPS_INDEX_VERSIONS'.
   *
   * <p>If schema versions were not specified by an env var, they are read from the
   * 'gerrit.index.<schema-name>.versions' system property, e.g. 'gerrit.index.accounts.version',
   * 'gerrit.index.changes.version', 'gerrit.index.groups.version'.
   *
   * <p>As value a comma-separated list of schema versions is expected. {@code current} can be used
   * for the latest schema version and {@code previous} is resolved to the second last schema
   * version. Alternatively the value can also be {@code all} for all schema versions.
   *
   * <p>If schema versions were neither specified by an env var nor by a system property, the
   * current and the second last schema versions are returned. If there is no other schema version
   * than the current schema version, only the current schema version is returned.
   *
   * @param schemaDef the schema definition
   * @return the schema versions against which the query tests should be executed
   * @throws IllegalArgumentException if the value of the env var or system property is invalid or
   *     if any of the specified schema versions doesn't exist
   */
  public static <V> ImmutableList<Integer> get(SchemaDefinitions<V> schemaDef) {
    String envVar = schemaDef.getName().toUpperCase() + "_INDEX_VERSIONS";
    String value = System.getenv(envVar);
    if (!Strings.isNullOrEmpty(value)) {
      return get(schemaDef, "env variable " + envVar, value);
    }

    String systemProperty = "gerrit.index." + schemaDef.getName().toLowerCase() + ".versions";
    value = System.getProperty(systemProperty);
    return get(schemaDef, "system property " + systemProperty, value);
  }

  @VisibleForTesting
  static <V> ImmutableList<Integer> get(SchemaDefinitions<V> schemaDef, String name, String value) {
    if (value != null) {
      value = value.trim();
    }

    SortedMap<Integer, Schema<V>> schemas = schemaDef.getSchemas();
    if (!Strings.isNullOrEmpty(value)) {
      if (ALL.equals(value)) {
        return ImmutableList.copyOf(schemas.keySet());
      }

      List<Integer> versions = new ArrayList<>();
      for (String s : Splitter.on(',').trimResults().split(value)) {
        if (CURRENT.equals(s)) {
          versions.add(schemaDef.getLatest().getVersion());
        } else if (PREVIOUS.equals(s)) {
          checkArgument(schemaDef.getPrevious() != null, "previous version does not exist");
          versions.add(schemaDef.getPrevious().getVersion());
        } else {
          Integer version = Ints.tryParse(s);
          checkArgument(version != null, "Invalid value for %s: %s", name, s);
          checkArgument(
              schemas.containsKey(version),
              "Index version %s that was specified by %s not found." + " Possible versions are: %s",
              version,
              name,
              schemas.keySet());
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

  public static <V> Map<String, Config> asConfigMap(
      SchemaDefinitions<V> schemaDef,
      List<Integer> schemaVersions,
      String testSuiteNamePrefix,
      Config baseConfig) {
    return schemaVersions
        .stream()
        .collect(
            toMap(
                i -> testSuiteNamePrefix + i,
                i -> {
                  Config cfg = baseConfig;
                  cfg.setInt(
                      "index", "lucene", schemaDef.getName().toLowerCase() + "TestVersion", i);
                  return cfg;
                }));
  }
}
