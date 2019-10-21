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

public class BranchTest {
  @Test
  public void canonicalizeNameDuringConstruction() {
    assertThat(BranchNameKey.create(new Project.NameKey("foo"), "bar").branch())
        .isEqualTo("refs/heads/bar");
    assertThat(BranchNameKey.create(new Project.NameKey("foo"), "refs/heads/bar").branch())
        .isEqualTo("refs/heads/bar");
  }

  @Test
  public void idToString() {
    assertThat(BranchNameKey.create(new Project.NameKey("foo"), "bar").toString())
        .isEqualTo("foo,refs/heads/bar");
    assertThat(BranchNameKey.create(new Project.NameKey("foo bar"), "bar baz").toString())
        .isEqualTo("foo+bar,refs/heads/bar+baz");
    assertThat(BranchNameKey.create(new Project.NameKey("foo^bar"), "bar^baz").toString())
        .isEqualTo("foo%5Ebar,refs/heads/bar%5Ebaz");
  }
}
