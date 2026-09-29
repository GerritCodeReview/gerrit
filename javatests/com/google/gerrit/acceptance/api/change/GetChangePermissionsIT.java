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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.AI_REVIEW;
import static com.google.gerrit.entities.Permission.DELETE_COMMENT;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ChangePermissionsInfo;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class GetChangePermissionsIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void deleteCommentFalseByDefault() throws Exception {
    String changeId = createChange().getChangeId();
    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(DELETE_COMMENT);
  }

  @Test
  public void deleteCommentTrueWhenGranted() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(DELETE_COMMENT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).contains(DELETE_COMMENT);
  }

  @Test
  public void deleteCommentTrueForAdmin() throws Exception {
    String changeId = createChange().getChangeId();

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).contains(DELETE_COMMENT);
  }

  @Test
  public void deleteCommentRespectsBlockRule() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(DELETE_COMMENT).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(block(DELETE_COMMENT).ref("refs/heads/sensitive").group(REGISTERED_USERS))
        .update();
    createBranch(BranchNameKey.create(project, "sensitive"));

    PushOneCommit.Result onMaster = createChange("refs/for/master");
    PushOneCommit.Result onSensitive = createChange("refs/for/sensitive");

    requestScopeOperations.setApiUser(user.id());

    assertThat(gApi.changes().id(onMaster.getChangeId()).permissions().permissions)
        .contains(DELETE_COMMENT);
    assertThat(gApi.changes().id(onSensitive.getChangeId()).permissions().permissions)
        .doesNotContain(DELETE_COMMENT);
  }

  @Test
  public void aiReviewTrueByDefaultForRegisteredUser() throws Exception {
    String changeId = createChange().getChangeId();
    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).contains(AI_REVIEW);
  }

  @Test
  public void aiReviewTrueForAdmin() throws Exception {
    String changeId = createChange().getChangeId();

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).contains(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenDenied() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenBlocked() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewRespectsBlockOnRefPattern() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(AI_REVIEW).ref("refs/heads/sensitive").group(REGISTERED_USERS))
        .update();
    createBranch(BranchNameKey.create(project, "sensitive"));

    PushOneCommit.Result onMaster = createChange("refs/for/master");
    PushOneCommit.Result onSensitive = createChange("refs/for/sensitive");

    requestScopeOperations.setApiUser(user.id());

    assertThat(gApi.changes().id(onMaster.getChangeId()).permissions().permissions)
        .contains(AI_REVIEW);
    assertThat(gApi.changes().id(onSensitive.getChangeId()).permissions().permissions)
        .doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenAllProjectsGrantRemoved() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .allProjectsForUpdate()
        .remove(permissionKey(AI_REVIEW).ref("refs/heads/*"))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentForAdminWhenAdminGroupBlocked() throws Exception {
    String changeId = createChange().getChangeId();

    // Admins are not exempt: a BLOCK on the admin group overrides the seeded
    // grant (a bare DENY would only cancel an ALLOW for the same group).
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenDenySuppressesAllow() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(deny(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenAllowForOtherGroupAndDenyForUserGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .add(deny(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewPresentWhenAllowForUserGroupAndDenyForOtherGroup() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(deny(AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).contains(AI_REVIEW);
  }

  @Test
  public void aiReviewPresentWhenDenyTargetsOtherGroup() throws Exception {
    String changeId = createChange().getChangeId();

    // A DENY on a group the caller is not in does not cancel the seeded grant.
    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).contains(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenDenyInheritedFromAllProjects() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .allProjectsForUpdate()
        .add(deny(AI_REVIEW).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewAbsentWhenGrantedToOtherGroupOnly() throws Exception {
    String changeId = createChange().getChangeId();

    // Drop the seeded grant, then grant only the admin group: a non-admin
    // registered user is covered by no ALLOW rule and is denied.
    projectOperations
        .allProjectsForUpdate()
        .remove(permissionKey(AI_REVIEW).ref("refs/heads/*"))
        .update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(AI_REVIEW).ref("refs/heads/*").group(adminGroupUuid()))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.permissions).doesNotContain(AI_REVIEW);
  }

  @Test
  public void aiReviewInheritedByOtherProjectsFromAllProjects() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    TestRepository<InMemoryRepository> otherRepo = cloneProject(otherProject);
    String otherChangeId =
        pushFactory.create(admin.newIdent(), otherRepo).to("refs/for/master").getChangeId();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(otherChangeId).permissions();

    assertThat(info.permissions).contains(AI_REVIEW);
  }
}
