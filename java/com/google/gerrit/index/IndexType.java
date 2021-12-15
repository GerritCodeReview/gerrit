// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/**
 * Index types supported by the secondary index.
 *
 * <p>The explicitly known index types are Lucene (the default) and a fake index used in tests.
 *
 * <p>The third supported index type is any other type String value, deemed as custom. This is for
 * configuring index types that are internal or not to be disclosed. Supporting custom index types
 * allows to not break that case upon core implementation changes.
 */
public class IndexType {
  public static final String SYS_PROP = "gerrit.index.type";
  private static final String ENV_VAR = "GERRIT_INDEX_TYPE";

  private static final String LUCENE = "lucene";
  private static final String FAKE = "fake";

  private final String type;

  /**
   * Returns the index type in case it was set by an environment variable. This is useful to run
   * tests against a certain index backend.
   */
  public static Optional<IndexType> fromEnvironment() {
    String value = System.getenv(ENV_VAR);
    if (Strings.isNullOrEmpty(value)) {
      value = System.getProperty(SYS_PROP);
    }
    if (Strings.isNullOrEmpty(value)) {
      return Optional.empty();
    }
    value = value.toUpperCase().replace("-", "_");
    IndexType type = new IndexType(value);
    if (!Strings.isNullOrEmpty(System.getenv(ENV_VAR))) {
      checkArgument(
          type != null, "Invalid value for env variable %s: %s", ENV_VAR, System.getenv(ENV_VAR));
    } else {
      checkArgument(
          type != null,
          "Invalid value for system property %s: %s",
          SYS_PROP,
          System.getProperty(SYS_PROP));
    }
    return Optional.of(type);
  }

  public IndexType(@Nullable String type) {
    this.type = type == null ? getDefault() : type.toLowerCase();
  }

  public static String getDefault() {
    return LUCENE;
  }

  public static ImmutableSet<String> getKnownTypes() {
    return ImmutableSet.of(LUCENE, FAKE);
  }

  public boolean isLucene() {
    return type.equals(LUCENE);
  }

  public boolean isFake() {
    return type.equals(FAKE);
  }

  @Override
  public String toString() {
    return type;
  }
}
