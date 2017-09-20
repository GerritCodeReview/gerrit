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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.testing.IndexVersions.ALL;
import static com.google.gerrit.testing.IndexVersions.CURRENT;
import static com.google.gerrit.testing.IndexVersions.PREVIOUS;

import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class IndexVersionsTest extends GerritBaseTests {
  private static final ChangeSchemaDefinitions SCHEMA_DEF = ChangeSchemaDefinitions.INSTANCE;

  @Test
  public void noValue() {
    List<Integer> expected = new ArrayList<>();
    if (SCHEMA_DEF.getPrevious() != null) {
      expected.add(SCHEMA_DEF.getPrevious().getVersion());
    }
    expected.add(SCHEMA_DEF.getLatest().getVersion());

    assertThat(get(null)).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void emptyValue() {
    List<Integer> expected = new ArrayList<>();
    if (SCHEMA_DEF.getPrevious() != null) {
      expected.add(SCHEMA_DEF.getPrevious().getVersion());
    }
    expected.add(SCHEMA_DEF.getLatest().getVersion());

    assertThat(get("")).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void all() {
    assertThat(get(ALL)).containsExactlyElementsIn(SCHEMA_DEF.getSchemas().keySet()).inOrder();
  }

  @Test
  public void current() {
    assertThat(get(CURRENT)).containsExactly(SCHEMA_DEF.getLatest().getVersion());
  }

  @Test
  public void previous() {
    assume().that(SCHEMA_DEF.getPrevious()).isNotNull();

    assertThat(get(PREVIOUS)).containsExactly(SCHEMA_DEF.getPrevious().getVersion());
  }

  @Test
  public void versionNumber() {
    assume().that(SCHEMA_DEF.getPrevious()).isNotNull();

    assertThat(get(Integer.toString(SCHEMA_DEF.getPrevious().getVersion())))
        .containsExactly(SCHEMA_DEF.getPrevious().getVersion());
  }

  @Test
  public void invalid() {
    assertIllegalArgument("foo", "Invalid value for test: foo");
  }

  @Test
  public void currentAndPrevious() {
    if (SCHEMA_DEF.getPrevious() == null) {
      assertIllegalArgument(CURRENT + "," + PREVIOUS, "previous version does not exist");
      return;
    }

    assertThat(get(CURRENT + "," + PREVIOUS))
        .containsExactly(SCHEMA_DEF.getLatest().getVersion(), SCHEMA_DEF.getPrevious().getVersion())
        .inOrder();
    assertThat(get(PREVIOUS + "," + CURRENT))
        .containsExactly(SCHEMA_DEF.getPrevious().getVersion(), SCHEMA_DEF.getLatest().getVersion())
        .inOrder();
  }

  @Test
  public void currentAndVersionNumber() {
    assume().that(SCHEMA_DEF.getPrevious()).isNotNull();

    assertThat(get(CURRENT + "," + SCHEMA_DEF.getPrevious().getVersion()))
        .containsExactly(SCHEMA_DEF.getLatest().getVersion(), SCHEMA_DEF.getPrevious().getVersion())
        .inOrder();
    assertThat(get(SCHEMA_DEF.getPrevious().getVersion() + "," + CURRENT))
        .containsExactly(SCHEMA_DEF.getPrevious().getVersion(), SCHEMA_DEF.getLatest().getVersion())
        .inOrder();
  }

  @Test
  public void currentAndAll() {
    assertIllegalArgument(CURRENT + "," + ALL, "Invalid value for test: " + ALL);
  }

  @Test
  public void currentAndInvalid() {
    assertIllegalArgument(CURRENT + ",foo", "Invalid value for test: foo");
  }

  @Test
  public void nonExistingVersion() {
    int nonExistingVersion = SCHEMA_DEF.getLatest().getVersion() + 1;
    assertIllegalArgument(
        Integer.toString(nonExistingVersion),
        "Index version "
            + nonExistingVersion
            + " that was specified by test not found. Possible versions are: "
            + SCHEMA_DEF.getSchemas().keySet());
  }

  private static List<Integer> get(String value) {
    return IndexVersions.get(ChangeSchemaDefinitions.INSTANCE, "test", value);
  }

  private void assertIllegalArgument(String value, String expectedMessage) {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage(expectedMessage);
    get(value);
  }
}
