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
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.assertPushRejected;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.entities.RefNames.REFS_HEADS;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class CreateBranchIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  private BranchNameKey testBranch;

  @Before
  public void setUp() throws Exception {
    testBranch = BranchNameKey.create(project, "test");
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
    requestScopeOperations.setApiUser(user.id());
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createBranchByAdmin() throws Exception {
    assertCreateSucceeds(testBranch);
  }

  @Test
  public void branchAlreadyExists_Conflict() throws Exception {
    assertCreateSucceeds(testBranch);
    assertCreateFails(
        testBranch,
        ResourceConflictException.class,
        "branch \"" + testBranch.branch() + "\" already exists");
  }

  @Test
  public void createBranch_LockFailure() throws Exception {
    // check that the branch doesn't exist yet
    assertThrows(ResourceNotFoundException.class, () -> branch(testBranch).get());

    // Register a validation listener that creates the branch to simulate a concurrent request that
    // creates the same branch.
    try (ExtensionRegistry.Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                new RefOperationValidationListener() {
                  @Override
                  public ImmutableList<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
                      throws ValidationException {
                    try (Repository repo = repoManager.openRepository(project)) {
                      RefUpdate u = repo.updateRef(testBranch.branch());
                      u.setExpectedOldObjectId(ObjectId.zeroId());
                      u.setNewObjectId(repo.exactRef("refs/heads/master").getObjectId());
                      RefUpdate.Result result = u.update();
                      if (result != RefUpdate.Result.NEW) {
                        throw new ValidationException(
                            "Concurrent creation of branch failed: " + result);
                      }
                      return ImmutableList.of();
                    } catch (IOException e) {
                      throw new ValidationException("Concurrent creation of branch failed.", e);
                    }
                  }
                })) {
      // Creating the branch is expected to fail, since it is created by the validation listener
      // right before the ref update to create the new branch is done.
      assertCreateFails(
          testBranch,
          ResourceConflictException.class,
          "branch \"" + testBranch.branch() + "\" already exists");
    }
  }

  @Test
  public void conflictingBranchAlreadyExists_Conflict() throws Exception {
    assertCreateSucceeds(testBranch);
    BranchNameKey testBranch2 = BranchNameKey.create(project, testBranch.branch() + "/foo/bar");
    assertCreateFails(
        testBranch2,
        ResourceConflictException.class,
        "Cannot create branch \""
            + testBranch2.branch()
            + "\" since it conflicts with branch \""
            + testBranch.branch()
            + "\"");
  }

  @Test
  public void createBranchByProjectOwner() throws Exception {
    grantOwner();
    requestScopeOperations.setApiUser(user.id());
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
    requestScopeOperations.setApiUser(user.id());
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createMetaBranch() throws Exception {
    String metaRef = RefNames.REFS_META + "foo";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(metaRef).group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(metaRef).group(REGISTERED_USERS))
        .update();
    assertCreateSucceeds(BranchNameKey.create(project, metaRef));
  }

  @Test
  public void createMetaConfigBranch() throws Exception {
    // Since the refs/meta/config branch exists by default, we must delete it before we can test
    // creating it. Since deleting the refs/meta/config branch is not allowed through the API, we
    // delete it directly in the remote repository.
    try (TestRepository<Repository> repo =
        new TestRepository<>(repoManager.openRepository(project))) {
      repo.delete(RefNames.REFS_CONFIG);
    }

    // Create refs/meta/config branch.
    BranchInfo created =
        branch(BranchNameKey.create(project, RefNames.REFS_CONFIG)).create(new BranchInput()).get();
    assertThat(created.ref).isEqualTo(RefNames.REFS_CONFIG);
    assertThat(created.canDelete).isNull();
  }

  @Test
  public void createUserBranch_NotAllowed() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .update();
    assertCreateFails(
        BranchNameKey.create(allUsers, RefNames.refsUsers(Account.id(1))),
        RefNames.refsUsers(admin.id()),
        BadRequestException.class,
        "Not allowed to create branches under Gerrit internal or tags refs.");
  }

  @Test
  public void createGroupBranch_NotAllowed() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .update();
    assertCreateFails(
        BranchNameKey.create(allUsers, RefNames.refsGroups(AccountGroup.uuid("foo"))),
        RefNames.refsGroups(adminGroupUuid()),
        BadRequestException.class,
        "Not allowed to create branches under Gerrit internal or tags refs.");
  }

  @Test
  public void createWithRevision() throws Exception {
    RevCommit revision = projectOperations.project(project).getHead("master");

    // update master so that points to a different revision than the revision on which we create the
    // new branch
    pushTo("refs/heads/master");
    assertThat(projectOperations.project(project).getHead("master")).isNotEqualTo(revision);

    BranchInput input = new BranchInput();
    input.revision = revision.name();
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertThat(created.revision).isEqualTo(revision.name());
    assertThat(projectOperations.project(project).getHead(testBranch.branch())).isEqualTo(revision);
  }

  @Test
  public void createWithoutSpecifyingRevision() throws Exception {
    // If revision is not specified, the branch is created based on HEAD, which points to master.
    RevCommit expectedRevision = projectOperations.project(project).getHead("master");

    BranchInput input = new BranchInput();
    input.revision = null;
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(projectOperations.project(project).getHead(testBranch.branch()))
        .isEqualTo(expectedRevision);
  }

  @Test
  public void createWithEmptyRevision() throws Exception {
    // If revision is not specified, the branch is created based on HEAD, which points to master.
    RevCommit expectedRevision = projectOperations.project(project).getHead("master");

    BranchInput input = new BranchInput();
    input.revision = "";
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(projectOperations.project(project).getHead(testBranch.branch()))
        .isEqualTo(expectedRevision);
  }

  @Test
  public void createRevisionIsTrimmed() throws Exception {
    RevCommit revision = projectOperations.project(project).getHead("master");

    BranchInput input = new BranchInput();
    input.revision = "\t" + revision.name();
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertThat(created.revision).isEqualTo(revision.name());
    assertThat(projectOperations.project(project).getHead(testBranch.branch())).isEqualTo(revision);
  }

  @Test
  public void createWithBranchNameAsRevision() throws Exception {
    RevCommit expectedRevision = projectOperations.project(project).getHead("master");

    BranchInput input = new BranchInput();
    input.revision = "master";
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(projectOperations.project(project).getHead(testBranch.branch()))
        .isEqualTo(expectedRevision);
  }

  @Test
  public void createWithFullBranchNameAsRevision() throws Exception {
    RevCommit expectedRevision = projectOperations.project(project).getHead("master");

    BranchInput input = new BranchInput();
    input.revision = "refs/heads/master";
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertThat(created.revision).isEqualTo(expectedRevision.name());
    assertThat(projectOperations.project(project).getHead(testBranch.branch()))
        .isEqualTo(expectedRevision);
  }

  @Test
  public void cannotCreateWithNonExistingBranchNameAsRevision() throws Exception {
    assertCreateFails(
        testBranch,
        "refs/heads/non-existing",
        UnprocessableEntityException.class,
        "base revision \"refs/heads/non-existing\" not found");
  }

  @Test
  public void cannotCreateWithNonExistingRevision() throws Exception {
    assertCreateFails(
        testBranch,
        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
        UnprocessableEntityException.class,
        "base revision \"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\" not found");
  }

  @Test
  public void cannotCreateWithInvalidRevision() throws Exception {
    assertCreateFails(
        testBranch,
        "invalid\trevision",
        UnprocessableEntityException.class,
        "base revision \"invalid\trevision\" is invalid");
  }

  @Test
  public void cannotCreateWithNonCommitAsRevision() throws Exception {
    String treeId =
        projectOperations
            .project(testBranch.project())
            .getHead("refs/heads/master")
            .getTree()
            .name();
    assertCreateFails(
        testBranch,
        treeId,
        BadRequestException.class,
        "base revision \"" + treeId + "\" is not a commit");
  }

  @Test
  public void createEmptyCommitAndRevisionAreMutuallyExclusive() throws Exception {
    BranchInput input = new BranchInput();
    input.createEmptyCommit = true;
    input.revision = "master";
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> branch(testBranch).create(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("create_empty_commit and revision are mutually exclusive");
  }

  @Test
  public void createBranchWithEmptyCommit() throws Exception {
    BranchInput input = new BranchInput();
    input.createEmptyCommit = true;
    BranchInfo created = branch(testBranch).create(input).get();
    assertThat(created.ref).isEqualTo(testBranch.branch());
    assertEmptyCommit(testBranch);
  }

  @Test
  public void cannotCreateBranchInMagicBranchNamespace() throws Exception {
    assertCreateFails(
        BranchNameKey.create(project, MagicBranch.NEW_CHANGE + "foo"),
        BadRequestException.class,
        "not allowed to create branches under \"" + MagicBranch.NEW_CHANGE + "\"");
  }

  @Test
  public void cannotCreateBranchInGerritInternalRefsNamespace() throws Exception {
    assertCreateFails(
        BranchNameKey.create(project, RefNames.REFS_CHANGES + "00/1000"),
        BadRequestException.class,
        "Not allowed to create branches under Gerrit internal or tags refs.");
  }

  @Test
  public void cannotCreateBranchInTagsNamespace() throws Exception {
    assertCreateFails(
        BranchNameKey.create(project, RefNames.REFS_TAGS + "v1.0"),
        BadRequestException.class,
        "Not allowed to create branches under Gerrit internal or tags refs.");
  }

  @Test
  public void cannotCreateBranchWithInvalidName() throws Exception {
    assertCreateFails(
        BranchNameKey.create(project, RefNames.REFS_HEADS),
        BadRequestException.class,
        "invalid branch name \"" + RefNames.REFS_HEADS + "\"");
  }

  @Test
  public void createBranchLeadingSlashesAreRemoved() throws Exception {
    BranchNameKey expectedNameKey = BranchNameKey.create(project, "test");

    // check that the branch doesn't exist yet
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).branch(expectedNameKey.branch()).get());

    // create the branch, but include leading slashes in the branch name,
    // when creating the branch ensure that the branch name in the URL matches the branch name in
    // the input (if there is a mismatch the creation request is rejected)
    BranchInput branchInput = new BranchInput();
    branchInput.ref = "////" + expectedNameKey.shortName();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    // verify that the branch was created without the leading slashes in the name
    assertThat(gApi.projects().name(project.get()).branch(expectedNameKey.branch()).get().ref)
        .isEqualTo(expectedNameKey.branch());
  }

  @Test
  public void branchNameInInputMustMatchBranchNameInUrl() throws Exception {
    BranchInput branchInput = new BranchInput();
    branchInput.ref = "foo";
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).branch("bar").create(branchInput));
    assertThat(ex).hasMessageThat().isEqualTo("ref must match URL");
  }

  @Test
  public void createBranchViaRestApiFailsIfCommitIsInvalid() throws Exception {
    BranchInput input = new BranchInput();
    input.ref = "new";

    TestRefOperationValidationListener testRefOperationValidationListener =
        new TestRefOperationValidationListener();
    testRefOperationValidationListener.doReject = true;
    try (Registration registration =
        extensionRegistry.newRegistration().add(testRefOperationValidationListener)) {
      ResourceConflictException ex =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.projects().name(project.get()).branch(input.ref).create(input));
      assertThat(ex)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Validation for creation of ref 'refs/heads/new' in project %s failed:\n%s",
                  project, TestRefOperationValidationListener.FAILURE_MESSAGE));
    }
  }

  @Test
  public void createBranchViaRestApiWithValidationOptions() throws Exception {
    BranchInput input = new BranchInput();
    input.ref = "new";
    input.validationOptions = ImmutableMap.of("key", "value");

    TestRefOperationValidationListener testRefOperationValidationListener =
        new TestRefOperationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testRefOperationValidationListener)) {
      gApi.projects().name(project.get()).branch(input.ref).create(input);
      assertThat(testRefOperationValidationListener.refReceivedEvent.pushOptions)
          .containsExactly("key", "value");
    }
  }

  @Test
  public void createBranchViaPushFailsIfCommitIsInvalid() throws Exception {
    TestRefOperationValidationListener testRefOperationValidationListener =
        new TestRefOperationValidationListener();
    testRefOperationValidationListener.doReject = true;
    try (Registration registration =
        extensionRegistry.newRegistration().add(testRefOperationValidationListener)) {
      PushResult r =
          pushHead(
              testRepo,
              "refs/heads/new",
              /* pushTags= */ false,
              /* force= */ false,
              /* pushOptions= */ ImmutableList.of());
      assertPushRejected(
          r,
          "refs/heads/new",
          String.format(
              "Validation for creation of ref 'refs/heads/new' in project %s failed:\n%s",
              project, TestRefOperationValidationListener.FAILURE_MESSAGE));
    }
  }

  @Test
  public void createBranchViaPushWithValidationOptions() throws Exception {
    TestRefOperationValidationListener testRefOperationValidationListener =
        new TestRefOperationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testRefOperationValidationListener)) {
      PushResult r =
          pushHead(
              testRepo,
              "refs/heads/new",
              /* pushTags= */ false,
              /* force= */ false,
              /* pushOptions= */ ImmutableList.of("key=value"));
      assertPushOk(r, "refs/heads/new");
      assertThat(testRefOperationValidationListener.refReceivedEvent.pushOptions)
          .containsExactly("key", "value");
    }
  }

  @Test
  public void createBranchRevisionVisibility() throws Exception {
    AccountGroup.UUID privilegedGroupUuid =
        groupOperations.newGroup().name(name("privilegedGroup")).create();
    TestAccount privilegedUser =
        accountCreator.create(
            "privilegedUser", "privilegedUser@example.com", "privilegedUser", null);
    groupOperations.group(privilegedGroupUuid).forUpdate().addMember(privilegedUser.id()).update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/secret/*").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref("refs/heads/secret/*").group(privilegedGroupUuid))
        .add(allow(Permission.READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(allow(Permission.CREATE).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Configure", "file.txt", "contents");
    PushOneCommit.Result result = push.to("refs/heads/secret/main");
    result.assertOkStatus();
    RevCommit secretCommit = result.getCommit();
    requestScopeOperations.setApiUser(privilegedUser.id());
    BranchInfo info = gApi.projects().name(project.get()).branch("refs/heads/secret/main").get();
    assertThat(info.revision).isEqualTo(secretCommit.name());
    TestAccount unprivileged =
        accountCreator.create("unprivileged", "unprivileged@example.com", "unprivileged", null);
    requestScopeOperations.setApiUser(unprivileged.id());
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).branch("refs/heads/secret/main").get());
    BranchInput branchInput = new BranchInput();
    branchInput.ref = "public";
    branchInput.revision = secretCommit.name();
    assertThrows(
        AuthException.class,
        () -> gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput));

    branchInput.revision = "refs/heads/secret/main";
    assertThrows(
        AuthException.class,
        () -> gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput));
  }

  private void blockCreateReference() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.CREATE).ref("refs/*").group(ANONYMOUS_USERS))
        .update();
  }

  private void grantOwner() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();
  }

  private BranchApi branch(BranchNameKey branch) throws Exception {
    return gApi.projects().name(branch.project().get()).branch(branch.branch());
  }

  private void assertCreateSucceeds(BranchNameKey branch) throws Exception {
    BranchInfo created = branch(branch).create(new BranchInput()).get();
    assertThat(created.ref).isEqualTo(branch.branch());
  }

  private void assertCreateFails(
      BranchNameKey branch, Class<? extends RestApiException> errType, String errMsg)
      throws Exception {
    assertCreateFails(branch, null, errType, errMsg);
  }

  private void assertCreateFails(
      BranchNameKey branch,
      String revision,
      Class<? extends RestApiException> errType,
      String errMsg)
      throws Exception {
    BranchInput in = new BranchInput();
    in.revision = revision;
    RestApiException thrown = assertThrows(errType, () -> branch(branch).create(in));
    if (errMsg != null) {
      assertThat(thrown).hasMessageThat().contains(errMsg);
    }
  }

  private void assertEmptyCommit(BranchNameKey branchNameKey) throws Exception {
    try (Repository repo = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repo);
        TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
      RevCommit commit = rw.lookupCommit(repo.exactRef(branchNameKey.branch()).getObjectId());
      rw.parseBody(commit);
      tw.addTree(commit.getTree());
      assertThat(tw.next()).isFalse();
      tw.reset();
    }
  }

  private static class TestRefOperationValidationListener
      implements RefOperationValidationListener {
    static final String FAILURE_MESSAGE = "failure from test";

    public boolean doReject;
    public RefReceivedEvent refReceivedEvent;

    @Override
    public List<ValidationMessage> onRefOperation(RefReceivedEvent refReceivedEvent)
        throws ValidationException {
      this.refReceivedEvent = refReceivedEvent;

      if (doReject) {
        throw new ValidationException(FAILURE_MESSAGE);
      }

      return ImmutableList.of();
    }
  }
}
