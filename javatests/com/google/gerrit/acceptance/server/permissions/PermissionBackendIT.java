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

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
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
  @Inject GroupOperations groupOperations;

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
    // A change owned by a non-admin user, targeting refs/heads/master.
    TestRepository<?> userRepo = cloneProject(project, user);
    Change.Id changeId =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master").getChange().getId();
    ChangeNotes notes = changeNotesFactory.create(project, changeId);

    // With requireWriteChange off the gate imposes no restriction: any identity resolves Write
    // Change without a grant.
    Account.Id stranger = accountOperations.newAccount().create();
    assertThat(pb.absentUser(stranger).change(notes).test(ChangePermission.WRITE_CHANGE)).isTrue();

    // Enable the gate for the remaining assertions.
    enableRequireWriteChange();

    // The change owner and an administrator are exempt.
    assertThat(pb.absentUser(user.id()).change(notes).test(ChangePermission.WRITE_CHANGE)).isTrue();
    assertThat(pb.absentUser(admin.id()).change(notes).test(ChangePermission.WRITE_CHANGE))
        .isTrue();

    // A plain registered user with no grant and no ownership is denied.
    assertThat(pb.absentUser(stranger).change(notes).test(ChangePermission.WRITE_CHANGE)).isFalse();

    // A branch owner (Owner on the change's destination ref) is exempt.
    Account.Id branchOwner = accountOperations.newAccount().create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allow(Permission.OWNER)
                .ref("refs/heads/master")
                .group(groupOperations.newGroup().addMember(branchOwner).create()))
        .update();
    assertThat(pb.absentUser(branchOwner).change(notes).test(ChangePermission.WRITE_CHANGE))
        .isTrue();

    // A project owner (Owner on refs/*) is exempt.
    Account.Id projectOwner = accountOperations.newAccount().create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allow(Permission.OWNER)
                .ref("refs/*")
                .group(groupOperations.newGroup().addMember(projectOwner).create()))
        .update();
    assertThat(pb.absentUser(projectOwner).change(notes).test(ChangePermission.WRITE_CHANGE))
        .isTrue();

    // A grant on a different branch must not satisfy a change whose destination is
    // refs/heads/master: the permission resolves against the change's own ref.
    Account.Id otherBranchGrantee = accountOperations.newAccount().create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allow(Permission.WRITE_CHANGE)
                .ref("refs/heads/other")
                .group(groupOperations.newGroup().addMember(otherBranchGrantee).create()))
        .update();
    assertThat(pb.absentUser(otherBranchGrantee).change(notes).test(ChangePermission.WRITE_CHANGE))
        .isFalse();

    // A grant on the change's actual destination ref lets a non-owner resolve it.
    Account.Id masterGrantee = accountOperations.newAccount().create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allow(Permission.WRITE_CHANGE)
                .ref("refs/heads/master")
                .group(groupOperations.newGroup().addMember(masterGrantee).create()))
        .update();
    assertThat(pb.absentUser(masterGrantee).change(notes).test(ChangePermission.WRITE_CHANGE))
        .isTrue();
  }

  private void enableRequireWriteChange() throws Exception {
    ConfigInput input = new ConfigInput();
    input.requireWriteChange = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(input);
  }
}
