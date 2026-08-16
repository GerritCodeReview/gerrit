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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

  @Test
  public void benchmarkBranchOrderLookup() {
    List<String> branches = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      branches.add("branch-" + i);
    }
    BranchOrderSection section = BranchOrderSection.create(branches);

    long dummySum = 0;
    // Warm up JVM
    for (int i = 0; i < 50_000; i++) {
      dummySum += section.getMoreStable("branch-" + (i % 20)).size();
      dummySum += section.getMoreStable("refs/heads/branch-" + (i % 20)).size();
      dummySum += section.getMoreStable("nonexistent-branch").size();
    }

    int iterations = 200_000;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      dummySum += section.getMoreStable("branch-" + (i % 20)).size();
      dummySum += section.getMoreStable("refs/heads/branch-" + (i % 20)).size();
      dummySum += section.getMoreStable("nonexistent-branch").size();
    }
    long elapsedNs = System.nanoTime() - start;
    double avgLatencyNs = (double) elapsedNs / (iterations * 3);
    System.out.printf(
        "[BENCHMARK] Total elapsed: %d ms, Operations: %d, Avg latency: %.2f ns/op (dummySum=%d)%n",
        TimeUnit.NANOSECONDS.toMillis(elapsedNs), iterations * 3, avgLatencyNs, dummySum);
  }
}
