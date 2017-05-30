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

public class ChangeIdParserTest {

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void emptyStringThrowsException() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage(" is not a valid change identifier");
    ChangeIdParser.parse("");
  }

  @Test
  public void noChangeIdThrowsException() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("some/path is not a valid change identifier");
    ChangeIdParser.parse("some/path");
  }

  @Test
  public void noChangeButProjectIdThrowsException() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("some/+/path is not a valid change identifier");
    ChangeIdParser.parse("some/+/path");
  }

  @Test
  public void project() {
    assertThat(ChangeIdParser.parse("test/+/123/some/path")).isEqualTo(result(123, "test"));
    assertThat(ChangeIdParser.parse("test/+/123/some/path/")).isEqualTo(result(123, "test"));
    assertThat(ChangeIdParser.parse("test/+/123/")).isEqualTo(result(123, "test"));
    assertThat(ChangeIdParser.parse("test/+/123")).isEqualTo(result(123, "test"));
    // Numeric Project.NameKey
    assertThat(ChangeIdParser.parse("123/+/123")).isEqualTo(result(123, "123"));
    // Numeric Project.NameKey with ,edit as part of the name
    assertThat(ChangeIdParser.parse("123,edit/+/123")).isEqualTo(result(123, "123,edit"));
  }

  @Test
  public void noProject() {
    assertThat(ChangeIdParser.parse("123/some/path")).isEqualTo(result(123, null));
    assertThat(ChangeIdParser.parse("123/some/path/")).isEqualTo(result(123, null));
    assertThat(ChangeIdParser.parse("123/")).isEqualTo(result(123, null));
    assertThat(ChangeIdParser.parse("123")).isEqualTo(result(123, null));
  }

  @Test
  public void editSuffix() {
    assertThat(ChangeIdParser.parse("123,edit/some/path")).isEqualTo(result(123, null));
    assertThat(ChangeIdParser.parse("123,edit/")).isEqualTo(result(123, null));
    assertThat(ChangeIdParser.parse("123,edit")).isEqualTo(result(123, null));

    assertThat(ChangeIdParser.parse("test/+/123,edit/some/path")).isEqualTo(result(123, "test"));
    assertThat(ChangeIdParser.parse("test/+/123,edit/")).isEqualTo(result(123, "test"));
    assertThat(ChangeIdParser.parse("test/+/123,edit")).isEqualTo(result(123, "test"));
  }

  private static ChangeIdParser.Result result(int id, @Nullable String project) {
    return new ChangeIdParser.Result(
        project == null ? null : new Project.NameKey(project), new Change.Id(id));
  }
}
