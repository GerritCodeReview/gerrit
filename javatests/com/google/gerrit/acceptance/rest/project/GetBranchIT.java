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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.capabilityKey;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.Sequence;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class GetBranchIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @ConfigSuite.Config
  public static Config skipFalse() {
    Config config = new Config();
    config.setBoolean("auth", null, "skipFullRefEvaluationIfAllRefsAreVisible", false);
    return config;
  }

  @Test
  public void cannotGetNonExistingBranch() {
    assertBranchNotFound(project, RefNames.fullName("non-existing"));
  }

  @Test
  public void getBranch() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchFound(project, RefNames.fullName("master"));
  }

  @Test
  public void getBranchByShortName() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchFound(project, "master");
  }

  @Test
  public void cannotGetNonVisibleBranch() throws Exception {
    String branchName = "master";

    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(ANONYMOUS_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, RefNames.fullName(branchName));
  }

  @Test
  public void cannotGetNonVisibleBranchByShortName() throws Exception {
    String branchName = "master";

    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(ANONYMOUS_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, branchName);
  }

  @Test
  public void getChangeRef() throws Exception {
    // create a change
    Change.Id changeId = changeOperations.newChange().project(project).createV1();

    // a user without the 'Access Database' capability can see the change ref
    requestScopeOperations.setApiUser(user.id());
    String changeRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));
    assertBranchFound(project, changeRef);
  }

  @Test
  public void getChangeRefOfNonVisibleChange() throws Exception {
    // create a change
    String branchName = "master";
    Change.Id changeId =
        changeOperations.newChange().project(project).branch(branchName).createV1();

    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(ANONYMOUS_USERS))
        .update();

    // a user without the 'Access Database' capability cannot see the change ref
    requestScopeOperations.setApiUser(user.id());
    String changeRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));
    assertBranchNotFound(project, changeRef);

    // a user with the 'Access Database' capability can see the change ref
    testGetRefWithAccessDatabase(project, changeRef);
  }

  @Test
  public void getChangeEditRef() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create a change
    Change.Id changeId = changeOperations.newChange().project(project).createV1();

    // create a change edit by 'user'
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.get()).edit().create();

    // every user can see their own change edit refs
    String changeEditRef = RefNames.refsEdit(user.id(), changeId, PatchSet.id(changeId, 1));
    assertBranchFound(project, changeEditRef);

    // a user without the 'Access Database' capability cannot see the change edit ref of another
    // user
    requestScopeOperations.setApiUser(user2.id());
    assertBranchNotFound(project, changeEditRef);

    // a user with the 'Access Database' capability can see the change edit ref of another user
    testGetRefWithAccessDatabase(project, changeEditRef);
  }

  @Test
  public void cannotGetChangeEditRefOfNonVisibleChange() throws Exception {
    // create a change
    String branchName = "master";
    Change.Id changeId =
        changeOperations.newChange().project(project).branch(branchName).createV1();

    // create a change edit by 'user'
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.get()).edit().create();

    // make the change non-visible by blocking read access on the destination
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(ANONYMOUS_USERS))
        .update();

    // user cannot see their own change edit refs if the change is no longer visible
    String changeEditRef = RefNames.refsEdit(user.id(), changeId, PatchSet.id(changeId, 1));
    assertBranchNotFound(project, changeEditRef);

    // a user with the 'Access Database' capability can see the change edit ref
    testGetRefWithAccessDatabase(project, changeEditRef);
  }

  @Test
  public void getChangeMetaRef() throws Exception {
    // create a change
    Change.Id changeId = changeOperations.newChange().project(project).createV1();

    // A user without the 'Access Database' capability can see the change meta ref.
    // This may be surprising, as 'Access Database' guards access to meta refs and the change meta
    // ref is a meta ref, however change meta refs have been always visible to all users that can
    // see the change and some tools rely on seeing these refs, so we have to keep the current
    // behaviour.
    requestScopeOperations.setApiUser(user.id());
    String changeMetaRef = RefNames.changeMetaRef(changeId);
    assertBranchFound(project, changeMetaRef);
  }

  @Test
  public void getRefsMetaConfig() throws Exception {
    // a non-project owner cannot get the refs/meta/config branch
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, RefNames.REFS_CONFIG);

    // a non-project owner cannot get the refs/meta/config branch even with the 'Access Database'
    // capability
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    try {
      assertBranchNotFound(project, RefNames.REFS_CONFIG);
    } finally {
      projectOperations
          .allProjectsForUpdate()
          .remove(
              capabilityKey(GlobalCapability.ACCESS_DATABASE)
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();
    }

    requestScopeOperations.setApiUser(user.id());

    // a project owner can get the refs/meta/config branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    assertBranchFound(project, RefNames.REFS_CONFIG);
  }

  @Test
  public void getUserRefOfOtherUser() throws Exception {
    String userRef = RefNames.refsUsers(admin.id());

    // a user without the 'Access Database' capability cannot see the user ref of another user
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, userRef);

    // a user with the 'Access Database' capability can see the user ref of another user
    testGetRefWithAccessDatabase(allUsers, userRef);
  }

  @Test
  public void getOwnUserRef() throws Exception {
    // every user can see the own user ref
    requestScopeOperations.setApiUser(user.id());
    assertBranchFound(allUsers, RefNames.refsUsers(user.id()));

    // every user can see the own user ref via the magic ref/users/self ref. For this special case,
    // the branch in the request is refs/users/self, but the response contains the actual
    // refs/users/$sharded_id/$id
    BranchInfo branchInfo =
        gApi.projects().name(allUsers.get()).branch(RefNames.REFS_USERS_SELF).get();
    assertThat(branchInfo.ref).isEqualTo(RefNames.refsUsers(user.id()));
  }

  @Test
  public void getExternalIdsRefs() throws Exception {
    // a user without the 'Access Database' capability cannot see the refs/meta/external-ids ref
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.REFS_EXTERNAL_IDS);

    // a user with the 'Access Database' capability can see the refs/meta/external-ids ref
    testGetRefWithAccessDatabase(allUsers, RefNames.REFS_EXTERNAL_IDS);
  }

  @Test
  public void getGroupRef() throws Exception {
    // create a group
    AccountGroup.UUID ownerGroupUuid =
        groupOperations.newGroup().name("owner-group").addMember(admin.id()).create();
    AccountGroup.UUID testGroupUuid =
        groupOperations.newGroup().name("test-group").ownerGroupUuid(ownerGroupUuid).create();

    // a non-group owner without the 'Access Database' capability cannot see the group ref
    requestScopeOperations.setApiUser(user.id());
    String groupRef = RefNames.refsGroups(testGroupUuid);
    assertBranchNotFound(allUsers, groupRef);

    // a non-group owner with the 'Access Database' capability can see the group ref
    testGetRefWithAccessDatabase(allUsers, groupRef);

    // a group owner can see the group ref if the group ref is visible
    groupOperations.group(ownerGroupUuid).forUpdate().addMember(user.id()).update();
    assertBranchFound(allUsers, groupRef);

    // A group owner cannot see the group ref if the group ref is not visible.
    // The READ access for refs/groups/* must be blocked on All-Projects rather than All-Users.
    // This is because READ access for refs/groups/* on All-Users is by default granted to
    // REGISTERED_USERS, and if an ALLOW rule and a BLOCK rule are on the same project and ref,
    // the ALLOW rule takes precedence.
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/groups/*").group(ANONYMOUS_USERS))
        .update();
    assertBranchNotFound(allUsers, groupRef);
  }

  @Test
  public void getGroupNamesRef() throws Exception {
    // a user without the 'Access Database' capability cannot see the refs/meta/group-names ref
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.REFS_GROUPNAMES);

    // a user with the 'Access Database' capability can see the refs/meta/group-names ref
    testGetRefWithAccessDatabase(allUsers, RefNames.REFS_GROUPNAMES);
  }

  @Test
  public void getDeletedGroupRef() throws Exception {
    // Create a deleted group ref. We must create a directly in the repo, since group deletion is
    // not supported yet.
    String deletedGroupRef = RefNames.refsDeletedGroups(AccountGroup.uuid("deleted-group"));
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

    // a user with the 'Access Database' capability can see the deleted group ref
    testGetRefWithAccessDatabase(allUsers, deletedGroupRef);
  }

  @Test
  public void getDraftCommentsRef() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create a change
    String fileName = "a.txt";
    Change change = createChange("A Change", fileName, "content").getChange().change();

    // create a draft comment by the by 'user'
    requestScopeOperations.setApiUser(user.id());
    DraftInput draftInput = new DraftInput();
    draftInput.path = fileName;
    draftInput.line = 0;
    draftInput.message = "Some Comment";
    gApi.changes().id(change.getChangeId()).current().createDraft(draftInput);

    // every user can see their own draft comments refs
    // TODO: is this a bug?
    String draftCommentsRef = RefNames.refsDraftComments(change.getId(), user.id());
    assertBranchFound(allUsers, draftCommentsRef);

    // a user without the 'Access Database' capability cannot see the draft comments ref of another
    // user
    requestScopeOperations.setApiUser(user2.id());
    assertBranchNotFound(allUsers, draftCommentsRef);

    // a user with the 'Access Database' capability can see the draft comments ref of another user
    testGetRefWithAccessDatabase(allUsers, draftCommentsRef);
  }

  @Test
  public void getStarredChangesRef() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create a change
    Change change = createChange().getChange().change();

    // let user star the change
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().starChange(Integer.toString(change.getChangeId()));

    // every user can see their own starred changes refs
    // TODO: is this a bug?
    String starredChangesRef = RefNames.refsStarredChanges(change.getId(), user.id());
    assertBranchFound(allUsers, starredChangesRef);

    // a user without the 'Access Database' capability cannot see the starred changes ref of another
    // user
    requestScopeOperations.setApiUser(user2.id());
    assertBranchNotFound(allUsers, starredChangesRef);

    // a user with the 'Access Database' capability can see the starred changes ref of another user
    testGetRefWithAccessDatabase(allUsers, starredChangesRef);
  }

  @Test
  public void getTagRef() throws Exception {
    // create a tag
    TagInput input = new TagInput();
    input.message = "My Tag";
    input.revision = projectOperations.project(project).getHead("master").name();
    TagInfo tagInfo = gApi.projects().name(project.get()).tag("my-tag").create(input).get();

    // any user who can see the project, can see the tag
    requestScopeOperations.setApiUser(user.id());
    assertBranchFound(project, tagInfo.ref);
  }

  @Test
  public void cannotGetTagRefThatPointsToNonVisibleBranch() throws Exception {
    // create a tag
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

    // if the user cannot see the project, the tag is not visible
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, tagInfo.ref);
  }

  @Test
  public void getSymbolicRef() throws Exception {
    // 'HEAD' is visible since it points to 'master' that is visible
    requestScopeOperations.setApiUser(user.id());
    assertBranchFound(project, "HEAD");
  }

  @Test
  public void cannotGetSymbolicRefThatPointsToNonVisibleBranch() throws Exception {
    // block read access to the branch to which HEAD points by default
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName("master")).group(ANONYMOUS_USERS))
        .update();

    // since 'master' is not visible, 'HEAD' which points to 'master' is also not visible
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, "HEAD");
  }

  @Test
  public void getAccountSequenceRef() throws Exception {
    // a user without the 'Access Database' capability cannot see the refs/sequences/accounts ref
    requestScopeOperations.setApiUser(user.id());
    String accountSequenceRef = RefNames.REFS_SEQUENCES + Sequence.NAME_ACCOUNTS;
    assertBranchNotFound(allUsers, accountSequenceRef);

    // a user with the 'Access Database' capability can see the refs/sequences/accounts ref
    testGetRefWithAccessDatabase(allUsers, accountSequenceRef);
  }

  @Test
  public void getChangeSequenceRef() throws Exception {
    // a user without the 'Access Database' capability cannot see the refs/sequences/changes ref
    requestScopeOperations.setApiUser(user.id());
    String changeSequenceRef = RefNames.REFS_SEQUENCES + Sequence.NAME_CHANGES;
    assertBranchNotFound(allProjects, changeSequenceRef);

    // a user with the 'Access Database' capability can see the refs/sequences/changes ref
    testGetRefWithAccessDatabase(allProjects, changeSequenceRef);
  }

  @Test
  public void getGroupSequenceRef() throws Exception {
    // a user without the 'Access Database' capability cannot see the refs/sequences/groups ref
    requestScopeOperations.setApiUser(user.id());
    String groupSequenceRef = RefNames.REFS_SEQUENCES + Sequence.NAME_GROUPS;
    assertBranchNotFound(allUsers, groupSequenceRef);

    // a user with the 'Access Database' capability can see the refs/sequences/groups ref
    testGetRefWithAccessDatabase(allUsers, groupSequenceRef);
  }

  @Test
  public void getVersionMetaRef() throws Exception {
    // TODO: a user without the 'Access Database' capability cannot see the refs/meta/version ref
    // requestScopeOperations.setApiUser(user.id());
    // assertBranchNotFound(allProjects, RefNames.REFS_VERSION);

    // a user with the 'Access Database' capability can see the refs/meta/vaersion ref
    testGetRefWithAccessDatabase(allProjects, RefNames.REFS_VERSION);
  }

  @Test
  public void cannotGetAutoMergeRef() throws Exception {
    String file = "foo/a.txt";

    // Create a base change.
    Change.Id baseChange =
        changeOperations
            .newChange()
            .project(project)
            .branch("master")
            .file(file)
            .content("base content")
            .createV1();
    approve(Integer.toString(baseChange.get()));
    gApi.changes().id(baseChange.get()).current().submit();

    // Create another branch
    String branchName = "foo";
    createBranchWithRevision(
        BranchNameKey.create(project, branchName),
        projectOperations.project(project).getHead("master").name());

    // Create a change in master that touches the file.
    Change.Id changeInMaster =
        changeOperations
            .newChange()
            .project(project)
            .branch("master")
            .file(file)
            .content("master content")
            .createV1();
    approve(Integer.toString(changeInMaster.get()));
    gApi.changes().id(changeInMaster.get()).current().submit();

    // Create a change in the other branch and that touches the file.
    Change.Id changeInOtherBranch =
        changeOperations
            .newChange()
            .project(project)
            .branch(branchName)
            .file(file)
            .content("other content")
            .createV1();
    approve(Integer.toString(changeInOtherBranch.get()));
    gApi.changes().id(changeInOtherBranch.get()).current().submit();

    // Create a merge change with a conflict resolution for the file.
    Change.Id mergeChange =
        changeOperations
            .newChange()
            .project(project)
            .branch("master")
            .mergeOfButBaseOnFirst()
            .tipOfBranch("master")
            .and()
            .tipOfBranch(branchName)
            .file(file)
            .content("merged content")
            .createV1();

    String mergeRevision =
        changeOperations.change(mergeChange).currentPatchset().get().commitId().name();
    assertBranchNotFound(project, RefNames.refsCacheAutomerge(mergeRevision));
  }

  private void testGetRefWithAccessDatabase(Project.NameKey project, String ref) throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    try {
      requestScopeOperations.setApiUser(user.id());
      assertBranchFound(project, ref);
    } finally {
      projectOperations
          .allProjectsForUpdate()
          .remove(
              capabilityKey(GlobalCapability.ACCESS_DATABASE)
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();
    }
  }

  private void assertBranchNotFound(Project.NameKey project, String ref) {
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).branch(ref).get());
    assertThat(exception).hasMessageThat().isEqualTo("Not found: " + ref);
  }

  private void assertBranchFound(Project.NameKey project, String ref) throws RestApiException {
    BranchInfo branchInfo = gApi.projects().name(project.get()).branch(ref).get();
    assertThat(branchInfo.ref).isEqualTo(RefNames.fullName(ref));
  }
}
