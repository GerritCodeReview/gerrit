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

package com.google.gerrit.server.notedb;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class CombinedCheckStateTest extends GerritBaseTests {
  @Test
  public void combineNone() {
    assertThat(combine()).isEqualTo(CombinedCheckState.NOT_RELEVANT);
  }

  @Test
  public void combineSingleState() {
    ImmutableMap<CheckState, CombinedCheckState> states =
        ImmutableMap.<CheckState, CombinedCheckState>builder()
            .put(CheckState.FAILED, CombinedCheckState.FAILED)
            .put(CheckState.NOT_STARTED, CombinedCheckState.IN_PROGRESS)
            .put(CheckState.SCHEDULED, CombinedCheckState.IN_PROGRESS)
            .put(CheckState.RUNNING, CombinedCheckState.IN_PROGRESS)
            .put(CheckState.SUCCESSFUL, CombinedCheckState.SUCCESSFUL)
            .put(CheckState.NOT_RELEVANT, CombinedCheckState.NOT_RELEVANT)
            .build();
    assertThat(states.keySet()).containsExactlyElementsIn(CheckState.values());
    for (Map.Entry<CheckState, CombinedCheckState> e : states.entrySet()) {
      for (int i = 1; i <= 5; i++) {
        assertThat(combine(Collections.nCopies(i, e.getKey())))
            .named("%s copies of %s", i, e.getKey())
            .isEqualTo(e.getValue());
      }
    }
  }

  @Test
  public void combineNotRelevantAlwaysLoses() {
    assertThat(combine(CheckState.NOT_RELEVANT, CheckState.FAILED))
        .isEqualTo(CombinedCheckState.FAILED);
    assertThat(combine(CheckState.NOT_RELEVANT, CheckState.NOT_STARTED))
        .isEqualTo(CombinedCheckState.IN_PROGRESS);
    assertThat(combine(CheckState.NOT_RELEVANT, CheckState.SCHEDULED))
        .isEqualTo(CombinedCheckState.IN_PROGRESS);
    assertThat(combine(CheckState.NOT_RELEVANT, CheckState.RUNNING))
        .isEqualTo(CombinedCheckState.IN_PROGRESS);
    assertThat(combine(CheckState.NOT_RELEVANT, CheckState.SUCCESSFUL))
        .isEqualTo(CombinedCheckState.SUCCESSFUL);
  }

  @Test
  public void combineInProgressAlwaysWins() {
    ImmutableSet<CheckState> allInProgress =
        ImmutableSet.of(CheckState.NOT_STARTED, CheckState.SCHEDULED, CheckState.RUNNING);
    ImmutableSet<CheckState> others =
        ImmutableSet.of(CheckState.FAILED, CheckState.SUCCESSFUL, CheckState.NOT_RELEVANT);
    assertThat(Sets.union(allInProgress, others)).containsExactlyElementsIn(CheckState.values());

    for (CheckState inProgress : allInProgress) {
      for (Set<CheckState> subset : Sets.powerSet(others)) {
        Set<CheckState> toCombine = Sets.union(ImmutableSet.of(inProgress), subset);
        assertThat(combine(toCombine))
            .named(toCombine.toString())
            .isEqualTo(CombinedCheckState.IN_PROGRESS);
      }
    }
  }

  @Test
  public void combineFailedBeatsSuccessful() {
    assertThat(combine(CheckState.SUCCESSFUL, CheckState.FAILED))
        .isEqualTo(CombinedCheckState.FAILED);
  }

  @Test
  public void combineWarningBeatsSuccessful() {
    assertThat(
            combine(statesBuilder().put(CheckState.SUCCESSFUL, true).put(CheckState.FAILED, false)))
        .isEqualTo(CombinedCheckState.WARNING);
  }

  @Test
  public void combineWarning() {
    assertThat(combine(statesBuilder().put(CheckState.FAILED, false).put(CheckState.FAILED, false)))
        .isEqualTo(CombinedCheckState.WARNING);
    assertThat(combine(statesBuilder().put(CheckState.FAILED, false).put(CheckState.FAILED, true)))
        .isEqualTo(CombinedCheckState.FAILED);
  }

  private static ImmutableListMultimap.Builder<CheckState, Boolean> statesBuilder() {
    return ImmutableListMultimap.builder();
  }

  private static CombinedCheckState combine(CheckState... states) {
    return combine(Arrays.asList(states));
  }

  private static CombinedCheckState combine(Collection<CheckState> states) {
    return CombinedCheckState.combine(
        states.stream().collect(toImmutableListMultimap(s -> s, s -> true)));
  }

  private static CombinedCheckState combine(
      ImmutableListMultimap.Builder<CheckState, Boolean> states) {
    return CombinedCheckState.combine(states.build());
  }
}
