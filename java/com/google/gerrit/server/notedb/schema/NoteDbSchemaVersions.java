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

package com.google.gerrit.server.notedb.schema;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ImmutableSortedMap;
import com.google.gerrit.server.UsedAt;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

public class NoteDbSchemaVersions {
  private static final ImmutableSortedMap<Integer, Class<? extends NoteDbSchemaVersion>> ALL =
      Stream.of(Schema_170.class)
          .collect(toImmutableSortedMap(naturalOrder(), v -> guessVersion(v), v -> v));

  // TODO: tests:
  // * range is contiguous
  // * all Schema_* classes are listed explicitly.
  // * min version matches max ReviewDb version plus one.
  // (but if we test they're all listed explicitly, why not just automagically list them at
  // runtime?)

  public static final int LATEST = ALL.lastKey();

  // TODO(dborowitz): Migrate delete-project plugin to use this copy.
  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public static int guessVersion(Class<?> c) {
    String n = c.getName();
    n = n.substring(n.lastIndexOf('_') + 1);
    while (n.startsWith("0")) {
      n = n.substring(1);
    }
    return Integer.parseInt(n);
  }

  public static NoteDbSchemaVersion get(int i, NoteDbSchemaVersion.Arguments args) {
    Class<? extends NoteDbSchemaVersion> clazz = ALL.get(i);
    checkArgument(clazz != null, "Schema version not found: %s", i);
    try {
      return clazz.getConstructor(NoteDbSchemaVersion.Arguments.class).newInstance(args);
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new IllegalStateException("failed to invoke constructor on " + clazz.getName());
    }
  }
}
