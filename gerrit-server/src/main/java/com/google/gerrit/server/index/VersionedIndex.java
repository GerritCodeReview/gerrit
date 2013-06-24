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

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import java.util.List;

/** Specific version of a secondary index schema. */
public class VersionedIndex<T> {
  private final ImmutableMap<String, FieldDef<T, ?>> fields;

  protected VersionedIndex(List<FieldDef<T, ?>> fields) {
    ImmutableMap.Builder<String, FieldDef<T, ?>> b = ImmutableMap.builder();
    for (FieldDef<T, ?> f : fields) {
      b.put(f.getName(), f);
    }
    this.fields = b.build();
  }

  public boolean isRelease() {
    return false;
  }

  public final int getVersion() {
    String name = getClass().getSimpleName();
    int und = name.lastIndexOf('_');
    if (und < 0) {
      throw new IllegalStateException(
          "Invalid versioned index class name: " + getClass().getName());
    }
    Integer v = Ints.tryParse(name.substring(und + 1));
    if (v == null) {
      throw new IllegalStateException(
          "Invalid versioned index class name: " + getClass().getName());
    }
    return v;
  }

  public ImmutableMap<String, FieldDef<T, ?>> getFields() {
    return fields;
  }
}
