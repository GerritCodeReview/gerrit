// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class BranchOrderSectionTest {

  @Test
  public void getMoreStableWithUnqualifiedNames() {
    BranchOrderSection section =
        BranchOrderSection.create(ImmutableList.of("master", "stable-3.0", "stable-2.0"));

    assertThat(section.getMoreStable("master"))
        .containsExactly("refs/heads/stable-3.0", "refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("stable-3.0"))
        .containsExactly("refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("stable-2.0")).isEmpty();
  }

  @Test
  public void getMoreStableWithFullyQualifiedNames() {
    BranchOrderSection section =
        BranchOrderSection.create(
            ImmutableList.of(
                "refs/heads/master", "refs/heads/stable-3.0", "refs/heads/stable-2.0"));

    assertThat(section.getMoreStable("master"))
        .containsExactly("refs/heads/stable-3.0", "refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("refs/heads/master"))
        .containsExactly("refs/heads/stable-3.0", "refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("refs/heads/stable-3.0"))
        .containsExactly("refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("refs/heads/stable-2.0")).isEmpty();
  }

  @Test
  public void getMoreStableWithMixedQualifications() {
    BranchOrderSection section =
        BranchOrderSection.create(
            ImmutableList.of("master", "refs/heads/stable-3.0", "stable-2.0"));

    assertThat(section.getMoreStable("master"))
        .containsExactly("refs/heads/stable-3.0", "refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("refs/heads/master"))
        .containsExactly("refs/heads/stable-3.0", "refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("stable-3.0"))
        .containsExactly("refs/heads/stable-2.0")
        .inOrder();
    assertThat(section.getMoreStable("refs/heads/stable-3.0"))
        .containsExactly("refs/heads/stable-2.0")
        .inOrder();
  }

  @Test
  public void getMoreStableWithUnknownBranch() {
    BranchOrderSection section =
        BranchOrderSection.create(ImmutableList.of("master", "stable-3.0"));

    assertThat(section.getMoreStable("unknown")).isEmpty();
    assertThat(section.getMoreStable("refs/heads/unknown")).isEmpty();
  }

  @Test
  public void getMoreStableWithEmptyOrder() {
    BranchOrderSection section = BranchOrderSection.create(ImmutableList.of());

    assertThat(section.getMoreStable("master")).isEmpty();
    assertThat(section.getMoreStable("refs/heads/master")).isEmpty();
  }

  @Test
  public void getMoreStableWithDuplicateBranches() {
    BranchOrderSection section =
        BranchOrderSection.create(ImmutableList.of("master", "stable-3.0", "master", "stable-2.0"));

    assertThat(section.getMoreStable("master"))
        .containsExactly("refs/heads/stable-3.0", "refs/heads/master", "refs/heads/stable-2.0")
        .inOrder();
  }
}
