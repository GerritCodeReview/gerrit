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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestChanges;
import java.util.UUID;
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
  public void notesDoesNotClearPrepopulatedPatchSets() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);
    Change testChange = TestChanges.newChange(project, Account.id(1000), 1);
    ChangeData cd =
        ChangeData.createForTest(
            project,
            changeNum,
            1,
            ObjectId.zeroId(),
            new ChangeNumberNoopAlgorithm(),
            null,
            changeNotesMock);
    cd.setChange(testChange);
    PatchSet ps1 = newPatchSet(cd.getId(), 1);
    cd.setPatchSets(ImmutableList.of(ps1));
    assertThat(cd.patchSets()).containsExactly(ps1);

    // Accessing notes() should not wipe the patchSets that were already set
    ChangeNotes notes = cd.notes();
    assertThat(notes).isSameInstanceAs(changeNotesMock);
    assertThat(cd.patchSets()).containsExactly(ps1);
    assertThat(cd.currentPatchSet()).isEqualTo(ps1);
  }

  @Test
  public void reloadChangeClearsCachedFields() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);
    ChangeNotes.Factory notesFactoryMock = org.mockito.Mockito.mock(ChangeNotes.Factory.class);
    when(notesFactoryMock.createChecked(project, changeNum, null)).thenReturn(changeNotesMock);
    Change testChange = TestChanges.newChange(project, Account.id(1000));
    when(changeNotesMock.getChange()).thenReturn(testChange);

    ChangeData cd =
        ChangeData.createForTest(
            project,
            changeNum,
            1,
            ObjectId.zeroId(),
            new ChangeNumberNoopAlgorithm(),
            notesFactoryMock,
            null);
    PatchSet ps1 = newPatchSet(cd.getId(), 1);
    cd.setPatchSets(ImmutableList.of(ps1));
    cd.setMessages(ImmutableList.of());
    cd.setReviewedBy(java.util.Collections.singleton(Account.id(1000)));

    assertThat(cd.patchSets()).containsExactly(ps1);
    assertThat(cd.messages()).isEmpty();
    assertThat(cd.reviewedBy()).containsExactly(Account.id(1000));

    cd.reloadChange();
  }

  @Test
  public void metaRevisionCachesResolvedIdFromRefStates() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);
    ChangeData cd =
        ChangeData.createForTest(
            project, changeNum, 1, ObjectId.zeroId(), new ChangeNumberNoopAlgorithm(), null, null);
    ObjectId metaSha1 = ObjectId.fromString("1111111111111111111111111111111111111111");
    cd.setRefStates(
        com.google.common.collect.ImmutableSetMultimap.of(
            project,
            com.google.gerrit.index.RefState.create(
                com.google.gerrit.entities.RefNames.changeMetaRef(changeNum), metaSha1)));

    assertThat(cd.metaRevision()).hasValue(metaSha1);
    assertThat(cd.metaRevisionOrThrow()).isEqualTo(metaSha1);
  }

  @Test
  public void setMessagesAndReviewedByDirectlyHydrates() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);
    ChangeData cd =
        ChangeData.createForTest(
            project, changeNum, 1, ObjectId.zeroId(), new ChangeNumberNoopAlgorithm(), null, null);
    cd.setChange(TestChanges.newChange(project, Account.id(1000)));

    com.google.gerrit.entities.ChangeMessage msg =
        com.google.gerrit.entities.ChangeMessage.create(
            com.google.gerrit.entities.ChangeMessage.key(changeNum, "uuid-1"),
            Account.id(1001),
            TimeUtil.now(),
            PatchSet.id(changeNum, 1),
            "LGTM",
            Account.id(1001),
            null);
    cd.setMessages(ImmutableList.of(msg));

    assertThat(cd.messages()).containsExactly(msg);
    assertThat(cd.reviewedBy()).containsExactly(Account.id(1001));
  }

  @Test
  public void ensureReviewedByLoadedForOpenChangesHydratesWithoutPatchSets() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    Change.Id changeNum = Change.id(1);
    ChangeData cd =
        ChangeData.createForTest(
            project, changeNum, 1, ObjectId.zeroId(), new ChangeNumberNoopAlgorithm(), null, null);
    Change change = TestChanges.newChange(project, Account.id(1000));
    cd.setChange(change);

    com.google.gerrit.entities.ChangeMessage msg =
        com.google.gerrit.entities.ChangeMessage.create(
            com.google.gerrit.entities.ChangeMessage.key(changeNum, "uuid-1"),
            Account.id(1002),
            TimeUtil.now(),
            PatchSet.id(changeNum, 1),
            "Looks good",
            Account.id(1002),
            null);
    cd.setMessages(ImmutableList.of(msg));

    ChangeData.ensureReviewedByLoadedForOpenChanges(ImmutableList.of(cd));

    assertThat(cd.reviewedBy()).containsExactly(Account.id(1002));
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
