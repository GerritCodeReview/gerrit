// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.UsedAt;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.stream.Stream;

public class NoteDbSchemaVersions {
  static final ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> ALL =
      // List all supported NoteDb schema versions here.
      Stream.of(Schema_180.class, Schema_181.class)
          .collect(toImmutableSortedMap(naturalOrder(), v -> guessVersion(v).get(), v -> v));

  public static final int FIRST = ALL.firstKey();
  public static final int LATEST = ALL.lastKey();

  // TODO(dborowitz): Migrate delete-project plugin to use this implementation.
  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public static Optional<Integer> guessVersion(Class<?> c) {
    String prefix = "Schema_";
    if (!c.getSimpleName().startsWith(prefix)) {
      return Optional.empty();
    }
    return Optional.ofNullable(Ints.tryParse(c.getSimpleName().substring(prefix.length())));
  }

  public static NoteDbSchemaVersion get(
      ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> schemaVersions, int i) {
    Class<? extends NoteDbSchemaVersion> clazz = schemaVersions.get(i);
    checkArgument(clazz != null, "Schema version not found: %s", i);
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new IllegalStateException("failed to invoke constructor on " + clazz.getName(), e);
    }
  }
}
