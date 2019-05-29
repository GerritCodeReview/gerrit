// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.blockLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.labelPermissionKey;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

public class ChangeOwnerIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private TestAccount user2;

  @Before
  public void setUp() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    user2 = accountCreator.user2();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void testChangeOwner_OwnerACLNotGranted() throws Exception {
    assertApproveFails(user, createMyChange(testRepo));
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void testChangeOwner_OwnerACLGranted() throws Exception {
    grantApproveToChangeOwner(project);
    approve(user, createMyChange(testRepo));
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void testChangeOwner_NotOwnerACLGranted() throws Exception {
    grantApproveToChangeOwner(project);
    assertApproveFails(user2, createMyChange(testRepo));
  }

  @Test
  public void testChangeOwner_OwnerACLGrantedOnParentProject() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    grantApproveToChangeOwner(project);
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    requestScopeOperations.setApiUser(user.id());
    TestRepository<InMemoryRepository> childRepo = cloneProject(child, user);
    approve(user, createMyChange(childRepo));
  }

  @Test
  public void testChangeOwner_BlockedOnParentProject() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    blockApproveForChangeOwner(project);
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    requestScopeOperations.setApiUser(user.id());
    grantApproveToAll(child);
    TestRepository<InMemoryRepository> childRepo = cloneProject(child, user);
    String changeId = createMyChange(childRepo);

    // change owner cannot approve because Change-Owner group is blocked on parent
    assertApproveFails(user, changeId);

    // other user can approve
    approve(user2, changeId);
  }

  @Test
  public void testChangeOwner_BlockedOnParentProjectAndExclusiveAllowOnChild() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    blockApproveForChangeOwner(project);
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    requestScopeOperations.setApiUser(user.id());
    grantExclusiveApproveToAll(child);
    TestRepository<InMemoryRepository> childRepo = cloneProject(child, user);
    String changeId = createMyChange(childRepo);

    // change owner cannot approve because Change-Owner group is blocked on parent
    assertApproveFails(user, changeId);

    // other user can approve
    approve(user2, changeId);
  }

  private void approve(TestAccount a, String changeId) throws Exception {
    Context old = requestScopeOperations.setApiUser(a.id());
    try {
      gApi.changes().id(changeId).current().review(ReviewInput.approve());
    } finally {
      atrScope.set(old);
    }
  }

  private void assertApproveFails(TestAccount a, String changeId) throws Exception {
    assertThrows(AuthException.class, () -> approve(a, changeId));
  }

  private void grantApproveToChangeOwner(Project.NameKey project) throws Exception {
    grantApprove(project, SystemGroupBackend.CHANGE_OWNER, false);
  }

  private void grantApproveToAll(Project.NameKey project) throws Exception {
    grantApprove(project, SystemGroupBackend.REGISTERED_USERS, false);
  }

  private void grantExclusiveApproveToAll(Project.NameKey project) throws Exception {
    grantApprove(project, SystemGroupBackend.REGISTERED_USERS, true);
  }

  private void grantApprove(Project.NameKey project, AccountGroup.UUID groupUUID, boolean exclusive)
      throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(groupUUID).range(-2, 2))
        .setExclusiveGroup(labelPermissionKey("Code-Review").ref("refs/heads/*"), exclusive)
        .update();
  }

  private void blockApproveForChangeOwner(Project.NameKey project) throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            blockLabel("Code-Review")
                .ref("refs/heads/*")
                .group(SystemGroupBackend.CHANGE_OWNER)
                .range(-2, 2))
        .update();
  }

  private String createMyChange(TestRepository<InMemoryRepository> testRepo) throws Exception {
    PushOneCommit push = pushFactory.create(user.newIdent(), testRepo);
    return push.to("refs/for/master").getChangeId();
  }
}
