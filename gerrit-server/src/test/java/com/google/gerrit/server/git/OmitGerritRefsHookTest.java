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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class OmitGerritRefsHookTest {
  private static final ObjectId A = ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  private static final ObjectId B = ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  private static final ObjectId C = ObjectId.fromString("cccccccccccccccccccccccccccccccccccccccc");

  /**
   * A test case based on a map of all refs, where the set of <em>omitted</em> and <em>allowed</em>
   * refs are specified by name.
   *
   * <p><em>Omitted</em> refs are those refs that would be omitted by {@link OmitGerritRefsHook},
   * which may include some refs that the user has permissions to see.
   *
   * <p><em>Allowed</em> refs are the subset of the omitted refs that the user has permissions to
   * see.
   */
  private static class TestCase {
    static TestCase withAllRefsVisible(Map<String, ObjectId> allRefs, Set<String> omittedRefNames) {
      return new TestCase(allRefs, omittedRefNames, omittedRefNames);
    }

    private final Map<String, ObjectId> allRefs;
    private final Set<String> omittedRefNames;
    private final Set<String> allowedRefNames;

    TestCase(
        Map<String, ObjectId> allRefs, Set<String> omittedRefNames, Set<String> allowedRefNames) {
      checkArgument(
          allRefs.keySet().containsAll(omittedRefNames),
          "omitted ref names %s must be a subset of all refs %s",
          omittedRefNames,
          allRefs.keySet());
      checkArgument(
          omittedRefNames.containsAll(allowedRefNames),
          "allowed ref names %s must be a subset of omitted ref names %s",
          allowedRefNames,
          omittedRefNames);
      this.allRefs = allRefs;
      this.omittedRefNames = omittedRefNames;
      this.allowedRefNames = allowedRefNames;
    }

    Set<ObjectId> trimToNonTipWants(ObjectId... wants) throws Exception {
      ListMultimap<ObjectId, String> omittedById =
          MultimapBuilder.hashKeys().arrayListValues().build();
      omittedRefNames.forEach(r -> omittedById.put(allRefs.get(r), r));
      return OmitGerritRefsHook.trimToNonTipWants(
          Arrays.asList(wants),
          omittedById,
          names -> Maps.filterKeys(allRefs, names::contains),
          refs -> Maps.filterKeys(allRefs, allowedRefNames::contains));
    }
  }

  @Test
  public void allWantsSatisfiedByVisibleTips() throws Exception {
    TestCase t =
        TestCase.withAllRefsVisible(
            ImmutableMap.of("refs/omitted/a", A, "refs/omitted/b", B, "refs/notomitted/c", C),
            ImmutableSet.of("refs/omitted/a", "refs/omitted/b"));
    assertThat(t.trimToNonTipWants(A)).isEmpty();
    assertThat(t.trimToNonTipWants(B)).isEmpty();
    assertThat(t.trimToNonTipWants(A, B)).isEmpty();
  }

  @Test
  public void wantSatisfiedByOneVisibleRefAndOneInvisibleRef() throws Exception {
    Map<String, ObjectId> allRefs =
        ImmutableMap.of("refs/omitted/a1", A, "refs/omitted/a2", A, "refs/notomitted/c", C);
    Set<String> omittedRefNames = ImmutableSet.of("refs/omitted/a1", "refs/omitted/a2");

    TestCase onlyA1Visible =
        new TestCase(allRefs, omittedRefNames, ImmutableSet.of("refs/omitted/a1"));
    assertThat(onlyA1Visible.trimToNonTipWants(A)).isEmpty();
    assertThat(onlyA1Visible.trimToNonTipWants(A, B)).containsExactly(B);

    TestCase onlyA2Visible =
        new TestCase(allRefs, omittedRefNames, ImmutableSet.of("refs/omitted/a2"));
    assertThat(onlyA2Visible.trimToNonTipWants(A)).isEmpty();
    assertThat(onlyA2Visible.trimToNonTipWants(A, B)).containsExactly(B);
  }

  @Test
  public void allWantsSatisfiedByTipsButNotAllTipsVisible() throws Exception {
    Map<String, ObjectId> allRefs =
        ImmutableMap.of("refs/omitted/a", A, "refs/omitted/b", B, "refs/notomitted/c", C);
    Set<String> omittedRefNames = ImmutableSet.of("refs/omitted/a", "refs/omitted/b");

    TestCase onlyAVisible = new TestCase(allRefs, omittedRefNames, ImmutableSet.of("refs/omitted/a"));
    assertThat(onlyAVisible.trimToNonTipWants(A)).isEmpty();
    assertThat(onlyAVisible.trimToNonTipWants(A, B)).containsExactly(B);
    assertThat(onlyAVisible.trimToNonTipWants(A, B, C)).containsExactly(B, C);

    TestCase noRefsVisible = new TestCase(allRefs, omittedRefNames, ImmutableSet.of());
    assertThat(noRefsVisible.trimToNonTipWants(A)).containsExactly(A);
    assertThat(noRefsVisible.trimToNonTipWants(A, B)).containsExactly(A, B);
    assertThat(noRefsVisible.trimToNonTipWants(A, B, C)).containsExactly(A, B, C);
  }

  @Test
  public void notAllWantsSatisfiedByTips() throws Exception {
    TestCase t =
        TestCase.withAllRefsVisible(
            ImmutableMap.of("refs/omitted/a", A, "refs/notomitted/b", B),
            ImmutableSet.of("refs/omitted/a"));
    assertThat(t.trimToNonTipWants(A)).isEmpty();
    assertThat(t.trimToNonTipWants(A, C)).containsExactly(C);
  }
}
