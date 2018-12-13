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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.schema.NoteDbSchemaVersions.guessVersion;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.stream.IntStream;
import org.junit.Test;

public class NoteDbSchemaVersionsTest extends GerritBaseTests {
  @Test
  public void testGuessVersion() {
    assertThat(guessVersion(getClass())).isEmpty();
    assertThat(guessVersion(Schema_180.class)).hasValue(180);
  }

  @Test
  public void contiguousVersions() {
    ImmutableSortedSet<Integer> keys = NoteDbSchemaVersions.ALL.keySet();
    ImmutableList<Integer> expected =
        IntStream.rangeClosed(keys.first(), keys.last()).boxed().collect(toImmutableList());
    assertThat(keys).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void exceedsReviewDbVersion() {
    assertThat(NoteDbSchemaVersions.ALL.firstKey()).isGreaterThan(170);
  }

  @Test
  public void containsAllNoteDbSchemas() throws Exception {
    int minNoteDbVersion = 180;
    ImmutableList<Integer> allSchemaVersions =
        ClassPath.from(getClass().getClassLoader())
            .getTopLevelClasses(getClass().getPackage().getName()).stream()
            .map(ClassInfo::load)
            .map(NoteDbSchemaVersions::guessVersion)
            .flatMap(Streams::stream)
            .filter(v -> v >= minNoteDbVersion)
            .sorted()
            .collect(toImmutableList());
    assertThat(NoteDbSchemaVersions.ALL.keySet())
        .containsExactlyElementsIn(allSchemaVersions)
        .inOrder();
  }

  @Test
  public void schemaConstructors() throws Exception {
    NoteDbSchemaVersion.Arguments args = new NoteDbSchemaVersion.Arguments(null, null, null);
  }
}
