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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

/** Specific version of a secondary index schema. */
public class Schema<T> {
  private final boolean release;
  private final ImmutableMap<String, FieldDef<T, ?>> fields;
  private int version;

  protected Schema(boolean release, Iterable<FieldDef<T, ?>> fields) {
    this(0, release, fields);
  }

  @VisibleForTesting
  public Schema(int version, boolean release,
      Iterable<FieldDef<T, ?>> fields) {
    this.version = version;
    this.release = release;
    ImmutableMap.Builder<String, FieldDef<T, ?>> b = ImmutableMap.builder();
    for (FieldDef<T, ?> f : fields) {
      b.put(f.getName(), f);
    }
    this.fields = b.build();
  }

  public final boolean isRelease() {
    return release;
  }

  public final int getVersion() {
    return version;
  }

  public final ImmutableMap<String, FieldDef<T, ?>> getFields() {
    return fields;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .addValue(fields.keySet())
        .toString();
  }

  void setVersion(int version) {
    this.version = version;
  }
}
