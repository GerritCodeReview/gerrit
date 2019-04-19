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

package com.google.gerrit.reviewdb.client;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class ProjectTest extends GerritBaseTests {
  @Test
  public void parseId() {
    assertThat(Project.NameKey.parse("foo")).isEqualTo(new Project.NameKey("foo"));
    assertThat(Project.NameKey.parse("foo%20bar")).isEqualTo(new Project.NameKey("foo bar"));
    assertThat(Project.NameKey.parse("foo+bar")).isEqualTo(new Project.NameKey("foo bar"));
    assertThat(Project.NameKey.parse("foo%2fbar")).isEqualTo(new Project.NameKey("foo/bar"));
    assertThat(Project.NameKey.parse("foo%2Fbar")).isEqualTo(new Project.NameKey("foo/bar"));
  }

  @Test
  public void idToString() {
    assertThat(Project.nameKey("foo").toString()).isEqualTo("foo");
    assertThat(Project.nameKey("foo bar").toString()).isEqualTo("foo+bar");
    assertThat(Project.nameKey("foo/bar").toString()).isEqualTo("foo/bar");
    assertThat(Project.nameKey("foo^bar").toString()).isEqualTo("foo%5Ebar");
    assertThat(Project.nameKey("foo%bar").toString()).isEqualTo("foo%25bar");
  }
}
