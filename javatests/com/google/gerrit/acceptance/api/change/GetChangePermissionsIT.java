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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
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

    assertThat(info.deleteComment).isNull();
  }

  @Test
  public void deleteCommentTrueWhenGranted() throws Exception {
    String changeId = createChange().getChangeId();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE_COMMENT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.deleteComment).isTrue();
  }

  @Test
  public void deleteCommentTrueForAdmin() throws Exception {
    String changeId = createChange().getChangeId();

    ChangePermissionsInfo info = gApi.changes().id(changeId).permissions();

    assertThat(info.deleteComment).isTrue();
  }

  @Test
  public void deleteCommentFalseWhenGrantedOnDifferentProject() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE_COMMENT).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    Project.NameKey otherProject = projectOperations.newProject().create();
    TestRepository<InMemoryRepository> otherRepo = cloneProject(otherProject);
    String otherChangeId =
        pushFactory.create(admin.newIdent(), otherRepo).to("refs/for/master").getChangeId();

    requestScopeOperations.setApiUser(user.id());

    ChangePermissionsInfo info = gApi.changes().id(otherChangeId).permissions();

    assertThat(info.deleteComment).isNull();
  }

  @Test
  public void deleteCommentRespectsRefPattern() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE_COMMENT).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();
    createBranch(BranchNameKey.create(project, "stable"));

    PushOneCommit.Result onMaster = createChange("refs/for/master");
    PushOneCommit.Result onStable = createChange("refs/for/stable");

    requestScopeOperations.setApiUser(user.id());

    assertThat(gApi.changes().id(onMaster.getChangeId()).permissions().deleteComment).isTrue();
    assertThat(gApi.changes().id(onStable.getChangeId()).permissions().deleteComment).isNull();
  }

  @Test
  public void deleteCommentRespectsBlockRule() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE_COMMENT).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(block(Permission.DELETE_COMMENT).ref("refs/heads/sensitive").group(REGISTERED_USERS))
        .update();
    createBranch(BranchNameKey.create(project, "sensitive"));

    PushOneCommit.Result onMaster = createChange("refs/for/master");
    PushOneCommit.Result onSensitive = createChange("refs/for/sensitive");

    requestScopeOperations.setApiUser(user.id());

    assertThat(gApi.changes().id(onMaster.getChangeId()).permissions().deleteComment).isTrue();
    assertThat(gApi.changes().id(onSensitive.getChangeId()).permissions().deleteComment).isNull();
  }
}
