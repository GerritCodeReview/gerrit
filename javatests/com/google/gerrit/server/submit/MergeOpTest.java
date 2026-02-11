// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.ImpersonationPermissionMode;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.WithUser;
import com.google.gerrit.server.query.change.ChangeData;
import java.time.Instant;
import java.util.EnumSet;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MergeOpTest {
  @Mock private PermissionBackend permissionBackend;
  @Mock private IdentifiedUser impersonatingUser;
  @Mock private IdentifiedUser realUser;
  @Mock private ChangeData changeData;
  @Mock private WithUser withUser;
  @Mock private ForChange forChange;
  @Mock private Change change;
  @Mock private ChangeNotes changeNotes;

  private PatchSet patchSet;
  private final Change.Id changeId = Change.id(100);

  @Before
  public void setUp() throws Exception {
    when(impersonatingUser.isImpersonated()).thenReturn(true);
    when(impersonatingUser.getRealUser()).thenReturn(realUser);
    when(impersonatingUser.getUserForPermission()).thenReturn(impersonatingUser);
    when(impersonatingUser.getLoggableName()).thenReturn("test-user");

    when(permissionBackend.user(impersonatingUser)).thenReturn(withUser);
    when(permissionBackend.user(impersonatingUser, ImpersonationPermissionMode.REAL_USER))
        .thenReturn(withUser);
    when(permissionBackend.user(impersonatingUser, ImpersonationPermissionMode.THIS_USER))
        .thenReturn(withUser);
    when(withUser.change(changeData)).thenReturn(forChange);

    when(changeData.getId()).thenReturn(changeId);
    when(changeData.change()).thenReturn(change);
    when(change.getId()).thenReturn(changeId);
    when(change.isNew()).thenReturn(true);
    when(changeData.notes()).thenReturn(changeNotes);

    patchSet =
        PatchSet.builder()
            .id(PatchSet.id(changeId, 1))
            .commitId(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .uploader(Account.id(1))
            .realUploader(Account.id(1))
            .createdOn(Instant.now())
            .build();
  }

  @Test
  public void submitAsPermissionDenied() throws Exception {
    when(forChange.test(
            EnumSet.of(
                ChangePermission.READ, ChangePermission.SUBMIT, ChangePermission.SUBMIT_AS)))
        .thenReturn(EnumSet.of(ChangePermission.READ, ChangePermission.SUBMIT));
    when(forChange.test(ChangePermission.READ)).thenReturn(true);
    when(forChange.test(ChangePermission.SUBMIT_AS)).thenReturn(false);

    ChangeSet changeSet = new ChangeSet(ImmutableList.of(changeData), ImmutableList.of());
    ImmutableList<MergeOp.ChangeProblem> problems =
        MergeOp.checkCommonSubmitProblems(
            change, changeSet, /* allowMerged= */ false, permissionBackend, impersonatingUser);

    assertThat(problems).hasSize(1);
    assertThat(problems.get(0).getChangeId()).isEqualTo(changeId);
    assertThat(problems.get(0).getProblem())
        .contains("Insufficient permission to submit change");
  }

  @Test
  public void submitAsPermissionGranted() throws Exception {
    when(forChange.test(
            EnumSet.of(
                ChangePermission.READ, ChangePermission.SUBMIT, ChangePermission.SUBMIT_AS)))
        .thenReturn(
            EnumSet.of(
                ChangePermission.READ, ChangePermission.SUBMIT, ChangePermission.SUBMIT_AS));
    when(forChange.test(ChangePermission.READ)).thenReturn(true);
    when(forChange.test(ChangePermission.SUBMIT_AS)).thenReturn(true);
    when(changeData.currentPatchSet()).thenReturn(patchSet);
    when(changeData.submitRequirementsIncludingLegacy()).thenReturn(ImmutableMap.of());

    ChangeSet changeSet = new ChangeSet(ImmutableList.of(changeData), ImmutableList.of());
    ImmutableList<MergeOp.ChangeProblem> problems =
        MergeOp.checkCommonSubmitProblems(
            change, changeSet, /* allowMerged= */ false, permissionBackend, impersonatingUser);

    assertThat(problems).isEmpty();
  }
}
