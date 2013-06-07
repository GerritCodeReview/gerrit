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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

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
      assertNotNull("missing branch: " + b.ref, info);
      assertBranchInfo(b, info);
      missingBranches.remove(info);
    }
    assertTrue("unexpected branches: " + missingBranches,
        missingBranches.isEmpty());
  }

  public static void assertBranchInfo(BranchInfo expected, BranchInfo actual) {
    assertEquals(expected.ref, actual.ref);
    if (expected.revision != null) {
      assertEquals(expected.revision, actual.revision);
    }
    assertEquals(expected.can_delete, toBoolean(actual.can_delete));
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
