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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testutil.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.inject.Inject;
import org.junit.Test;

public class GetBranchIT extends AbstractDaemonTest {
  @Inject protected AllUsersName allUsers;

  @Test
  public void cannotGetNonExistingBranch() {
    assertBranchNotFound(project, RefNames.fullName("non-existing"));
  }

  @Test
  public void getBranch() throws Exception {
    setApiUser(user);
    assertBranchFound(project, RefNames.fullName("master"));
  }

  @Test
  public void getBranchByShortName() throws Exception {
    setApiUser(user);
    assertBranchFound(project, "master");
  }

  @Test
  public void cannotGetNonVisibleBranch() throws Exception {
    String branchName = "master";

    // block read access to the branch
    block(project, RefNames.fullName(branchName), Permission.READ, ANONYMOUS_USERS);

    setApiUser(user);
    assertBranchNotFound(project, RefNames.fullName(branchName));
  }

  @Test
  public void cannotGetNonVisibleBranchByShortName() throws Exception {
    String branchName = "master";

    // block read access to the branch
    block(project, RefNames.fullName(branchName), Permission.READ, ANONYMOUS_USERS);

    setApiUser(user);
    assertBranchNotFound(project, branchName);
  }

  @Test
  public void getChangeRef() throws Exception {
    // create a change
    Change.Id changeId = createChange("refs/for/master").getPatchSetId().changeId;

    // a user without the 'Access Database' capability can see the change ref
    setApiUser(user);
    String changeRef = RefNames.patchSetRef(new PatchSet.Id(changeId, 1));
    assertBranchFound(project, changeRef);
  }

  @Test
  public void getChangeRefOfNonVisibleChange() throws Exception {
    // create a change
    String branchName = "master";
    Change.Id changeId = createChange("refs/for/" + branchName).getPatchSetId().changeId;

    // block read access to the branch
    block(project, RefNames.fullName(branchName), Permission.READ, ANONYMOUS_USERS);

    // a user without the 'Access Database' capability cannot see the change ref
    setApiUser(user);
    String changeRef = RefNames.patchSetRef(new PatchSet.Id(changeId, 1));
    assertBranchNotFound(project, changeRef);

    // a user with the 'Access Database' capability can see the change ref
    testGetRefWithAccessDatabase(project, changeRef);
  }

  @Test
  public void getChangeEditRef() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create a change
    Change.Id changeId = createChange("refs/for/master").getPatchSetId().changeId;

    // create a change edit by 'user'
    setApiUser(user);
    gApi.changes().id(changeId.get()).edit().create();

    // every user can see their own change edit refs
    String changeEditRef = RefNames.refsEdit(user.id, changeId, new PatchSet.Id(changeId, 1));
    assertBranchFound(project, changeEditRef);

    // a user without the 'Access Database' capability cannot see the change edit ref of another
    // user
    setApiUser(user2);
    assertBranchNotFound(project, changeEditRef);

    // a user with the 'Access Database' capability can see the change edit ref of another user
    testGetRefWithAccessDatabase(project, changeEditRef);
  }

  @Test
  public void cannotGetChangeEditRefOfNonVisibleChange() throws Exception {
    // create a change
    String branchName = "master";
    Change.Id changeId = createChange("refs/for/" + branchName).getPatchSetId().changeId;

    // create a change edit by 'user'
    setApiUser(user);
    gApi.changes().id(changeId.get()).edit().create();

    // make the change non-visible by blocking read access on the destination
    block(project, RefNames.fullName(branchName), Permission.READ, ANONYMOUS_USERS);

    // user cannot see their own change edit refs if the change is no longer visible
    String changeEditRef = RefNames.refsEdit(user.id, changeId, new PatchSet.Id(changeId, 1));
    assertBranchNotFound(project, changeEditRef);

    // a user with the 'Access Database' capability can see the change edit ref
    testGetRefWithAccessDatabase(project, changeEditRef);
  }

  @Test
  public void getChangeMetaRef() throws Exception {
    // create a change
    Change.Id changeId = createChange("refs/for/master").getPatchSetId().changeId;

    // A user without the 'Access Database' capability can see the change meta ref.
    // This may be surprising, as 'Access Database' guards access to meta refs and the change meta
    // ref is a meta ref, however change meta refs have been always visible to all users that can
    // see the change and some tools rely on seeing these refs, so we have to keep the current
    // behaviour.
    setApiUser(user);
    String changeMetaRef = RefNames.changeMetaRef(changeId);
    if (NoteDbMode.get().ordinal() >= NoteDbMode.WRITE.ordinal()) {
      // Branch is there when we write to NoteDb
      assertBranchFound(project, changeMetaRef);
    }
  }

  @Test
  public void getRefsMetaConfig() throws Exception {
    // a non-project owner cannot get the refs/meta/config branch
    setApiUser(user);
    assertBranchNotFound(project, RefNames.REFS_CONFIG);

    // a non-project owner cannot get the refs/meta/config branch even with the 'Access Database'
    // capability
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    try {
      assertBranchNotFound(project, RefNames.REFS_CONFIG);
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    }

    setApiUser(user);

    // a project owner can get the refs/meta/config branch
    allow(project, "refs/*", Permission.OWNER, REGISTERED_USERS);
    assertBranchFound(project, RefNames.REFS_CONFIG);
  }

  @Test
  public void getUserRefOfOtherUser() throws Exception {
    String userRef = RefNames.refsUsers(admin.id);

    // a user without the 'Access Database' capability cannot see the user ref of another user
    setApiUser(user);
    assertBranchNotFound(allUsers, userRef);

    // a user with the 'Access Database' capability can see the user ref of another user
    testGetRefWithAccessDatabase(allUsers, userRef);
  }

  @Test
  public void getOwnUserRef() throws Exception {
    // every user can see the own user ref
    setApiUser(user);
    assertBranchFound(allUsers, RefNames.refsUsers(user.id));

    // TODO: every user can see the own user ref via the magic ref/users/self ref
    //  setApiUser(user);
    // assertBranchFound(allUsers, RefNames.REFS_USERS_SELF);
  }

  @Test
  public void getExternalIdsRefs() throws Exception {
    // a user without the 'Access Database' capability cannot see the refs/meta/external-ids ref
    setApiUser(user);
    assertBranchNotFound(allUsers, RefNames.REFS_EXTERNAL_IDS);

    // a user with the 'Access Database' capability can see the refs/meta/external-ids ref
    testGetRefWithAccessDatabase(allUsers, RefNames.REFS_EXTERNAL_IDS);
  }

  @Test
  public void getGroupRef() throws Exception {
    // Groups were not yet in NoteDb in this release.
  }

  @Test
  public void getGroupNamesRef() throws Exception {
    // Groups were not yet in NoteDb in this release.
  }

  @Test
  public void getDeletedGroupRef() throws Exception {
    // Groups were not yet in NoteDb in this release.
  }

  @Test
  public void getDraftCommentsRef() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create a change
    String fileName = "a.txt";
    Change change = createChange("A Change", fileName, "content").getChange().change();

    // create a draft comment by the by 'user'
    setApiUser(user);
    DraftInput draftInput = new DraftInput();
    draftInput.path = fileName;
    draftInput.line = 0;
    draftInput.message = "Some Comment";
    gApi.changes().id(change.getChangeId()).current().createDraft(draftInput);

    // every user can see their own draft comments refs
    // TODO: is this a bug?
    String draftCommentsRef = RefNames.refsDraftComments(change.getId(), user.id);
    if (NoteDbMode.get().ordinal() >= NoteDbMode.WRITE.ordinal()) {
      // Branch is there when we write to NoteDb
      assertBranchFound(allUsers, draftCommentsRef);
    }

    // a user without the 'Access Database' capability cannot see the draft comments ref of another
    // user
    setApiUser(user2);
    assertBranchNotFound(allUsers, draftCommentsRef);

    // a user with the 'Access Database' capability can see the draft comments ref of another user
    if (NoteDbMode.get().ordinal() >= NoteDbMode.WRITE.ordinal()) {
      // Branch is there when we write to NoteDb
      testGetRefWithAccessDatabase(allUsers, draftCommentsRef);
    }
  }

  @Test
  public void getStarredChangesRef() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create a change
    Change change = createChange().getChange().change();

    // let user star the change
    setApiUser(user);
    gApi.accounts().self().starChange(Integer.toString(change.getChangeId()));

    // every user can see their own starred changes refs
    // TODO: is this a bug?
    String starredChangesRef = RefNames.refsStarredChanges(change.getId(), user.id);
    assertBranchFound(allUsers, starredChangesRef);

    // a user without the 'Access Database' capability cannot see the starred changes ref of another
    // user
    setApiUser(user2);
    assertBranchNotFound(allUsers, starredChangesRef);

    // a user with the 'Access Database' capability can see the starred changes ref of another user
    testGetRefWithAccessDatabase(allUsers, starredChangesRef);
  }

  @Test
  public void getTagRef() throws Exception {
    // create a tag
    TagInput input = new TagInput();
    input.message = "My Tag";
    input.revision = gApi.projects().name(project.get()).head();
    TagInfo tagInfo = gApi.projects().name(project.get()).tag("my-tag").create(input).get();

    // any user who can see the project, can see the tag
    setApiUser(user);
    assertBranchFound(project, tagInfo.ref);
  }

  @Test
  public void cannotGetTagRefThatPointsToNonVisibleBranch() throws Exception {
    // create a tag
    TagInput input = new TagInput();
    input.message = "My Tag";
    input.revision = gApi.projects().name(project.get()).head();
    TagInfo tagInfo = gApi.projects().name(project.get()).tag("my-tag").create(input).get();

    // block read access to the branch
    block(project, RefNames.fullName("master"), Permission.READ, ANONYMOUS_USERS);

    // if the user cannot see the project, the tag is not visible
    setApiUser(user);
    assertBranchNotFound(project, tagInfo.ref);
  }

  @Test
  public void getSymbolicRef() throws Exception {
    // 'HEAD' is visible since it points to 'master' that is visible
    setApiUser(user);
    assertBranchFound(project, "HEAD");
  }

  @Test
  public void cannotGetSymbolicRefThatPointsToNonVisibleBranch() throws Exception {
    // block read access to the branch to which HEAD points by default
    block(project, RefNames.fullName("master"), Permission.READ, ANONYMOUS_USERS);

    // since 'master' is not visible, 'HEAD' which points to 'master' is also not visible
    setApiUser(user);
    assertBranchNotFound(project, "HEAD");
  }

  @Test
  public void getAccountSequenceRef() throws Exception {
    // Sequences were not yet in NoteDb in this release.
  }

  @Test
  public void getGroupSequenceRef() throws Exception {
    // Sequences were not yet in NoteDb in this release.
  }

  private void testGetRefWithAccessDatabase(Project.NameKey project, String ref) throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    try {
      setApiUser(user);
      assertBranchFound(project, ref);
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
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
