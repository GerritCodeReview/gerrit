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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class CreateMergePatchSetIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private ChangeOperations changeOperations;
  @Inject private AccountOperations accountOperations;

  @Before
  public void setUp() {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();
  }

  @Test
  public void createMergePatchSet() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();
    String parent = currentMaster.getCommit().getName();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    String subject = "update change by merge ps2";
    in.subject = subject;

    TestWorkInProgressStateChangedListener wipStateChangedListener =
        new TestWorkInProgressStateChangedListener();
    try (ExtensionRegistry.Registration registration =
        extensionRegistry.newRegistration().add(wipStateChangedListener)) {
      ChangeInfo changeInfo = gApi.changes().id(changeId).createMergePatchSet(in);
      assertThat(changeInfo.subject).isEqualTo(in.subject);
      assertThat(changeInfo.containsGitConflicts).isNull();
      assertThat(changeInfo.workInProgress).isNull();
    }
    assertThat(wipStateChangedListener.invoked).isFalse();

    // To get the revisions, we must retrieve the change with more change options.
    ChangeInfo changeInfo =
        gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
    assertThat(changeInfo.revisions).hasSize(2);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(parent);

    // Verify the message that has been posted on the change.
    List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
    assertThat(messages).hasSize(2);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Uploaded patch set 2.");

    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.message)
        .contains(subject);
  }

  @Test
  public void createMergePatchSet_SubjectCarriesOverByDefault() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String subject = result.getChange().change().getSubject();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result pushResult =
        pushFactory.create(user.newIdent(), testRepo).to("refs/heads/dev");
    pushResult.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = null;

    // Ensure subject carries over
    gApi.changes().id(changeId).createMergePatchSet(in);
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    assertThat(changeInfo.subject).isEqualTo(subject);
  }

  @Test
  public void createMergePatchSetAfterUpdatingPreferredEmail() throws Exception {
    String emailOne = "email1@example.com";
    Account.Id testUser = accountOperations.newAccount().preferredEmail(emailOne).create();
    String branch = "dev";
    createBranch(BranchNameKey.create(project, branch));

    // Create a change for master branch
    Change.Id change = changeOperations.newChange().project(project).owner(testUser).create();

    // Push a commit to dev branch
    createChange("refs/heads/dev");

    // Change preferred email for the user
    String emailTwo = "email2@example.com";
    accountOperations.account(testUser).forUpdate().preferredEmail(emailTwo).update();
    requestScopeOperations.setApiUser(testUser);

    // Create merge patch-set
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = branch;
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    gApi.changes().id(change.get()).createMergePatchSet(in);

    assertThat(gApi.changes().id(change.get()).get().getCurrentRevision().commit.committer.email)
        .isEqualTo(emailTwo);
  }

  @Test
  public void createMergePatchSet_Conflict() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    String fileName = "shared.txt";
    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster =
        pushFactory
            .create(admin.newIdent(), testRepo, "change 1", fileName, "content 1")
            .to("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change 2", fileName, "content 2")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2";
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId).createMergePatchSet(in));
    assertThat(thrown).hasMessageThat().isEqualTo("merge conflict(s):\n" + fileName);
  }

  @Test
  public void createMergePatchSet_ConflictAllowed() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    String fileName = "shared.txt";
    String sourceSubject = "source change";
    String sourceContent = "source content";
    String targetSubject = "target change";
    String targetContent = "target content";
    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster =
        pushFactory
            .create(admin.newIdent(), testRepo, targetSubject, fileName, targetContent)
            .to("refs/heads/master");
    currentMaster.assertOkStatus();
    String parent = currentMaster.getCommit().getName();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, sourceSubject, fileName, sourceContent)
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    mergeInput.allowConflicts = true;
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2";

    TestWorkInProgressStateChangedListener wipStateChangedListener =
        new TestWorkInProgressStateChangedListener();
    try (ExtensionRegistry.Registration registration =
        extensionRegistry.newRegistration().add(wipStateChangedListener)) {
      ChangeInfo changeInfo = gApi.changes().id(changeId).createMergePatchSet(in);
      assertThat(changeInfo.subject).isEqualTo(in.subject);
      assertThat(changeInfo.containsGitConflicts).isTrue();
      assertThat(changeInfo.workInProgress).isTrue();
    }
    assertThat(wipStateChangedListener.invoked).isTrue();
    assertThat(wipStateChangedListener.wip).isTrue();

    // To get the revisions, we must retrieve the change with more change options.
    ChangeInfo changeInfo =
        gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
    assertThat(changeInfo.revisions).hasSize(2);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(parent);

    // Verify that the file content in the created patch set is correct.
    // We expect that it has conflict markers to indicate the conflict.
    BinaryResult bin = gApi.changes().id(changeId).current().file(fileName).content();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String fileContent = new String(os.toByteArray(), UTF_8);
    String sourceSha1 = abbreviateName(changeA.getCommit(), 6);
    String targetSha1 = abbreviateName(currentMaster.getCommit(), 6);
    assertThat(fileContent)
        .isEqualTo(
            "<<<<<<< TARGET BRANCH ("
                + targetSha1
                + " "
                + targetSubject
                + ")\n"
                + targetContent
                + "\n"
                + "=======\n"
                + sourceContent
                + "\n"
                + ">>>>>>> SOURCE BRANCH ("
                + sourceSha1
                + " "
                + sourceSubject
                + ")\n");

    // Verify the message that has been posted on the change.
    List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
    assertThat(messages).hasSize(2);
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Uploaded patch set 2.\n\n"
                + "The following files contain Git conflicts:\n"
                + "* "
                + fileName
                + "\n");
  }

  @Test
  public void createMergePatchSet_ConflictAllowedNotSupportedByMergeStrategy() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    String fileName = "shared.txt";
    String sourceSubject = "source change";
    String sourceContent = "source content";
    String targetSubject = "target change";
    String targetContent = "target content";
    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster =
        pushFactory
            .create(admin.newIdent(), testRepo, targetSubject, fileName, targetContent)
            .to("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, sourceSubject, fileName, sourceContent)
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    mergeInput.allowConflicts = true;
    mergeInput.strategy = "simple-two-way-in-core";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2";

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).createMergePatchSet(in));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "merge with conflicts is not supported with merge strategy: " + mergeInput.strategy);
  }

  @Test
  public void createMergePatchSetInheritParent() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String parent = r.getCommit().getParent(0).getName();

    // advance master branch
    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2 inherit parent of ps1";
    in.inheritParent = true;
    gApi.changes().id(changeId).createMergePatchSet(in);
    ChangeInfo changeInfo =
        gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);

    assertThat(changeInfo.revisions).hasSize(2);
    assertThat(changeInfo.subject).isEqualTo(in.subject);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(parent);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isNotEqualTo(currentMaster.getCommit().getName());
  }

  @Test
  public void createMergePatchSetCannotBaseOnInvisibleChange() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "foo"));
    createBranch(BranchNameKey.create(project, "bar"));

    // Create a merged commit on 'foo' branch.
    merge(createChange("refs/for/foo"));

    // Create the base change on 'bar' branch.
    testRepo.reset(initialHead);
    String baseChange = createChange("refs/for/bar").getChangeId();
    gApi.changes().id(baseChange).setPrivate(true, "set private");

    // Create the destination change on 'master' branch.
    requestScopeOperations.setApiUser(user.id());
    testRepo.reset(initialHead);
    String changeId = createChange().getChangeId();

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                gApi.changes()
                    .id(changeId)
                    .createMergePatchSet(createMergePatchSetInput(baseChange)));
    assertThat(thrown).hasMessageThat().contains("Read not permitted for " + baseChange);
  }

  @Test
  public void createMergePatchSetBaseOnChange() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "foo"));
    createBranch(BranchNameKey.create(project, "bar"));

    // Create a merged commit on 'foo' branch.
    merge(createChange("refs/for/foo"));

    // Create the base change on 'bar' branch.
    testRepo.reset(initialHead);
    PushOneCommit.Result result = createChange("refs/for/bar");
    String baseChange = result.getChangeId();
    String expectedParent = result.getCommit().getName();

    // Create the destination change on 'master' branch.
    testRepo.reset(initialHead);
    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).createMergePatchSet(createMergePatchSetInput(baseChange));

    ChangeInfo changeInfo =
        gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
    assertThat(changeInfo.revisions).hasSize(2);
    assertThat(changeInfo.subject).isEqualTo("create ps2");
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(expectedParent);
  }

  @Test
  public void createMergePatchSetWithUnupportedMergeStrategy() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    String fileName = "shared.txt";
    String sourceSubject = "source change";
    String sourceContent = "source content";
    String targetSubject = "target change";
    String targetContent = "target content";
    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster =
        pushFactory
            .create(admin.newIdent(), testRepo, targetSubject, fileName, targetContent)
            .to("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, sourceSubject, fileName, sourceContent)
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    mergeInput.strategy = "unsupported-strategy";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2";

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).createMergePatchSet(in));
    assertThat(ex).hasMessageThat().isEqualTo("invalid merge strategy: " + mergeInput.strategy);
  }

  @Test
  public void createMergePatchSetWithOtherAuthor() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();
    String parent = currentMaster.getCommit().getName();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    String subject = "update change by merge ps2";
    in.subject = subject;
    in.author = new AccountInput();
    in.author.name = "Other Author";
    in.author.email = "otherauthor@example.com";
    gApi.changes().id(changeId).createMergePatchSet(in);

    // To get the revisions, we must retrieve the change with more change options.
    ChangeInfo changeInfo =
        gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
    assertThat(changeInfo.revisions).hasSize(2);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(parent);

    // Verify the message that has been posted on the change.
    List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
    assertThat(messages).hasSize(2);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Uploaded patch set 2.");

    CommitInfo commitInfo = changeInfo.revisions.get(changeInfo.currentRevision).commit;
    assertThat(commitInfo.message).contains(subject);
    assertThat(commitInfo.author.name).isEqualTo("Other Author");
    assertThat(commitInfo.author.email).isEqualTo("otherauthor@example.com");
    assertThat(commitInfo.committer.email).isEqualTo(admin.email());
  }

  @Test
  public void createMergePatchSetWithSpecificAuthorButNoForgeAuthorPermission() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    String subject = "update change by merge ps2";
    in.subject = subject;
    in.author = new AccountInput();
    in.author.name = "Foo";
    in.author.email = "foo@example.com";

    projectOperations
        .project(project)
        .forUpdate()
        .remove(
            TestProjectUpdate.permissionKey(Permission.FORGE_AUTHOR)
                .ref("refs/*")
                .group(REGISTERED_USERS))
        .add(block(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();
    AuthException ex =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(changeId).createMergePatchSet(in));
    assertThat(ex).hasMessageThat().isEqualTo("not permitted: forge author on refs/heads/master");
  }

  @Test
  public void createMergePatchSetWithMissingNameFailsWithBadRequestException() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    String subject = "update change by merge ps2";
    in.subject = subject;
    in.author = new AccountInput();
    in.author.name = "Foo";

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).createMergePatchSet(in));
    assertThat(ex).hasMessageThat().isEqualTo("Author must specify name and email");
  }

  @Test
  public void createMergePatchSetWithMissingEmailFailsWithBadRequestException() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    createBranch(BranchNameKey.create(project, "dev"));

    // create a change for master
    String changeId = createChange().getChangeId();

    testRepo.reset(initialHead);
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    testRepo.reset(initialHead);
    PushOneCommit.Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    String subject = "update change by merge ps2";
    in.subject = subject;
    in.author = new AccountInput();
    in.author.email = "Foo";

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).createMergePatchSet(in));
    assertThat(ex).hasMessageThat().isEqualTo("Author must specify name and email");
  }

  private MergePatchSetInput createMergePatchSetInput(String baseChange) {
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "foo";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "create ps2";
    in.inheritParent = false;
    in.baseChange = baseChange;
    return in;
  }

  private static class TestWorkInProgressStateChangedListener
      implements WorkInProgressStateChangedListener {
    boolean invoked;
    Boolean wip;

    @Override
    public void onWorkInProgressStateChanged(Event event) {
      this.invoked = true;
      this.wip =
          event.getChange().workInProgress != null ? event.getChange().workInProgress : false;
    }
  }
}
