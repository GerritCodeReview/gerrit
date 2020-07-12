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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.HashMap;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

/** Asserts behavior on {@link PermissionBackend} using a fully-started Gerrit. */
public class PermissionBackendIT extends AbstractDaemonTest {
  @Inject PermissionBackend pb;
  @Inject ChangeNotes.Factory changeNotesFactory;
  @Inject RequestScopeOperations requestScopeOperations;

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
  public void changeNotes_canCheckReviewerState() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    gApi.changes().id(changeId.get()).setPrivate(true);
    gApi.changes().id(changeId.get()).addReviewer(user.email());

    ChangeNotes notes = changeNotesFactory.create(project, changeId);
    boolean reviewerCanSee = pb.absentUser(user.id()).change(notes).test(ChangePermission.READ);
    assertThat(reviewerCanSee).isTrue();
  }

  @Test
  public void checkSubmitPermission() throws Exception {
    String ref = Constants.R_HEADS + "master";
    Change.Id changeId = createChange().getChange().getId();
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    requestScopeOperations.setApiUser(user.id());

    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(changeId.get()).current().submit());
    assertThat(thrown).hasMessageThat().isEqualTo("submit not permitted");

    assertThat(pb.absentUser(user.id()).project(project).ref(ref).test(RefPermission.SUBMIT))
        .isFalse();

    requestScopeOperations.setApiUser(admin.id());
    ProjectAccessInput accessInput = newProjectAccessInput();
    AccessSectionInfo accessSectionInfo = createSubmitSectionInfo();

    accessInput.add.put(ref, accessSectionInfo);
    gApi.projects().name(project.get()).access(accessInput);

    requestScopeOperations.setApiUser(user.id());
    assertThat(pb.absentUser(user.id()).project(project).ref(ref).test(RefPermission.SUBMIT))
        .isTrue();

    gApi.changes().id(changeId.get()).current().submit();
    assertThat(gApi.changes().id(changeId.get()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  private static ProjectAccessInput newProjectAccessInput() {
    ProjectAccessInput p = new ProjectAccessInput();
    p.add = new HashMap<>();
    p.remove = new HashMap<>();
    return p;
  }

  private static AccessSectionInfo createSubmitSectionInfo() {
    AccessSectionInfo accessSection = newAccessSectionInfo();

    PermissionInfo submit = newPermissionInfo();
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.ALLOW, false);
    submit.rules.put(SystemGroupBackend.REGISTERED_USERS.get(), pri);
    accessSection.permissions.put(Permission.SUBMIT, submit);

    return accessSection;
  }

  private static AccessSectionInfo newAccessSectionInfo() {
    AccessSectionInfo a = new AccessSectionInfo();
    a.permissions = new HashMap<>();
    return a;
  }

  private static PermissionInfo newPermissionInfo() {
    PermissionInfo p = new PermissionInfo(null, null);
    p.rules = new HashMap<>();
    return p;
  }
}
