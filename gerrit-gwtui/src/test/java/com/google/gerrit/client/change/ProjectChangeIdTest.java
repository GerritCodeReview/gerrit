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

package com.google.gerrit.client.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ProjectChangeIdTest {

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void emptyStringThrowsException() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage(" is not a valid change identifier");
    ProjectChangeId.create("");
  }

  @Test
  public void noChangeIdThrowsException() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("some/path is not a valid change identifier");
    ProjectChangeId.create("some/path");
  }

  @Test
  public void noChangeButProjectIdThrowsException() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("some/+/path is not a valid change identifier");
    ProjectChangeId.create("some/+/path");
  }

  @Test
  public void project() {
    assertThat(ProjectChangeId.create("test/+/123/some/path")).isEqualTo(result("test", 123));
    assertThat(ProjectChangeId.create("test/+/123/some/path/")).isEqualTo(result("test", 123));
    assertThat(ProjectChangeId.create("test/+/123/")).isEqualTo(result("test", 123));
    assertThat(ProjectChangeId.create("test/+/123")).isEqualTo(result("test", 123));
    // Numeric Project.NameKey
    assertThat(ProjectChangeId.create("123/+/123")).isEqualTo(result("123", 123));
    // Numeric Project.NameKey with ,edit as part of the name
    assertThat(ProjectChangeId.create("123,edit/+/123")).isEqualTo(result("123,edit", 123));
  }

  @Test
  public void noProject() {
    assertThat(ProjectChangeId.create("123/some/path")).isEqualTo(result(null, 123));
    assertThat(ProjectChangeId.create("123/some/path/")).isEqualTo(result(null, 123));
    assertThat(ProjectChangeId.create("123/")).isEqualTo(result(null, 123));
    assertThat(ProjectChangeId.create("123")).isEqualTo(result(null, 123));
  }

  @Test
  public void editSuffix() {
    assertThat(ProjectChangeId.create("123,edit/some/path")).isEqualTo(result(null, 123));
    assertThat(ProjectChangeId.create("123,edit/")).isEqualTo(result(null, 123));
    assertThat(ProjectChangeId.create("123,edit")).isEqualTo(result(null, 123));

    assertThat(ProjectChangeId.create("test/+/123,edit/some/path")).isEqualTo(result("test", 123));
    assertThat(ProjectChangeId.create("test/+/123,edit/")).isEqualTo(result("test", 123));
    assertThat(ProjectChangeId.create("test/+/123,edit")).isEqualTo(result("test", 123));
  }

  private static ProjectChangeId result(@Nullable String project, int id) {
    return new ProjectChangeId(
        project == null ? null : new Project.NameKey(project), new Change.Id(id));
  }
}
