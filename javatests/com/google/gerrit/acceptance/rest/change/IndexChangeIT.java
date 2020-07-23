// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class IndexChangeIT extends AbstractDaemonTest {
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void indexChange() throws Exception {
    String changeId = createChange().getChangeId();
    adminRestSession.post("/changes/" + changeId + "/index/").assertNoContent();
  }

  @Test
  public void indexChangeOnNonVisibleBranch() throws Exception {
    String changeId = createChange().getChangeId();
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();
    userRestSession.post("/changes/" + changeId + "/index/").assertNotFound();
  }

  @Test
  @GerritConfig(
      name = "change.mergeabilityComputationBehavior",
      value = "API_REF_UPDATED_AND_CHANGE_REINDEX")
  public void indexChangeAfterOwnerLosesVisibility() throws Exception {
    // Create a test group with 2 users as members
    TestAccount user2 = accountCreator.user2();
    AccountGroup.UUID groupId = groupOperations.newGroup().name("test").create();
    String group = groupOperations.group(groupId).get().name();
    gApi.groups().id(group).addMembers("admin", "user", user2.username());

    // Create a project and restrict its visibility to the group
    Project.NameKey p = projectOperations.newProject().create();
    projectOperations
        .project(p)
        .forUpdate()
        .add(
            allow(Permission.READ)
                .ref("refs/*")
                .group(groupCache.get(AccountGroup.nameKey(group)).get().getGroupUUID()))
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // Clone it and push a change as a regular user
    TestRepository<InMemoryRepository> repo = cloneProject(p, user);
    PushOneCommit push = pushFactory.create(user.newIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    assertThat(result.getChange().change().getOwner()).isEqualTo(user.id());
    String changeId = result.getChangeId();

    // User can see the change and it is mergeable
    requestScopeOperations.setApiUser(user.id());
    List<ChangeInfo> changes = gApi.changes().query(changeId).get();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).mergeable).isNotNull();

    // Other user can see the change and it is mergeable
    requestScopeOperations.setApiUser(user2.id());
    changes = gApi.changes().query(changeId).get();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).mergeable).isTrue();

    // Remove the user from the group so they can no longer see the project
    requestScopeOperations.setApiUser(admin.id());
    gApi.groups().id(group).removeMembers("user");

    // User can no longer see the change
    requestScopeOperations.setApiUser(user.id());
    changes = gApi.changes().query(changeId).get();
    assertThat(changes).isEmpty();

    // Reindex the change
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(changeId).index();

    // Other user can still see the change and it is still mergeable
    requestScopeOperations.setApiUser(user2.id());
    changes = gApi.changes().query(changeId).get();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).mergeable).isTrue();
  }
}
