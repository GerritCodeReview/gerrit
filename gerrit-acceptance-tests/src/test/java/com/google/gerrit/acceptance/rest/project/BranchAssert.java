// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import java.util.List;

public class BranchAssert {
  public static void assertBranches(
      List<BranchInfo> expectedBranches, List<BranchInfo> actualBranches) {
    assertRefNames(refs(expectedBranches), actualBranches);
    for (int i = 0; i < expectedBranches.size(); i++) {
      assertBranchInfo(expectedBranches.get(i), actualBranches.get(i));
    }
  }

  public static void assertRefNames(
      Iterable<String> expectedRefs, Iterable<BranchInfo> actualBranches) {
    Iterable<String> actualNames = refs(actualBranches);
    assertThat(actualNames).containsExactlyElementsIn(expectedRefs).inOrder();
  }

  public static void assertBranchInfo(BranchInfo expected, BranchInfo actual) {
    assertThat(actual.ref).isEqualTo(expected.ref);
    if (expected.revision != null) {
      assertThat(actual.revision).named("revision of " + actual.ref).isEqualTo(expected.revision);
    }
    assertThat(toBoolean(actual.canDelete))
        .named("can delete " + actual.ref)
        .isEqualTo(toBoolean(expected.canDelete));
  }

  private static Iterable<String> refs(Iterable<BranchInfo> infos) {
    return Iterables.transform(infos, b -> b.ref);
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
