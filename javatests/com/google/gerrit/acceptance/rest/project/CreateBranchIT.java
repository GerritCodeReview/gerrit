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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_HEADS;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.RefNames;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class CreateBranchIT extends AbstractDaemonTest {
  private Branch.NameKey testBranch;

  @Before
  public void setUp() throws Exception {
    testBranch = new Branch.NameKey(project, "test");
  }

  @Test
  public void createBranchRestApi() throws Exception {
    BranchInput input = new BranchInput();
    input.ref = "foo";
    assertThat(gApi.projects().name(project.get()).branches().get().stream().map(i -> i.ref))
        .doesNotContain(REFS_HEADS + input.ref);
    RestResponse r =
        adminRestSession.put("/projects/" + project.get() + "/branches/" + input.ref, input);
    r.assertCreated();
    assertThat(gApi.projects().name(project.get()).branches().get().stream().map(i -> i.ref))
        .contains(REFS_HEADS + input.ref);
  }

  @Test
  public void createBranch_Forbidden() throws Exception {
    setApiUser(user);
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createBranchByAdmin() throws Exception {
    assertCreateSucceeds(testBranch);
  }

  @Test
  public void branchAlreadyExists_Conflict() throws Exception {
    assertCreateSucceeds(testBranch);
    assertCreateFails(testBranch, ResourceConflictException.class);
  }

  @Test
  public void createBranchByProjectOwner() throws Exception {
    grantOwner();
    setApiUser(user);
    assertCreateSucceeds(testBranch);
  }

  @Test
  public void createBranchByAdminCreateReferenceBlocked_Forbidden() throws Exception {
    blockCreateReference();
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createBranchByProjectOwnerCreateReferenceBlocked_Forbidden() throws Exception {
    grantOwner();
    blockCreateReference();
    setApiUser(user);
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createMetaBranch() throws Exception {
    String metaRef = RefNames.REFS_META + "foo";
    allow(metaRef, Permission.CREATE, REGISTERED_USERS);
    allow(metaRef, Permission.PUSH, REGISTERED_USERS);
    assertCreateSucceeds(new Branch.NameKey(project, metaRef));
  }

  @Test
  public void createUserBranch_Conflict() throws Exception {
    allow(allUsers, RefNames.REFS_USERS + "*", Permission.CREATE, REGISTERED_USERS);
    allow(allUsers, RefNames.REFS_USERS + "*", Permission.PUSH, REGISTERED_USERS);
    assertCreateFails(
        new Branch.NameKey(allUsers, RefNames.refsUsers(new Account.Id(1))),
        RefNames.refsUsers(admin.getId()),
        ResourceConflictException.class,
        "Not allowed to create user branch.");
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
  public void createGroupBranch_Conflict() throws Exception {
    allow(allUsers, RefNames.REFS_GROUPS + "*", Permission.CREATE, REGISTERED_USERS);
    allow(allUsers, RefNames.REFS_GROUPS + "*", Permission.PUSH, REGISTERED_USERS);
    assertCreateFails(
        new Branch.NameKey(allUsers, RefNames.refsGroups(new AccountGroup.UUID("foo"))),
        RefNames.refsGroups(adminGroupUuid()),
        ResourceConflictException.class,
        "Not allowed to create group branch.");
  }

  @Test
  public void createWithRevision() throws Exception {
    RevCommit revision = getRemoteHead(project, "master");

    // update master so that points to a different revision than the revision on which we create the
    // new branch
    pushTo("refs/heads/master");
    assertThat(getRemoteHead(project, "master")).isNotEqualTo(revision);

    BranchInput input = new BranchInput();
    input.revision = revision.name();
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.get());
    assertThat(created.revision).isEqualTo(revision.name());
    assertThat(getRemoteHead(project, testBranch.getShortName())).isEqualTo(revision);
  }

  @Test
  public void createWithoutSpecifyingRevision() throws Exception {
    // If revision is not specified, the branch is created based on HEAD, which points to master.
    RevCommit expectedRevision = getRemoteHead(project, "master");

    BranchInput input = new BranchInput();
    input.revision = null;
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.get());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(getRemoteHead(project, testBranch.getShortName())).isEqualTo(expectedRevision);
  }

  @Test
  public void createWithEmptyRevision() throws Exception {
    // If revision is not specified, the branch is created based on HEAD, which points to master.
    RevCommit expectedRevision = getRemoteHead(project, "master");

    BranchInput input = new BranchInput();
    input.revision = "";
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.get());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(getRemoteHead(project, testBranch.getShortName())).isEqualTo(expectedRevision);
  }

  @Test
  public void createRevisionIsTrimmed() throws Exception {
    RevCommit revision = getRemoteHead(project, "master");

    BranchInput input = new BranchInput();
    input.revision = "\t" + revision.name();
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.get());
    assertThat(created.revision).isEqualTo(revision.name());
    assertThat(getRemoteHead(project, testBranch.getShortName())).isEqualTo(revision);
  }

  @Test
  public void createWithBranchNameAsRevision() throws Exception {
    RevCommit expectedRevision = getRemoteHead(project, "master");

    BranchInput input = new BranchInput();
    input.revision = "master";
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.get());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(getRemoteHead(project, testBranch.getShortName())).isEqualTo(expectedRevision);
  }

  @Test
  public void createWithFullBranchNameAsRevision() throws Exception {
    RevCommit expectedRevision = getRemoteHead(project, "master");

    BranchInput input = new BranchInput();
    input.revision = "refs/heads/master";
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.get());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(getRemoteHead(project, testBranch.getShortName())).isEqualTo(expectedRevision);
  }

  @Test
  public void cannotCreateWithNonExistingBranchNameAsRevision() throws Exception {
    assertCreateFails(
        testBranch,
        "refs/heads/non-existing",
        BadRequestException.class,
        "invalid revision \"refs/heads/non-existing\"");
  }

  @Test
  public void cannotCreateWithNonExistingRevision() throws Exception {
    assertCreateFails(
        testBranch,
        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
        BadRequestException.class,
        "invalid revision \"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\"");
  }

  @Test
  public void cannotCreateWithInvalidRevision() throws Exception {
    assertCreateFails(
        testBranch,
        "invalid\trevision",
        BadRequestException.class,
        "invalid revision \"invalid\trevision\"");
  }

  private void blockCreateReference() throws Exception {
    block("refs/*", Permission.CREATE, ANONYMOUS_USERS);
  }

  private void grantOwner() throws Exception {
    allow("refs/*", Permission.OWNER, REGISTERED_USERS);
  }

  private BranchApi branch(Branch.NameKey branch) throws Exception {
    return gApi.projects().name(branch.getParentKey().get()).branch(branch.get());
  }

  private void assertCreateSucceeds(Branch.NameKey branch) throws Exception {
    BranchInfo created = branch(branch).create(new BranchInput()).get();
    assertThat(created.ref).isEqualTo(branch.get());
  }

  private void assertCreateFails(
      Branch.NameKey branch, Class<? extends RestApiException> errType, String errMsg)
      throws Exception {
    assertCreateFails(branch, null, errType, errMsg);
  }

  private void assertCreateFails(
      Branch.NameKey branch,
      String revision,
      Class<? extends RestApiException> errType,
      String errMsg)
      throws Exception {
    BranchInput in = new BranchInput();
    in.revision = revision;
    if (errMsg != null) {
      exception.expectMessage(errMsg);
    }
    exception.expect(errType);
    branch(branch).create(in);
  }

  private void assertCreateFails(Branch.NameKey branch, Class<? extends RestApiException> errType)
      throws Exception {
    assertCreateFails(branch, errType, null);
  }
}
