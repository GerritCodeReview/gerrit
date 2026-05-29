// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.entities;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ProjectTest {
  @Test
  public void parseId() {
    assertThat(Project.NameKey.parse("foo")).isEqualTo(Project.nameKey("foo"));
    assertThat(Project.NameKey.parse("foo%20bar")).isEqualTo(Project.nameKey("foo bar"));
    assertThat(Project.NameKey.parse("foo+bar")).isEqualTo(Project.nameKey("foo bar"));
    assertThat(Project.NameKey.parse("foo%2fbar")).isEqualTo(Project.nameKey("foo/bar"));
    assertThat(Project.NameKey.parse("foo%2Fbar")).isEqualTo(Project.nameKey("foo/bar"));
    assertThat(Project.NameKey.parse("foo/a_bar%2B%2B")).isEqualTo(Project.nameKey("foo/a_bar++"));
  }

  @Test
  public void idToString() {
    assertThat(Project.nameKey("foo").toString()).isEqualTo("foo");
    assertThat(Project.nameKey("foo bar").toString()).isEqualTo("foo+bar");
    assertThat(Project.nameKey("foo/bar").toString()).isEqualTo("foo/bar");
    assertThat(Project.nameKey("foo^bar").toString()).isEqualTo("foo%5Ebar");
    assertThat(Project.nameKey("foo%bar").toString()).isEqualTo("foo%25bar");
    assertThat(Project.nameKey("foo/a_bar++").toString()).isEqualTo("foo/a_bar%2B%2B");
  }
}
