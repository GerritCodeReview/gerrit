// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSortedMap;

/**
 * Definitions of the various schema versions over a given Gerrit data type.
 *
 * <p>A <em>schema</em> is a description of the fields that are indexed over the given data type.
 * This class contains all the versions of a schema defined over its data type, exposed as a map of
 * version number to schema definition. If you are interested in the classes responsible for
 * backend-specific runtime implementations, see the implementations of {@link IndexDefinition}.
 */
public abstract class SchemaDefinitions<V> {
  private final String name;
  private final ImmutableSortedMap<Integer, Schema<V>> schemas;

  protected SchemaDefinitions(String name, Class<V> valueClass) {
    this.name = checkNotNull(name);
    this.schemas = SchemaUtil.schemasFromClass(getClass(), valueClass);
  }

  public final String getName() {
    return name;
  }

  public final ImmutableSortedMap<Integer, Schema<V>> getSchemas() {
    return schemas;
  }

  public final Schema<V> get(int version) {
    Schema<V> schema = schemas.get(version);
    checkArgument(schema != null, "Unrecognized %s schema version: %s", name, version);
    return schema;
  }

  public final Schema<V> getLatest() {
    return schemas.lastEntry().getValue();
  }
}
