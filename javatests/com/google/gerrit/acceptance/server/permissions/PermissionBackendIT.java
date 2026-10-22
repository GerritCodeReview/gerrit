// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Asserts behavior on {@link PermissionBackend} using a fully-started Gerrit. */
public class PermissionBackendIT extends AbstractDaemonTest {
  @Inject PermissionBackend pb;
  @Inject ChangeNotes.Factory changeNotesFactory;
  @Inject AccountOperations accountOperations;
  @Inject ProjectOperations projectOperations;

  @Test
  public void changeDataFromIndex_canCheckReviewerState() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    gApi.changes().id(changeId.get()).setPrivate(true);
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    ChangeData changeData =
        Iterables.getOnlyElement(queryProvider.get().byLegacyChangeId(changeId));
    boolean reviewerCanSee =
        pb.absentUser(user.id()).change(changeData).test(ChangePermission.READ);
    assertThat(reviewerCanSee).isTrue();
  }

  @Test
  public void changeDataFromNoteDb_canCheckReviewerState() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    gApi.changes().id(changeId.get()).setPrivate(true);
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    ChangeNotes notes = changeNotesFactory.create(project, changeId);
    ChangeData changeData = changeDataFactory.create(notes);
    boolean reviewerCanSee =
        pb.absentUser(user.id()).change(changeData).test(ChangePermission.READ);
    assertThat(reviewerCanSee).isTrue();
  }

  @Test
  public void nonPrivateChangeDataWithoutChange_canCheckReadWithoutLoadingChange()
      throws Exception {

    Change.Id nonExistentChangeId = Change.id(Integer.MAX_VALUE);
    ChangeData changeData =
        changeDataFactory.createNonPrivate(
            BranchNameKey.create(project, "refs/heads/master"),
            nonExistentChangeId,
            ObjectId.zeroId());

    boolean canSee =
        pb.absentUser(user.id()).project(project).change(changeData).test(ChangePermission.READ);
    assertThat(canSee).isTrue();
  }

  @Test
  public void changeNotes_canCheckReviewerState() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    gApi.changes().id(changeId.get()).setPrivate(true);
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    ChangeNotes notes = changeNotesFactory.create(project, changeId);
    boolean reviewerCanSee = pb.absentUser(user.id()).change(notes).test(ChangePermission.READ);
    assertThat(reviewerCanSee).isTrue();
  }

  @Test
  public void writeChangePermissionResolution() throws Exception {
    // A change owned by a non-admin user.
    TestRepository<?> userRepo = cloneProject(project, user);
    Change.Id changeId =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master").getChange().getId();
    ChangeNotes notes = changeNotesFactory.create(project, changeId);
    Account.Id other = accountOperations.newAccount().create();

    // The change owner and an administrator resolve Write Change without a grant; a non-owner
    // without a grant does not.
    assertThat(pb.absentUser(user.id()).change(notes).test(ChangePermission.WRITE_CHANGE)).isTrue();
    assertThat(pb.absentUser(admin.id()).change(notes).test(ChangePermission.WRITE_CHANGE))
        .isTrue();
    assertThat(pb.absentUser(other).change(notes).test(ChangePermission.WRITE_CHANGE)).isFalse();

    // Granting Write Change on the destination ref lets the non-owner resolve it.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.WRITE_CHANGE).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    assertThat(pb.absentUser(other).change(notes).test(ChangePermission.WRITE_CHANGE)).isTrue();
  }
}
