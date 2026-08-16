// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestChanges;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChangeDataTest {
  @Mock private ChangeNotes changeNotesMock;

  @Test
  public void setPatchSetsClearsCurrentPatchSet() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    cd.setChange(TestChanges.newChange(project, Account.id(1000)));
    PatchSet curr1 = cd.currentPatchSet();
    int currId = curr1.id().get();
    PatchSet ps1 = newPatchSet(cd.getId(), currId + 1);
    PatchSet ps2 = newPatchSet(cd.getId(), currId + 2);
    cd.setPatchSets(ImmutableList.of(ps1, ps2));
    PatchSet curr2 = cd.currentPatchSet();
    assertThat(curr2).isNotSameInstanceAs(curr1);
  }

  @Test
  public void getChangeVirtualIdUsingAlgorithmAndServerId() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);
    final Change.Id encodedChangeNum = Change.id(12345678);
    String serverId = UUID.randomUUID().toString();

    when(changeNotesMock.getServerId()).thenReturn(serverId);

    ChangeData cd =
        ChangeData.createForTest(
            project,
            changeNum,
            1,
            ObjectId.zeroId(),
            (sid, legacyChangeNum) -> {
              assertThat(sid.get()).isEqualTo(serverId);
              assertThat(legacyChangeNum).isEqualTo(changeNum);
              return encodedChangeNum;
            },
            changeNotesMock);
    verify(changeNotesMock, never()).getServerId();

    assertThat(cd.virtualId().get()).isEqualTo(encodedChangeNum.get());
    verify(changeNotesMock).getServerId();
  }

  @Test
  public void getChangeVirtualIdUsingNoopDefaultAlgorithm() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);

    ChangeData cd =
        ChangeData.createForTest(
            project, changeNum, 1, ObjectId.zeroId(), new ChangeNumberNoopAlgorithm(), null);

    assertThat(cd.virtualId()).isEqualTo(changeNum);
    verify(changeNotesMock, never()).getServerId();
  }

  @Test
  public void setAttentionSet() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    cd.setChange(TestChanges.newChange(project, Account.id(1000)));

    AttentionSetUpdate u1 =
        AttentionSetUpdate.createForWrite(
            Account.id(1001), AttentionSetUpdate.Operation.ADD, "reason 1");
    AttentionSetUpdate u2 =
        AttentionSetUpdate.createForWrite(
            Account.id(1002), AttentionSetUpdate.Operation.REMOVE, "reason 2");

    cd.setAttentionSet(ImmutableSet.of(u1, u2));
    assertThat(cd.attentionSet()).containsExactly(u1, u2);
  }

  @Test
  public void setAttentionSetWithDuplicatesThrows() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    cd.setChange(TestChanges.newChange(project, Account.id(1000)));

    AttentionSetUpdate u1 =
        AttentionSetUpdate.createForWrite(
            Account.id(1001), AttentionSetUpdate.Operation.ADD, "reason 1");
    AttentionSetUpdate u2 =
        AttentionSetUpdate.createForWrite(
            Account.id(1001), AttentionSetUpdate.Operation.REMOVE, "duplicate reason");

    IllegalStateException thrown =
        org.junit.Assert.assertThrows(
            IllegalStateException.class, () -> cd.setAttentionSet(ImmutableSet.of(u1, u2)));
    assertThat(thrown).hasMessageThat().contains("contains duplicate update");
  }

  @Test
  public void benchmarkAttentionSetComputation() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    cd.setChange(TestChanges.newChange(project, Account.id(1000)));

    ImmutableSet.Builder<AttentionSetUpdate> builder = ImmutableSet.builder();
    for (int i = 0; i < 10; i++) {
      builder.add(
          AttentionSetUpdate.createForWrite(
              Account.id(1000 + i),
              i % 2 == 0 ? AttentionSetUpdate.Operation.ADD : AttentionSetUpdate.Operation.REMOVE,
              "reason " + i));
    }
    ImmutableSet<AttentionSetUpdate> updates = builder.build();

    int iterations = 100_000;

    // Warm-up
    for (int i = 0; i < 5_000; i++) {
      validateAttentionSetBaseline(updates);
      cd.setAttentionSet(updates);
    }

    // Benchmark Baseline (stream pipeline + distinct + count)
    Stopwatch baselineTimer = Stopwatch.createStarted();
    for (int i = 0; i < iterations; i++) {
      validateAttentionSetBaseline(updates);
    }
    baselineTimer.stop();
    long baselineNs = baselineTimer.elapsed(TimeUnit.NANOSECONDS);

    // Benchmark Optimized (direct HashSet single-pass)
    Stopwatch optTimer = Stopwatch.createStarted();
    for (int i = 0; i < iterations; i++) {
      cd.setAttentionSet(updates);
    }
    optTimer.stop();
    long optNs = optTimer.elapsed(TimeUnit.NANOSECONDS);

    double speedup = (double) baselineNs / Math.max(optNs, 1);
    System.out.printf(
        "\n=======================================================\n"
            + "ATTENTION SET BENCHMARK (%d entries, %d iterations):\n"
            + "  - Baseline (stream pipeline distinct/count): %d ms (avg %.2f ns/op)\n"
            + "  - Optimized (single-pass set validation):    %d ms (avg %.2f ns/op)\n"
            + "  - Speedup: %.2fx faster\n"
            + "=======================================================\n\n",
        updates.size(),
        iterations,
        baselineNs / 1_000_000,
        (double) baselineNs / iterations,
        optNs / 1_000_000,
        (double) optNs / iterations,
        speedup);

    assertThat(cd.attentionSet()).containsExactlyElementsIn(updates);
  }

  private static void validateAttentionSetBaseline(ImmutableSet<AttentionSetUpdate> attentionSet) {
    if (attentionSet.stream().map(AttentionSetUpdate::account).distinct().count()
        != attentionSet.size()) {
      throw new IllegalStateException("duplicate");
    }
  }

  private static PatchSet newPatchSet(Change.Id changeId, int num) {
    return PatchSet.builder()
        .id(PatchSet.id(changeId, num))
        .commitId(ObjectId.zeroId())
        .uploader(Account.id(1234))
        .realUploader(Account.id(5678))
        .createdOn(TimeUtil.now())
        .build();
  }
}
