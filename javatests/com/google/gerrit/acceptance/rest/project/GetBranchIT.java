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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class GetBranchIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() throws Exception {
    // add block permissions that make the tests pass
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/changes/*").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/tags/*").group(ANONYMOUS_USERS))
        // READ access for refs/groups/* must be blocked on All-Projects rather than All-Users. This
        // is because READ access on refs/groups/* on All-Users is by default granted to
        // REGISTERED_USERS, and if an ALLOW rule and a BLOCK rule are on the same project and ref,
        // the ALLOW rule takes precedence.
        .add(block(Permission.READ).ref("refs/groups/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/meta/external-ids").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/meta/group-names").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/deleted-groups/*").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/users/*").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/draft-comments/*").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/starred-changes/*").group(ANONYMOUS_USERS))
        .update();
  }

  @Test
  public void cannotGetNonExistingBranch() throws Exception {
    assertBranchNotFound(project, RefNames.fullName("non-existing"));
  }

  @Test
  public void cannotGetChangeRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    Change.Id changeId = changeOperations.newChange().create();
    assertBranchNotFound(project, RefNames.patchSetRef(PatchSet.id(changeId, 1)));
  }

  @Test
  public void cannotGetChangeRefOfNonVisibleChange() throws Exception {
    String branchName = "master";
    Change.Id changeId = changeOperations.newChange().branch(branchName).create();

    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(ANONYMOUS_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, RefNames.patchSetRef(PatchSet.id(changeId, 1)));
  }

  @Test
  public void cannotGetChangeMetaRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    Change.Id changeId = changeOperations.newChange().create();
    assertBranchNotFound(project, RefNames.changeMetaRef(changeId));
  }

  @Test
  public void cannotGetRefsMetaConfig() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, RefNames.REFS_CONFIG);
  }

  @Test
  public void cannotGetUserRefOfOtherUser() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.refsUsers(admin.id()));
  }

  @Test
  public void cannotGetExternalIdsRefs() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.REFS_EXTERNAL_IDS);
  }

  @Test
  public void cannotGetGroupRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    InternalGroup adminGroup =
        groupCache
            .get(AccountGroup.nameKey("Administrators"))
            .orElseThrow(() -> new IllegalStateException("admin group not found"));
    assertBranchNotFound(allUsers, RefNames.refsGroups(adminGroup.getGroupUUID()));
  }

  @Test
  public void cannotGetGroupNamesRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.REFS_GROUPNAMES);
  }

  @Test
  public void cannotGetDeletedGroupRef() throws Exception {
    String deletedGroupRef = RefNames.refsDeletedGroups(AccountGroup.uuid("deleted-group"));

    // Create the ref.
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(allUsers))) {
      testRepo
          .branch(deletedGroupRef)
          .commit()
          .message("Some Message")
          .add("group.config", "content")
          .create();
    }

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, deletedGroupRef);
  }

  @Test
  public void cannotGetDraftCommentsRef() throws Exception {
    String fileName = "a.txt";
    Change change = createChange("A Change", fileName, "content").getChange().change();

    DraftInput draftInput = new DraftInput();
    draftInput.path = fileName;
    draftInput.line = 0;
    draftInput.message = "Some Comment";
    gApi.changes().id(change.getChangeId()).current().createDraft(draftInput);

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.refsDraftComments(change.getId(), admin.id()));
  }

  @Test
  public void cannotGetStarredChangesRef() throws Exception {
    Change change = createChange().getChange().change();
    gApi.accounts().self().starChange(Integer.toString(change.getChangeId()));

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.refsStarredChanges(change.getId(), admin.id()));
  }

  @Test
  public void cannotGetTagRefThatPointsToNonVisibleBranch() throws Exception {
    TagInput input = new TagInput();
    input.message = "My Tag";
    input.revision = projectOperations.project(project).getHead("master").name();
    TagInfo tagInfo = gApi.projects().name(project.get()).tag("my-tag").create(input).get();

    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName("master")).group(ANONYMOUS_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, tagInfo.ref);
  }

  @Test
  public void cannotGetSymbolicRefThatPointsToNonVisibleBranch() throws Exception {
    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName("master")).group(ANONYMOUS_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, "HEAD");
  }

  private void assertBranchNotFound(Project.NameKey project, String ref) {
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).branch(ref).get());
    assertThat(exception).hasMessageThat().isEqualTo("Not found: " + ref);
  }
}
