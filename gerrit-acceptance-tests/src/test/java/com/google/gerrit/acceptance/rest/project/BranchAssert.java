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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.server.project.ListBranches.BranchInfo;

import java.util.List;

public class BranchAssert {

  public static void assertBranches(List<BranchInfo> expectedBranches,
      List<BranchInfo> actualBranches) {
    List<BranchInfo> missingBranches = Lists.newArrayList(actualBranches);
    for (final BranchInfo b : expectedBranches) {
      BranchInfo info =
          Iterables.find(actualBranches, new Predicate<BranchInfo>() {
            @Override
            public boolean apply(BranchInfo info) {
              return info.ref.equals(b.ref);
            }
          }, null);
      assertThat(info).named("branch " + b.ref).isNotNull();
      assertBranchInfo(b, info);
      missingBranches.remove(info);
    }
    assertThat(missingBranches.isEmpty()).named("" + missingBranches).isTrue();
  }

  public static void assertBranchInfo(BranchInfo expected, BranchInfo actual) {
    assertThat(actual.ref).isEqualTo(expected.ref);
    if (expected.revision != null) {
      assertThat(actual.revision).isEqualTo(expected.revision);
    }
    assertThat(toBoolean(actual.canDelete)).isEqualTo(expected.canDelete);
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
